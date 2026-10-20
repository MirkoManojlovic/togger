package com.togger.ui.slacker

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.dto.organization.OrganizationProjectResp
import com.togger.dto.{FindMilestoneResp, LogEntryDto}
import com.togger.logentry.LogEntryStatus
import com.togger.markdown.TgMarkdown
import com.togger.ui.auth.AccountService
import com.togger.ui.components.LogEntryStatusBadgeColor.LogEntryStatusBadgeColorOps
import com.togger.ui.components.LogEntryCalendarComponent
import com.togger.ui.components.{
  DropdownMeta,
  TaskStatusBadgeColor,
  TgBadge,
  TgBadgeColor,
  TgSelectDropdown,
  TgSelectDropdownOption,
  TgStep,
  TgStepperBreadcrumb
}
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.modal.ModalStyles
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.bulk.BadgeSpec
import com.togger.ui.task.{TaskFeService, TaskProgressComponent, TaskProjectDataLoader, TgDateFormater}
import com.togger.ui.teamdaily.{
  ScrollableStepper,
  TeamDailyComponent,
  TeamDailyEntryDraft,
  TeamDailyEntryForm,
  TeamDailyStatusGroup,
  TeamDailyStatusOrder,
  TeamDailyTaskBadges,
  TeamDailyTaskPlan
}
import com.togger.ui.timebox.gantt.GanttLogStatus
import com.togger.ui.toast.Toast

