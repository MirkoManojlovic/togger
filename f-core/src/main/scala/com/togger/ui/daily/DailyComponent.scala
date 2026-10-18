package com.togger.ui.daily

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.dto.organization.OrganizationProjectResp
import com.togger.dto.{LogEntryDto, SearchAccountDto}
import com.togger.logentry.LogEntryStatus
import com.togger.ui.auth.AccountService
import com.togger.ui.auth.TgInput.tgInputStyle
import com.togger.ui.components.{DropdownMeta, TgSelectDropdown, TgSelectDropdownOption, TgStep, TgStepperBreadcrumb}
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.modal.ModalStyles
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskFeService, TaskProjectDataLoader}
import com.togger.ui.toast.Toast

import java.time.{DayOfWeek, LocalDate, LocalTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.duration.DurationInt

case class DailyState(
  projectId: Option[UUID],
  projects: Seq[OrganizationProjectResp] = Seq.empty,
  members: Seq[SearchAccountDto] = Seq.empty,
  workDays: Set[DayOfWeek] = WorkDayCalendar.DefaultWorkDays,
  selected: Set[UUID] = Set.empty,
  todayFirst: Boolean = true,
  plan: Seq[DailyMemberPlan] = Seq.empty,
  position: DailyPosition = DailyPosition.ChooseMembers,
  entries: Map[DailyEntryKey, DailyEntryDraft] = Map.empty
) {

  def positions: Seq[DailyPosition] = DailyPlan.positions(plan, todayFirst)

  def draft: DailyDraft =
    DailyDraft(projectId, selected.toSeq, todayFirst, entries.map { case (key, draft) => DailyEntryRecord(key, draft) }.toSeq)

  def toSave: Seq[(DailyEntryKey, DailyEntryDraft)] =
    plan.flatMap(member => member.tasks.flatMap(task => task.missingDays.map(DailyEntryKey(member.member.id, task.taskId, _))))
      .flatMap(key => entries.get(key).filter(_.willBeSaved).map(key -> _))
}

/** The daily: choose who it is for, fill in the missing log entries of their open tasks, accept all and submit. */
class DailyComponent(
  taskService: TaskFeService,
  projectDataLoader: TaskProjectDataLoader,
  projectService: ProjectService,
  spinnerService: SpinnerService,
  accountService: AccountService,
  tgClock: TgClock,
  currentProjectId: Option[UUID],
  localStorage: LocalStorage,
  toast: Toast,
  onClose: () => Unit
)(implicit ioRuntime: IORuntime) {

  private val DraftSaveDebounce = 300.millis
  private val ProcessSteps = Seq("Choose members", "Fill in the status", "Accept all changes")

  private val state = Var(DailyState(projectId = currentProjectId))

  private val currentAccountId: Option[UUID] = accountService.get.map(_.id)

  private def draftStore(projectId: UUID): Option[DailyDraftStore] =
    currentAccountId.map(new DailyDraftStore(localStorage, _, projectId))

  /** Off once the draft is discarded or submitted, so a save still waiting can't bring the draft back. */
  private val persisting = Var(true)

  private def saveDraft(): Unit =
    if (persisting.now()) {
      val current = state.now()
      current.projectId.flatMap(draftStore).foreach(_.save(current.draft))
    }

  private def closeKeepingDraft(): Unit = {
    saveDraft()
    onClose()
  }

  private def closeDiscardingDraft(): Unit = {
    persisting.set(false)
    state.now().projectId.flatMap(draftStore).foreach(_.clear())
    onClose()
  }

  // ---- loading

  private def loadProjects(): Unit =
    if (currentProjectId.isEmpty)
      projectDataLoader.loadProjects().map(projects => state.update(_.copy(projects = projects))).unsafeRunAndForget()

  private def loadProject(projectId: UUID): Unit =
    projectDataLoader
      .loadProjectData(projectId)
      .flatMap(data => projectService.findById(projectId).map(project => (data, project)))
      .map { case (data, project) =>
        val workDays = project.toOption
          .flatMap(_.body)
          .map(_.workDays.map(DayOfWeek.of))
          .filter(_.nonEmpty)
          .getOrElse(WorkDayCalendar.DefaultWorkDays)
        val restored = draftStore(projectId).flatMap(_.load())
        val memberIds = data.members.map(_.id).toSet
        state.update { current =>
          val me = currentAccountId.filter(memberIds.contains).toSet
          current.copy(
            members = data.members,
            workDays = workDays,
            selected = restored.fold(me)(_.memberIds.toSet.intersect(memberIds)),
            todayFirst = restored.fold(current.todayFirst)(_.todayFirst),
            entries = restored.fold(Map.empty[DailyEntryKey, DailyEntryDraft])(_.entries.map(r => r.key -> r.draft).toMap),
            plan = Seq.empty,
            position = DailyPosition.ChooseMembers
          )
        }
        if (restored.isDefined) toast.success("Restored your unfinished daily")
      }
      .unsafeRunAndForget()

  private def loadPlan(): Unit = {
    val current = state.now()
    current.projectId.foreach { projectId =>
      spinnerService
        .runWithSpinner(taskService.findAllByProjectId(projectId))
        .flatMap(
          _.fold(
            error => IO(toast.error(error)),
            tasks =>
              IO {
                val plan = DailyPlan.build(
                  tasks.body.getOrElse(Seq.empty),
                  current.members,
                  current.selected,
                  tgClock.currentDate(),
                  current.workDays
                )
                state.update { s =>
                  val planned = s.copy(plan = plan)
                  planned.copy(position = planned.positions.drop(1).headOption.getOrElse(DailyPosition.Review))
                }
              }
          )
        )
        .unsafeRunAndForget()
    }
  }

  // ---- navigation

  private def move(delta: Int): Unit = {
    val current = state.now()
    val positions = current.positions
    val target = positions.indexOf(current.position) + delta
    if (target >= 0 && target < positions.size) state.update(_.copy(position = positions(target)))
  }

  private def next(): Unit =
    if (state.now().position == DailyPosition.ChooseMembers) loadPlan() else move(1)

  private def jumpTo(find: DailyPosition => Boolean): Unit =
    state.now().positions.find(find).foreach(target => state.update(_.copy(position = target)))

  private def isPlanned: Boolean = state.now().position != DailyPosition.ChooseMembers || state.now().plan.nonEmpty

  // ---- editing

  private def defaultEntry(key: DailyEntryKey): DailyEntryDraft = {
    val previous = state.now().plan
      .flatMap(_.tasks)
      .find(_.taskId == key.taskId)
      .fold[LogEntryStatus](LogEntryStatus.InProgress)(_.previousStatus)
    DailyEntryDraft(previous, "", "", skipped = false)
  }

  private def edit(key: DailyEntryKey)(change: DailyEntryDraft => DailyEntryDraft): Unit =
    state.update(current =>
      current.copy(entries = current.entries.updated(key, change(current.entries.getOrElse(key, defaultEntry(key)))))
    )

  private def toggleMember(id: UUID, on: Boolean): Unit =
    state.update(current => current.copy(selected = if (on) current.selected + id else current.selected - id))

  // ---- submit

  private def logEntryOf(key: DailyEntryKey, draft: DailyEntryDraft): LogEntryDto = {
    val today = tgClock.currentDate()
    val createdAt =
      if (key.date == today) tgClock.currentInstant else tgClock.instantFrom(key.date, LocalTime.NOON, ZoneOffset.UTC)
    LogEntryDto(
      id = UUID.randomUUID(),
      taskId = key.taskId,
      createdBy = currentAccountId.get,
      createdAt = createdAt,
      latestVersion = UUID.randomUUID(),
      textInternal = draft.textInternal,
      textExternal = draft.textExternal,
      status = draft.status,
      updatedAt = createdAt
    )
  }

  /** One request per entry. Those that fail stay in the draft, so submitting again only retries them. */
  private def submit(): Unit = {
    val toSave = state.now().toSave
    if (toSave.isEmpty) toast.error("There is nothing to submit")
    else
      spinnerService
        .runWithSpinner(
          toSave.toList.traverse { case (key, draft) =>
            taskService.saveLogEntry(logEntryOf(key, draft)).map(result => key -> result.isRight)
          }
        )
        .flatMap { results =>
          val failed = results.collect { case (key, false) => key }
          IO {
            if (failed.isEmpty) {
              toast.success(s"${results.size} log entries saved")
              closeDiscardingDraft()
            } else {
              val saved = results.collect { case (key, true) => key }.toSet
              state.update(current => current.copy(entries = current.entries -- saved))
              saveDraft()
              toast.error(s"${failed.size} of ${results.size} log entries could not be saved, submit again to retry")
            }
          }
        }
        .unsafeRunAndForget()
  }

  // ---- stepper

  private def breadcrumb(labels: Seq[String], current: Int, onSelect: Int => Unit): HtmlElement =
    new TgStepperBreadcrumb(labels.map(TgStep(_)), Val(current), Observer(onSelect)).element

  private def levelOne: Signal[HtmlElement] =
    state.signal.map(_.position).distinct.map { position =>
      val current = position match {
        case DailyPosition.ChooseMembers => 0
        case _: DailyPosition.Fill       => 1
        case DailyPosition.Review        => 2
      }
      breadcrumb(ProcessSteps, current, {
        case 0                 => jumpTo(_ == DailyPosition.ChooseMembers)
        case 1 if isPlanned    => jumpTo(_.isInstanceOf[DailyPosition.Fill])
        case 2 if isPlanned    => jumpTo(_ == DailyPosition.Review)
        case _                 => ()
      })
    }

  private def levelTwo: Signal[Option[HtmlElement]] =
    state.signal.map(s => (s.plan, s.position)).distinct.map {
      case (plan, DailyPosition.Fill(memberId, _, _)) =>
        val current = plan.indexWhere(_.member.id == memberId)
        Some(
          breadcrumb(
            plan.map(_.member.fullName),
            current,
            index => jumpTo { case DailyPosition.Fill(m, _, _) => m == plan(index).member.id; case _ => false }
          )
        )
      case _ => None
    }

  private def levelThree: Signal[Option[HtmlElement]] =
    state.signal.map(s => (s.plan, s.position)).distinct.map {
      case (plan, DailyPosition.Fill(memberId, taskId, _)) =>
        plan.find(_.member.id == memberId).map { member =>
          breadcrumb(
            member.tasks.map(_.name),
            member.tasks.indexWhere(_.taskId == taskId),
            index => jumpTo { case DailyPosition.Fill(_, t, _) => t == member.tasks(index).taskId; case _ => false }
          )
        }
      case _ => None
    }

  private def levelFour: Signal[Option[HtmlElement]] =
    state.signal.map(s => (s.plan, s.todayFirst, s.position)).distinct.map {
      case (plan, todayFirst, DailyPosition.Fill(memberId, taskId, date)) =>
        plan.find(_.member.id == memberId).flatMap(_.tasks.find(_.taskId == taskId)).map { task =>
          val days = MissingLogDays.inFillOrder(task.missingDays, todayFirst)
          breadcrumb(
            days.map(_.toString),
            days.indexOf(date),
            index => jumpTo { case DailyPosition.Fill(_, t, d) => t == taskId && d == days(index); case _ => false }
          )
        }
      case _ => None
    }

  private val stepper: HtmlElement =
    div(
      cls("flex flex-col gap-2 mb-4"),
      child <-- levelOne,
      child.maybe <-- levelTwo,
      child.maybe <-- levelThree,
      child.maybe <-- levelFour
    )

  // ---- step 1: members

  private val checkboxCls = "w-4 h-4 border border-default-strong rounded-xs bg-neutral-secondary-strong cursor-pointer"
  private val labelCls = "block mb-2.5 text-sm font-medium text-heading"
  private val secondaryButtonCls =
    "text-body bg-neutral-secondary-medium border border-default-medium hover:bg-neutral-tertiary-medium " +
      "font-medium leading-5 rounded-base text-xs px-3 py-1.5 whitespace-nowrap cursor-pointer"
  private val primaryButtonCls =
    "text-white bg-brand border border-transparent hover:bg-brand-strong focus:ring-4 focus:ring-brand-medium " +
      "shadow-xs font-medium leading-5 rounded-base text-xs px-3 py-1.5 whitespace-nowrap cursor-pointer " +
      "focus:outline-none"

  private def projectPicker: Signal[HtmlElement] =
    state.signal.map(_.projects).distinct.map { projects =>
      TgSelectDropdown(
        inputSignal = state.signal.map(_.projectId.map(_.toString).getOrElse("")),
        inputStateUpdater = Observer[String] { id =>
          val projectId = UUID.fromString(id)
          state.update(_.copy(projectId = Some(projectId)))
        },
        labelValue = "Project",
        options = projects.map { project =>
          val label = s"${project.icon} ${project.name}".trim
          TgSelectDropdownOption(project.id.toString, DropdownMeta(label, ""), () => span(label))
        },
        placeholder = "Select a project"
      ).htmlElement
    }

  private def memberRow(member: SearchAccountDto): HtmlElement =
    label(
      cls("flex items-center gap-2 py-1 text-sm text-body cursor-pointer"),
      input(
        `type`("checkbox"),
        cls(checkboxCls),
        checked <-- state.signal.map(_.selected.contains(member.id)).distinct,
        onChange.mapToChecked --> (on => toggleMember(member.id, on))
      ),
      member.fullName
    )

  private val chooseMembers: HtmlElement =
    div(
      div(cls("mb-4 max-w-sm"), child <-- projectPicker),
      label(cls(labelCls), "Who is this daily for?"),
      div(
        cls("flex gap-2 mb-2"),
        button(
          `type`("button"),
          cls(secondaryButtonCls),
          "Select all",
          onClick.mapToUnit --> (_ => state.update(s => s.copy(selected = s.members.map(_.id).toSet)))
        ),
        button(
          `type`("button"),
          cls(secondaryButtonCls),
          "Unselect all",
          onClick.mapToUnit --> (_ => state.update(_.copy(selected = Set.empty)))
        )
      ),
      div(
        cls("max-h-[24rem] overflow-y-auto rounded-base border border-default-medium p-3"),
        children <-- state.signal.map(_.members).distinct.map(_.map(memberRow))
      ),
      div(
        cls("mt-4 flex items-center gap-4 text-sm text-body"),
        span("Fill in from"),
        fillOrderRadio("Today back to the last entry", todayFirst = true),
        fillOrderRadio("Last entry up to today", todayFirst = false)
      )
    )

  private def fillOrderRadio(text: String, todayFirst: Boolean): HtmlElement =
    label(
      cls("flex items-center gap-1 cursor-pointer"),
      input(
        `type`("radio"),
        nameAttr("daily-fill-order"),
        cls(checkboxCls),
        checked <-- state.signal.map(_.todayFirst == todayFirst).distinct,
        onChange.mapTo(todayFirst) --> (value => state.update(_.copy(todayFirst = value)))
      ),
      text
    )

  // ---- step 2: fill in

  private def fillForm(memberId: UUID, taskId: UUID, date: LocalDate): HtmlElement = {
    val key = DailyEntryKey(memberId, taskId, date)
    val current = state.now().entries.getOrElse(key, defaultEntry(key))
    val task = state.now().plan.flatMap(_.tasks).find(_.taskId == taskId)
    div(
      h4(cls("text-base font-medium text-heading"), task.fold("")(_.name)),
      p(cls("mb-3 text-sm text-body-subtle"), s"${task.fold("")(_.statusName)} · $date"),
      label(cls(labelCls), "Status"),
      select(
        cls(tgInputStyle),
        cls("mb-3 max-w-xs"),
        LogEntryStatus.values.map(status =>
          option(value(status.value.toString), selected(status == current.status), status.toStringWithEmoji)
        ),
        onChange.mapToValue --> (v => edit(key)(_.copy(status = LogEntryStatus.withValue(v.toInt), skipped = false)))
      ),
      label(cls(labelCls), "Internal text"),
      textArea(
        cls(tgInputStyle),
        cls("mb-3 min-h-[6rem]"),
        defaultValue(current.textInternal),
        onInput.mapToValue --> (v => edit(key)(_.copy(textInternal = v, skipped = false)))
      ),
      label(cls(labelCls), "External text"),
      textArea(
        cls(tgInputStyle),
        cls("mb-3 min-h-[6rem]"),
        defaultValue(current.textExternal),
        onInput.mapToValue --> (v => edit(key)(_.copy(textExternal = v, skipped = false)))
      ),
      label(
        cls("flex items-center gap-2 text-sm text-body cursor-pointer"),
        input(
          `type`("checkbox"),
          cls(checkboxCls),
          checked(current.skipped),
          onChange.mapToChecked --> (on => edit(key)(_.copy(skipped = on)))
        ),
        "Skip this day"
      )
    )
  }

  // ---- step 3: review

  private def reviewView(s: DailyState): HtmlElement = {
    val toSave = s.toSave.toMap
    if (s.plan.isEmpty) div(cls("text-sm text-body"), "Nothing to fill in: the chosen members have no open tasks with missing days.")
    else
      div(
        s.plan.map { member =>
          div(
            cls("mb-4"),
            h4(cls("text-base font-medium text-heading"), member.member.fullName),
            member.tasks.map { task =>
              div(
                cls("ml-3 mt-2"),
                p(cls("text-sm font-medium text-heading"), task.name),
                MissingLogDays.inFillOrder(task.missingDays, s.todayFirst).map { date =>
                  val key = DailyEntryKey(member.member.id, task.taskId, date)
                  val text = toSave.get(key).fold("Not filled in, will not be saved")(d => d.status.toStringWithEmoji + " " + d.textInternal)
                  button(
                    `type`("button"),
                    cls("block w-full text-start text-sm text-body hover:text-fg-brand cursor-pointer"),
                    s"$date: $text",
                    onClick.mapToUnit --> (_ => jumpTo(_ == DailyPosition.Fill(member.member.id, task.taskId, date)))
                  )
                }
              )
            }
          )
        },
        p(cls("text-sm text-body"), s"${toSave.size} log entries will be saved.")
      )
  }

  private val body: Signal[HtmlElement] =
    state.signal.map(_.position).distinct.flatMapSwitch {
      case DailyPosition.ChooseMembers   => Val(chooseMembers)
      case DailyPosition.Fill(m, t, d)   => Val(fillForm(m, t, d))
      case DailyPosition.Review          => state.signal.map(reviewView)
    }

  // ---- footer

  private val onReview = state.signal.map(_.position == DailyPosition.Review)
  private val canGoBack = state.signal.map(s => s.positions.indexOf(s.position) > 0)
  private val canGoNext = state.signal.map(s => s.position != DailyPosition.ChooseMembers || s.selected.nonEmpty)

  private val footer: HtmlElement =
    div(
      cls(ModalStyles.footerCls),
      cls("justify-between space-x-0"),
      div(
        cls("flex gap-2"),
        button(`type`("button"), cls(secondaryButtonCls), "Cancel", onClick.mapToUnit --> (_ => closeKeepingDraft())),
        button(
          `type`("button"),
          cls(secondaryButtonCls),
          cls("hidden") <-- state.signal.map(_.draft.isEmpty).distinct,
          "Discard draft",
          onClick.mapToUnit --> (_ => closeDiscardingDraft())
        )
      ),
      div(
        cls("flex gap-2"),
        button(
          `type`("button"),
          cls(secondaryButtonCls),
          cls("hidden") <-- canGoBack.map(!_),
          "Back",
          onClick.mapToUnit --> (_ => move(-1))
        ),
        button(
          `type`("button"),
          cls(primaryButtonCls),
          cls("opacity-50 cursor-not-allowed") <-- canGoNext.map(!_),
          disabled <-- canGoNext.map(!_),
          child.text <-- onReview.map(if (_) "Submit all changes" else "Next"),
          onClick.mapToUnit --> (_ => if (state.now().position == DailyPosition.Review) submit() else next())
        )
      )
    )

  val element: HtmlElement =
    div(
      cls("fixed inset-0 z-[100] flex items-start justify-center pt-16 px-4 bg-white/90 dark:bg-neutral-primary/90"),
      onClick.filter(event => event.target == event.currentTarget).mapToUnit --> (_ => closeKeepingDraft()),
      onMountCallback { _ =>
        loadProjects()
        state.now().projectId.foreach(loadProject)
      },
      state.signal.map(_.projectId).distinct.changes.collect { case Some(id) => id } --> (id => loadProject(id)),
      state.signal.map(_.draft).distinct.changes.debounce(DraftSaveDebounce.toMillis.toInt) --> (_ => saveDraft()),
      windowEvents(_.onBeforeUnload) --> (_ => saveDraft()),
      documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> (_ => closeKeepingDraft()),
      div(
        cls(ModalStyles.contentBoxCls),
        cls("w-full max-w-4xl"),
        div(
          cls(ModalStyles.headerCls),
          h3(cls(ModalStyles.titleCls), "Daily"),
          button(
            `type`("button"),
            cls(ModalStyles.closeButtonCls),
            aria.label("Close"),
            TgIcons.closeIcon("w-4 h-4"),
            onClick.mapToUnit --> (_ => closeKeepingDraft())
          )
        ),
        stepper,
        child <-- body,
        footer
      )
    )
}