import java.time.{DayOfWeek, LocalDate, LocalTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.duration.DurationInt

case class SlackerState(
  projectId: Option[UUID],
  projects: Seq[OrganizationProjectResp] = Seq.empty,
  milestones: Seq[FindMilestoneResp] = Seq.empty,
  loaded: Boolean = false,
  candidates: Seq[TeamDailyTaskPlan] = Seq.empty,
  selectedTasks: Option[Set[UUID]] = None,
  pastFirst: Set[UUID] = Set.empty,
  position: SlackerPosition = SlackerPosition.ChooseTasks,
  entries: Map[SlackerEntryKey, TeamDailyEntryDraft] = Map.empty
) {

  /** What is filled in: the chosen tasks of the candidates. */
  def plan: Seq[TeamDailyTaskPlan] = SlackerPlan.chosen(candidates, selectedTasks)

  def isFilled(task: TeamDailyTaskPlan): Boolean =
    task.missingDays.forall(date => entries.contains(SlackerEntryKey(task.taskId, date)))

  /** The status of each day of a task that is filled in and will be saved. */
  def pendingStatuses(taskId: UUID): Map[LocalDate, LogEntryStatus] =
    entries.collect { case (SlackerEntryKey(`taskId`, date), draft) => date -> draft.status }

  def isTaskSelected(taskId: UUID): Boolean = selectedTasks.forall(_.contains(taskId))

  def positions: Seq[SlackerPosition] = SlackerPlan.positions(plan, pastFirst)

  def draft: SlackerDraft =
    SlackerDraft(
      projectId,
      selectedTasks.map(_.toSeq),
      pastFirst.toSeq,
      entries.map { case (key, draft) => SlackerEntryRecord(key, draft) }.toSeq
    )

  /** The filled in days of the chosen tasks, oldest first within a task. */
  def toSave: Seq[(SlackerEntryKey, TeamDailyEntryDraft)] =
    plan
      .flatMap(task => task.missingDays.sortBy(_.toEpochDay).map(SlackerEntryKey(task.taskId, _)))
      .flatMap(key => entries.get(key).map(key -> _))
}

/** The slacker: the missing log entries of the tasks of the current user, filled in from today into the past or from the
  * past up to today, then accepted and submitted at once. Looks and works like [[com.togger.ui.teamdaily.TeamDailyComponent]].
  */
class SlackerComponent(
  taskService: TaskFeService,
  projectDataLoader: TaskProjectDataLoader,
  milestoneService: MilestoneFeService,
  spinnerService: SpinnerService,
  accountService: AccountService,
  tgClock: TgClock,
  tgDateFormater: TgDateFormater,
  tgMarkdown: TgMarkdown,
  currentProjectId: Option[UUID],
  localStorage: LocalStorage,
  toast: Toast,
  onClose: () => Unit,
  stepsPerArrowClick: Int = TeamDailyComponent.DefaultStepsPerArrowClick
)(implicit ioRuntime: IORuntime) {

  private val DraftSaveDebounce = 300.millis
  private val BreadcrumbScrollCls =
    "overflow-x-auto [&_ol]:w-max [&_ol]:min-w-full [&_li]:shrink-0 [&_li]:whitespace-nowrap"
  private val ProcessSteps = Seq("Choose tasks", "Fill in the status", "Review changes")

  private val state = Var(SlackerState(projectId = currentProjectId))

  private val currentAccountId: Option[UUID] = accountService.get.map(_.id)

  private def draftStore(projectId: UUID): Option[SlackerDraftStore] =
    currentAccountId.map(new SlackerDraftStore(localStorage, _, projectId))

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

  /** Asked before the modal closes with something filled in, so a stray click can't lose it. */
  private val confirmingClose = Var(false)

  private def requestClose(): Unit =
    if (state.now().draft.isEmpty) closeDiscardingDraft() else confirmingClose.set(true)

  private def closeDiscardingDraft(): Unit = {
    persisting.set(false)
    state.now().projectId.flatMap(draftStore).foreach(_.clear())
    onClose()
  }

  // ---- loading

  private def workDaysOf(projects: Seq[OrganizationProjectResp], projectId: UUID): Set[DayOfWeek] =
    projects
      .find(_.id == projectId)
      .map(_.workDays.map(DayOfWeek.of))
      .filter(_.nonEmpty)
      .getOrElse(WorkDayCalendar.DefaultWorkDays)

  /** The projects, the milestones and the tasks of the project, planned into the days still to log. */
  private def loadProject(projectId: UUID): Unit =
    spinnerService
      .runWithSpinner(
        for {
          projects <- projectDataLoader.loadProjects()
          milestones <- milestoneService.findAllByProjectId(projectId)
          tasks <- taskService.findAllByProjectId(projectId)
        } yield (projects, milestones, tasks)
      )
      .flatMap { case (projects, milestones, tasks) =>
        tasks.fold(
          error => IO(toast.error(error)),
          found =>
            IO {
              if (state.now().projectId.contains(projectId))
                applyPlan(
                  projectId,
                  projects,
                  milestones.toOption.flatMap(_.body).getOrElse(Seq.empty),
                  SlackerPlan.build(
                    found.body.getOrElse(Seq.empty),
                    currentAccountId.get,
                    workDaysOf(projects, projectId),
                    tgClock.currentDate()
                  )
                )
            }
        )
      }
      .unsafeRunAndForget()

  private def applyPlan(
    projectId: UUID,
    projects: Seq[OrganizationProjectResp],
    milestones: Seq[FindMilestoneResp],
    plan: Seq[TeamDailyTaskPlan]
  ): Unit = {
    val restored = draftStore(projectId).flatMap(_.load())
    val ids = SlackerPlan.taskIds(plan)
    // a day that was logged in the meantime is no longer missing, so its draft is dropped
    val stillMissing = plan.flatMap(task => task.missingDays.map(SlackerEntryKey(task.taskId, _))).toSet
    state.update(
      _.copy(
        projects = projects,
        milestones = milestones,
        loaded = true,
        candidates = plan,
        selectedTasks = restored.flatMap(_.taskIds.map(_.toSet.intersect(ids))),
        pastFirst = restored.fold(Set.empty[UUID])(_.pastFirst.toSet.intersect(ids)),
        entries = restored.fold(Map.empty[SlackerEntryKey, TeamDailyEntryDraft])(
          _.entries.filter(record => stillMissing.contains(record.key)).map(r => r.key -> r.draft).toMap
        ),
        position = SlackerPosition.ChooseTasks
      )
    )
    if (restored.isDefined) toast.success("Restored your unfinished slacker")
  }

  // ---- navigation

  private def move(delta: Int): Unit = {
    val current = state.now()
    val positions = current.positions
    val target = positions.indexOf(current.position) + delta
    if (target >= 0 && target < positions.size) state.update(_.copy(position = positions(target)))
  }

  private def jumpTo(find: SlackerPosition => Boolean): Unit =
    state.now().positions.find(find).foreach(target => state.update(_.copy(position = target)))

  // ---- editing

  private def defaultEntry(key: SlackerEntryKey): TeamDailyEntryDraft = {
    val current = state.now()
    current.plan
      .find(_.taskId == key.taskId)
      .fold(TeamDailyEntryDraft(LogEntryStatus.InProgress, ""))(
        SlackerPlan.defaultEntry(_, current.pastFirst, key.date, current.entries)
      )
  }

  private def edit(key: SlackerEntryKey)(change: TeamDailyEntryDraft => TeamDailyEntryDraft): Unit =
    state.update(current =>
      current.copy(entries = current.entries.updated(key, change(current.entries.getOrElse(key, defaultEntry(key)))))
    )

  private def setPastFirst(taskId: UUID, on: Boolean): Unit =
    state.update(current => current.copy(pastFirst = if (on) current.pastFirst + taskId else current.pastFirst - taskId))

  // ---- submit

  private def logEntryOf(key: SlackerEntryKey, draft: TeamDailyEntryDraft): LogEntryDto = {
    val createdAt =
      if (key.date == tgClock.currentDate()) tgClock.currentInstant
      else tgClock.instantFrom(key.date, LocalTime.NOON, ZoneOffset.UTC)
    LogEntryDto(
      id = UUID.randomUUID(),
      taskId = key.taskId,
      createdBy = currentAccountId.get,
      createdAt = createdAt,
      latestVersion = UUID.randomUUID(),
      textInternal = "",
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

  private def breadcrumb(steps: Seq[TgStep], current: Int, onSelect: Int => Unit): HtmlElement =
    new ScrollableStepper(
      new TgStepperBreadcrumb(steps, Val(current), Observer(onSelect)).element
        .amend(cls(BreadcrumbScrollCls)),
      stepsPerArrowClick,
      current
    ).element

  private def isLoaded: Boolean = state.now().loaded

  private def levelOne: Signal[HtmlElement] =
    state.signal.map(_.position).distinct.map { position =>
      val current = position match {
        case SlackerPosition.ChooseTasks => 0
        case _: SlackerPosition.Fill     => 1
        case SlackerPosition.Review      => 2
      }
      breadcrumb(
        ProcessSteps.map(TgStep(_)),
        current,
        {
          case 0                  => jumpTo(_ == SlackerPosition.ChooseTasks)
          case 1 if isLoaded      => jumpTo(_.isInstanceOf[SlackerPosition.Fill])
          case 2 if isLoaded      => jumpTo(_ == SlackerPosition.Review)
          case _                  => ()
        }
      )
    }

  private def levelTwo: Signal[Option[HtmlElement]] =
    state.signal.map(s => (s.plan, s.plan.map(s.isFilled), s.position)).distinct.map {
      case (plan, filled, SlackerPosition.Fill(taskId, _)) =>
        Some(
          breadcrumb(
            plan.zip(filled).map { case (task, done) => TgStep(task.name, done) },
            plan.indexWhere(_.taskId == taskId),
            index => jumpTo { case SlackerPosition.Fill(t, _) => t == plan(index).taskId; case _ => false }
          )
        )
      case _ => None
    }

  private def levelThree: Signal[Option[HtmlElement]] =
    state.signal.map(s => (s.plan, s.entries.keySet, s.pastFirst, s.position)).distinct.map {
      case (plan, entries, pastFirst, SlackerPosition.Fill(taskId, date)) =>
        plan.find(_.taskId == taskId).map { task =>
          val days = SlackerPlan.daysOf(task, pastFirst)
          breadcrumb(
            days.map(day => TgStep(tgDateFormater.asAmericanDateWithDayAbrv(day), entries.contains(SlackerEntryKey(taskId, day)))),
            days.indexOf(date),
            index => jumpTo(_ == SlackerPosition.Fill(taskId, days(index)))
          )
        }
      case _ => None
    }

  private val stepper: HtmlElement =
    div(
      cls("flex flex-col gap-2 mb-4"),
      child <-- levelOne,
      child.maybe <-- levelTwo,
      child.maybe <-- levelThree
    )

  // ---- shared look

  private val checkboxCls = "w-4 h-4 border border-default-strong rounded-xs bg-neutral-secondary-strong cursor-pointer"
  private val labelCls = "block mb-2.5 text-sm font-medium text-heading"
  private val secondaryButtonCls =
    "text-body bg-neutral-secondary-medium border border-default-medium hover:bg-neutral-tertiary-medium " +
      "font-medium leading-5 rounded-base text-xs px-3 py-1.5 whitespace-nowrap cursor-pointer"
  private val smallButtonCls =
    "text-body bg-neutral-secondary-medium border border-default-medium hover:bg-neutral-tertiary-medium " +
      "font-medium leading-4 rounded-base text-[11px] px-2 py-0.5 whitespace-nowrap cursor-pointer"
  private val primaryButtonCls =
    "text-white bg-brand border border-transparent hover:bg-brand-strong focus:ring-4 focus:ring-brand-medium " +
      "shadow-xs font-medium leading-5 rounded-base text-xs px-3 py-1.5 whitespace-nowrap cursor-pointer " +
      "focus:outline-none"

  private def badgeRow(specs: Seq[BadgeSpec]): HtmlElement =
    div(
      cls("flex flex-wrap items-center gap-1.5"),
      specs.map { spec =>
        val neutral = spec.color == TgBadgeColor.Gray || spec.color == TgBadgeColor.Alternative
        TgBadge(text = spec.text, color = spec.color, bordered = true, extraCls = if (neutral) "dark:text-body" else "").element
      }
    )

  private def statusTab(text: String, active: Signal[Boolean], onSelect: () => Unit): HtmlElement =
    button(
      `type`("button"),
      cls(smallButtonCls),
      cls("border-brand font-semibold text-fg-brand") <-- active,
      text,
      onClick.mapToUnit --> (_ => onSelect())
    )

  // ---- step 1: choose tasks

  private def projectPicker: Signal[HtmlElement] =
    state.signal.map(_.projects).distinct.map { projects =>
      TgSelectDropdown(
        inputSignal = state.signal.map(_.projectId.map(_.toString).getOrElse("")),
        inputStateUpdater = Observer[String](id => state.update(_.copy(projectId = Some(UUID.fromString(id))))),
        labelValue = "Project",
        options = projects.map { project =>
          val label = s"${project.icon} ${project.name}".trim
          TgSelectDropdownOption(project.id.toString, DropdownMeta(label, ""), () => span(label))
        },
        placeholder = "Select a project"
      ).htmlElement
    }

  private def setTasksSelected(taskIds: Set[UUID], on: Boolean): Unit =
    state.update { current =>
      val all = SlackerPlan.taskIds(current.candidates)
      val chosen = current.selectedTasks.getOrElse(all)
      val updated = if (on) chosen ++ taskIds else chosen -- taskIds
      current.copy(selectedTasks = if (updated == all) None else Some(updated))
    }

  private def missingText(task: TeamDailyTaskPlan): String =
    if (task.missingDays.size == 1) "1 day missing" else s"${task.missingDays.size} days missing"

  private def taskRow(task: TeamDailyTaskPlan): HtmlElement =
    label(
      cls("flex items-start gap-2 py-1 text-sm text-body cursor-pointer"),
      input(
        `type`("checkbox"),
        cls(checkboxCls),
        cls("mt-0.5 shrink-0"),
        checked <-- state.signal.map(_.isTaskSelected(task.taskId)).distinct,
        onChange.mapToChecked --> (on => setTasksSelected(Set(task.taskId), on))
      ),
      div(
        cls("flex flex-col gap-1"),
        div(
          cls("flex flex-wrap items-center gap-2"),
          span(cls("font-medium text-black dark:text-white"), task.name),
          badgeRow(TeamDailyTaskBadges.labels(task)),
          TaskProgressComponent
            .fromDescription(Val(task.description), ringSizeCls = "w-3 h-3", textSizeCls = "text-xs")
            .element,
          span(cls("text-body-subtle"), missingText(task))
        ),
        badgeRow(TeamDailyTaskBadges.milestoneAndPriority(task, state.now().milestones))
      )
    )

  private def statusGroup(group: TeamDailyStatusGroup): HtmlElement =
    div(
      cls("mb-2 ms-3"),
      h5(cls("mb-1 text-sm font-medium text-body"), s"${group.statusName} (${group.tasks.size})"),
      group.tasks.map(taskRow)
    )

  private val chooseTasks: HtmlElement =
    div(
      div(cls("mb-4 max-w-sm"), child <-- projectPicker),
      label(cls(labelCls), "Which tasks do you want to add missing statuses for?"),
      div(
        cls("flex gap-2 mb-2"),
        button(
          `type`("button"),
          cls(secondaryButtonCls),
          "Select all",
          onClick.mapToUnit --> (_ => state.update(_.copy(selectedTasks = None)))
        ),
        button(
          `type`("button"),
          cls(secondaryButtonCls),
          "Unselect all",
          onClick.mapToUnit --> (_ => state.update(_.copy(selectedTasks = Some(Set.empty))))
        )
      ),
      div(
        cls("rounded-base border border-default-medium p-3"),
        child.maybe <-- state.signal.map(s => (s.loaded, s.candidates.isEmpty)).distinct.map {
          case (true, true) => Some(p(cls("text-sm text-body"), "You are all caught up: none of your open tasks misses a log entry."))
          case _            => None
        },
        children <-- state.signal.map(_.candidates).distinct.map(candidates => SlackerPlan.byStatus(candidates).map(statusGroup))
      )
    )

  // ---- step 2: fill in

  /** The work days of the chosen project, the calendar default until the projects are loaded. */
  private def projectWorkDays: Set[DayOfWeek] = {
    val current = state.now()
    current.projectId.fold(WorkDayCalendar.DefaultWorkDays)(workDaysOf(current.projects, _))
  }

  private def taskHeader(
    task: TeamDailyTaskPlan,
    headingCls: String,
    trailing: Option[HtmlElement] = None
  ): HtmlElement =
    div(
      cls("mb-3 flex flex-col gap-1.5"),
      div(
        cls("flex flex-wrap items-center gap-2"),
        h4(cls(headingCls), task.name),
        if (task.labels.isEmpty) span(cls("text-sm"), aria.label("No labels"), "🏷️")
        else badgeRow(TeamDailyTaskBadges.labels(task)),
        TaskProgressComponent
          .fromDescription(Val(task.description), ringSizeCls = "w-3 h-3", textSizeCls = "text-xs")
          .element,
        trailing.map(_.amend(cls("ms-auto")))
      ),
      badgeRow(
        TeamDailyTaskBadges.milestoneAndPriority(task, state.now().milestones) :+
          BadgeSpec(task.statusName, TaskStatusBadgeColor.of(task.statusName))
      ),
      badgeRow(
        TeamDailyTaskBadges.dates(task, tgClock.currentDate(), projectWorkDays, tgDateFormater.asAmericanDateWithDayAbrv)
      )
    )

  private def directionTabs(taskId: UUID): HtmlElement =
    div(
      cls("flex flex-wrap items-center gap-1.5"),
      statusTab("Today → past", state.signal.map(!_.pastFirst.contains(taskId)).distinct, () => setPastFirst(taskId, on = false)),
      statusTab("Past → today", state.signal.map(_.pastFirst.contains(taskId)).distinct, () => setPastFirst(taskId, on = true))
    )

  /** Counts the repeats, each one builds the fill form again from the entry it has just been given. */
  private val formRefresh = Var(0)

  private def repeat(key: SlackerEntryKey, entry: LogEntryDto): Unit = {
    edit(key)(_ => TeamDailyEntryDraft(entry.status, entry.textExternal))
    formRefresh.update(_ + 1)
  }

  private def isRepeatable(task: TeamDailyTaskPlan, entry: LogEntryDto): Boolean =
    TeamDailyComponent.RepeatableStatuses.contains(entry.status) || TeamDailyStatusOrder.isOnHold(task.statusName)

  private def repeatButton(key: SlackerEntryKey, entry: LogEntryDto, focusRepeat: Boolean): HtmlElement =
    button(
      `type`("button"),
      cls(smallButtonCls),
      cls("focus:outline-none focus:ring-2 focus:ring-brand focus:border-brand focus:text-fg-brand"),
      "Repeat last status",
      onMountCallback(context => if (focusRepeat) context.thisNode.ref.focus()),
      onClick.mapToUnit --> (_ => repeat(key, entry))
    )

  private def entryView(entry: LogEntryDto): HtmlElement =
    div(
      div(
        cls("flex items-center gap-2 mb-2"),
        TgBadge(
          text = tgDateFormater.asAmericanDateWithDayAbrv(GanttLogStatus.toLocalDate(entry.createdAt)),
          bordered = true,
          color = TgBadgeColor.Gray
        ).element,
        TgBadge(text = entry.status.toStringWithEmoji, color = entry.status.badgeColor, bordered = true).element
      ),
      div(cls("max-h-48 overflow-y-auto"), tgMarkdown.toLaminarDiv(entry.textExternal))
    )

  private def dayView(day: LocalDate, entries: Seq[LogEntryDto]): HtmlElement =
    if (entries.isEmpty)
      div(
        div(
          cls("flex items-center gap-2 mb-2"),
          TgBadge(text = tgDateFormater.asAmericanDateWithDayAbrv(day), bordered = true, color = TgBadgeColor.Gray).element
        ),
        p(cls("text-sm text-body-subtle"), "No status update")
      )
    else div(entries.map(entryView))

  /** What was logged last above the form; a day of the tiny calendar beside it shows what was logged that day instead. */
  private def lastStatus(
    key: SlackerEntryKey,
    task: TeamDailyTaskPlan,
    entry: LogEntryDto,
    canRepeat: Boolean,
    focusRepeat: Boolean
  ): HtmlElement = {
    val selectedDay = Var(Option.empty[LocalDate])
    val dayEntries = (day: LocalDate) =>
      task.logEntries.filter(e => GanttLogStatus.toLocalDate(e.createdAt) == day).sortBy(_.createdAt)
    div(
      cls("mb-3 flex items-start gap-3 rounded-base border border-default-medium bg-neutral-secondary-soft p-3"),
      div(
        cls("min-w-0 flex-1"),
        div(
          cls("mb-2 flex flex-wrap items-center gap-1.5"),
          statusTab("Last status", selectedDay.signal.map(_.isEmpty), () => selectedDay.set(None)),
          child.maybe <-- selectedDay.signal.map(_.map(day =>
            statusTab(tgDateFormater.asAmericanDateWithDayAbrv(day), Val(true), () => ())
          )),
          child.maybe <-- selectedDay.signal.map(selected =>
            Option.when(canRepeat && selected.isEmpty)(repeatButton(key, entry, focusRepeat))
          )
        ),
        child <-- selectedDay.signal.map {
          case None      => entryView(entry)
          case Some(day) => dayView(day, dayEntries(day))
        }
      ),
      new LogEntryCalendarComponent(
        task.logEntries,
        tgDateFormater,
        projectWorkDays,
        selectedDay,
        startDate = Some(task.startDate),
        dueDate = Some(task.dueDate),
        markedDay = Some(key.date),
        extraDays = task.missingDays,
        pendingStatuses = state.signal.map(_.pendingStatuses(task.taskId)).distinct,
        onPendingClick = Some(day => jumpTo(_ == SlackerPosition.Fill(task.taskId, day)))
      ).tinyElement
        .amend(cls("shrink-0"))
    )
  }

  private def fillForm(taskId: UUID, date: LocalDate): HtmlElement = {
    val key = SlackerEntryKey(taskId, date)
    state.now().plan.find(_.taskId == taskId).fold[HtmlElement](div()) { task =>
      val current = state.now().entries.getOrElse(key, defaultEntry(key))
      val latest = task.latestEntry
      val canRepeat = latest.exists(isRepeatable(task, _))
      val focusRepeat = canRepeat && !state.now().entries.contains(key)
      div(
        cls("rounded-base border border-default-medium p-4"),
        taskHeader(task, headingCls = "text-base font-medium text-heading", trailing = Some(directionTabs(taskId))),
        latest.map(lastStatus(key, task, _, canRepeat, focusRepeat)),
        new TeamDailyEntryForm(
          taskId,
          date,
          current,
          tgDateFormater,
          draft => edit(key)(_ => draft),
          focusEditor = !focusRepeat,
          title = s"Status for ${task.name}"
        ).element
      )
    }
  }

  // ---- step 3: review

  private def reviewView(s: SlackerState): HtmlElement = {
    val toSave = s.toSave.toMap
    if (s.plan.isEmpty) div(cls("text-sm text-body"), "Nothing to fill in: none of the chosen tasks misses a log entry.")
    else
      div(
        s.plan.map { task =>
          div(
            cls("mt-2 rounded-base border border-default-medium p-3"),
            taskHeader(task, headingCls = "text-sm font-medium text-heading"),
            task.missingDays.sortBy(_.toEpochDay).map { date =>
              val key = SlackerEntryKey(task.taskId, date)
              div(
                cls("mb-2 cursor-pointer rounded-base border-s-4 border-brand p-2 ps-3 shadow-sm dark:shadow-white/10 hover:bg-neutral-secondary-medium"),
                div(
                  cls("flex items-center gap-2 mb-2"),
                  TgBadge(text = tgDateFormater.asAmericanDateWithDayAbrv(date), bordered = true, color = TgBadgeColor.Gray).element,
                  toSave.get(key).map(entry =>
                    TgBadge(text = entry.status.toStringWithEmoji, color = entry.status.badgeColor, bordered = true).element
                  )
                ),
                toSave.get(key).fold[HtmlElement](p(cls("text-sm text-body-subtle"), "Not filled in, will not be saved"))(entry =>
                  tgMarkdown.toLaminarDiv(entry.textExternal)
                ),
                onClick.mapToUnit --> (_ => jumpTo(_ == SlackerPosition.Fill(task.taskId, date)))
              )
            }
          )
        },
        p(cls("mt-3 text-sm text-body"), s"${toSave.size} log entries will be saved.")
      )
  }

  private val body: Signal[HtmlElement] =
    state.signal.map(_.position).distinct.flatMapSwitch {
      case SlackerPosition.ChooseTasks => Val(chooseTasks)
      case SlackerPosition.Fill(t, d)  => formRefresh.signal.map(_ => fillForm(t, d))
      case SlackerPosition.Review      => state.signal.map(reviewView)
    }

  // ---- footer

  private val onReview = state.signal.map(_.position == SlackerPosition.Review)
  private val canGoBack = state.signal.map(s => s.positions.indexOf(s.position) > 0)
  private val canGoNext = state.signal.map { s =>
    s.position match {
      case SlackerPosition.ChooseTasks => s.plan.nonEmpty
      case _                           => true
    }
  }

  private val stepButtons: HtmlElement =
    div(
      // Next comes first in the DOM so Tab reaches it before Back; the row is reversed to show Back on the left
      cls("flex flex-row-reverse gap-2"),
      button(
        `type`("button"),
        cls(primaryButtonCls),
        cls("opacity-50 cursor-not-allowed") <-- canGoNext.map(!_),
        disabled <-- canGoNext.map(!_),
        child.text <-- onReview.map(if (_) "Submit all changes" else "Next"),
        onClick.mapToUnit --> (_ => if (state.now().position == SlackerPosition.Review) submit() else move(1))
      ),
      button(
        `type`("button"),
        cls(secondaryButtonCls),
        cls("hidden") <-- canGoBack.map(!_),
        "Back",
        onClick.mapToUnit --> (_ => move(-1))
      )
    )

  private val closeConfirmation: HtmlElement =
    div(
      cls("flex flex-wrap items-center justify-end gap-2"),
      span(cls("text-sm text-body"), "Your slacker is saved as a draft."),
      button(
        `type`("button"),
        cls(secondaryButtonCls),
        "Keep editing",
        onClick.mapToUnit --> (_ => confirmingClose.set(false))
      ),
      button(`type`("button"), cls(secondaryButtonCls), "Discard", onClick.mapToUnit --> (_ => closeDiscardingDraft())),
      button(
        `type`("button"),
        cls(primaryButtonCls),
        "Close, keep draft",
        onClick.mapToUnit --> (_ => closeKeepingDraft())
      )
    )

  private val footer: HtmlElement =
    div(
      cls(ModalStyles.footerCls),
      cls("justify-end space-x-0"),
      child <-- confirmingClose.signal.map(if (_) closeConfirmation else stepButtons)
    )

  val element: HtmlElement =
    div(
      cls("fixed inset-0 z-[100] flex items-start justify-center pt-16 px-4 bg-white/90 dark:bg-neutral-primary/90"),
      onClick.filter(event => event.target == event.currentTarget).mapToUnit --> (_ => requestClose()),
      onMountCallback { _ =>
        projectDataLoader.loadProjects().map(projects => state.update(_.copy(projects = projects))).unsafeRunAndForget()
      },
      // a project chosen in the picker is planned from scratch; the one the modal opens with is planned on mount
      state.signal.map(_.projectId).distinct --> Observer[Option[UUID]](_.foreach(loadProject)),
      state.signal.map(_.draft).distinct.changes.debounce(DraftSaveDebounce.toMillis.toInt) --> (_ => saveDraft()),
      windowEvents(_.onBeforeUnload) --> (_ => saveDraft()),
      documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit -->
        (_ => if (confirmingClose.now()) confirmingClose.set(false) else requestClose()),
      div(
        cls(ModalStyles.contentBoxCls),
        cls("flex w-full max-w-4xl flex-col min-h-[75vh] max-h-[calc(100vh-6rem)]"),
        div(
          cls(ModalStyles.headerCls),
          h3(cls(ModalStyles.titleCls), "Slacker"),
          button(
            `type`("button"),
            cls(ModalStyles.closeButtonCls),
            aria.label("Close"),
            TgIcons.closeIcon("w-4 h-4"),
            onClick.mapToUnit --> (_ => requestClose())
          )
        ),
        stepper,
        div(cls("min-h-0 flex-1 overflow-y-auto"), child <-- body),
        footer
      )
    )
}
