package com.togger.ui.teamdaily

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.ui.logentry.LogEntryMemberBadge
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.dto.organization.OrganizationProjectResp
import com.togger.common.TgResponseTypes.TgResp
import com.togger.dto.{FindMilestoneResp, LogEntryDto, SearchAccountDto}
import com.togger.logentry.LogEntryStatus
import com.togger.markdown.TgMarkdown
import com.togger.ui.auth.AccountService
import com.togger.ui.components.LogEntryStatusBadgeColor.LogEntryStatusBadgeColorOps
import com.togger.ui.components.LogEntryCalendarComponent
import com.togger.ui.components.{
  DropdownMeta,
  TgBadge,
  TgBadgeColor,
  TgSelectDropdown,
  TgSelectDropdownOption,
  TgStep,
  TgStepperBreadcrumb,
  TaskStatusBadgeColor
}
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.modal.ModalStyles
import com.togger.ui.organization.TgIcons
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.bulk.BadgeSpec
import com.togger.ui.task.{TaskFeService, TaskProjectData, TaskProjectDataLoader, TgDateFormater, TaskProgressComponent}
import com.togger.ui.timebox.gantt.GanttLogStatus
import com.togger.ui.toast.Toast

import java.time.{DayOfWeek, LocalDate, LocalTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.duration.DurationInt

case class TeamDailyState(
  projectId: Option[UUID],
  projects: Seq[OrganizationProjectResp] = Seq.empty,
  members: Seq[SearchAccountDto] = Seq.empty,
  milestones: Seq[FindMilestoneResp] = Seq.empty,
  selected: Set[UUID] = Set.empty,
  candidates: Seq[TeamDailyMemberPlan] = Seq.empty,
  selectedTasks: Option[Set[UUID]] = None,
  position: TeamDailyPosition = TeamDailyPosition.ChooseMembers,
  entries: Map[TeamDailyEntryKey, TeamDailyEntryDraft] = Map.empty
) {

  /** What is filled in: the chosen tasks of the candidates. */
  def plan: Seq[TeamDailyMemberPlan] = TeamDailyPlan.chosen(candidates, selectedTasks)

  def isFilled(memberId: UUID, task: TeamDailyTaskPlan): Boolean =
    task.todayEntry.nonEmpty || task.missingDays.forall(date => entries.contains(TeamDailyEntryKey(memberId, task.taskId, date)))

  def isFilled(member: TeamDailyMemberPlan): Boolean = member.tasks.forall(isFilled(member.member.id, _))

  def isTaskSelected(taskId: UUID): Boolean = selectedTasks.forall(_.contains(taskId))

  def positions: Seq[TeamDailyPosition] = TeamDailyPlan.positions(plan)

  def draft: TeamDailyDraft =
    TeamDailyDraft(projectId, selected.toSeq, selectedTasks.map(_.toSeq), entries.map { case (key, draft) => TeamDailyEntryRecord(key, draft) }.toSeq)

  def toSave: Seq[(TeamDailyEntryKey, TeamDailyEntryDraft)] =
    plan.flatMap(member => member.tasks.flatMap(task => task.missingDays.map(TeamDailyEntryKey(member.member.id, task.taskId, _))))
      .flatMap(key => entries.get(key).map(key -> _))
}

/** The team daily: choose who it is for, fill in the missing log entries of their open tasks, accept all and submit. */
class TeamDailyComponent(
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
  /** A row with more steps than fit scrolls sideways instead of overflowing: the steps keep their size and the list
    * grows to its content, but never narrower than the row.
    */
  private val BreadcrumbScrollCls =
    "overflow-x-auto [&_ol]:w-max [&_ol]:min-w-full [&_li]:shrink-0 [&_li]:whitespace-nowrap"
  private val ProcessSteps = Seq("Choose members", "Choose tasks", "Fill in the status", "Review changes")

  private val state = Var(TeamDailyState(projectId = currentProjectId))

  private val currentAccountId: Option[UUID] = accountService.get.map(_.id)
  private val currentAccountName: String = accountService.get.map(LogEntryMemberBadge.nameOf).getOrElse("")

  private def draftStore(projectId: UUID): Option[TeamDailyDraftStore] =
    currentAccountId.map(new TeamDailyDraftStore(localStorage, _, projectId))

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

  private def loadProjects(): Unit =
    projectDataLoader
      .loadProjects()
      .map(projects => state.update(_.copy(projects = projects)))
      .unsafeRunAndForget()

  private def loadProject(projectId: UUID): Unit =
    projectDataLoader
      .loadProjectData(projectId)
      .flatMap(data => milestoneService.findAllByProjectId(projectId).map(milestones => (data, milestones)))
      .map { case (data, milestones) =>
        if (state.now().projectId.contains(projectId)) applyProject(projectId, data, milestones)
      }
      .unsafeRunAndForget()

  private def applyProject(
    projectId: UUID,
    data: TaskProjectData,
    milestones: TgResp[Seq[FindMilestoneResp]]
  ): Unit = {
    val restored = draftStore(projectId).flatMap(_.load())
    val memberIds = data.members.map(_.id).toSet
    state.update { current =>
      current.copy(
        members = data.members,
        milestones = milestones.toOption.flatMap(_.body).getOrElse(Seq.empty),
        selected = restored.fold(memberIds)(_.memberIds.toSet.intersect(memberIds)),
        entries = restored.fold(Map.empty[TeamDailyEntryKey, TeamDailyEntryDraft])(_.entries.map(r => r.key -> r.draft).toMap),
        candidates = Seq.empty,
        selectedTasks = restored.flatMap(_.taskIds.map(_.toSet)),
        position = TeamDailyPosition.ChooseMembers
      )
    }
    if (restored.isDefined) toast.success("Restored your unfinished team daily")
  }

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
                val plan = TeamDailyPlan.build(
                  tasks.body.getOrElse(Seq.empty),
                  current.members,
                  current.selected,
                  tgClock.currentDate()
                )
                state.update { s =>
                  s.copy(
                    candidates = plan,
                    selectedTasks = s.selectedTasks.map(_.intersect(TeamDailyPlan.taskIds(plan))),
                    position = TeamDailyPosition.ChooseTasks
                  )
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
    if (state.now().position == TeamDailyPosition.ChooseMembers) loadPlan() else move(1)

  private def jumpTo(find: TeamDailyPosition => Boolean): Unit =
    state.now().positions.find(find).foreach(target => state.update(_.copy(position = target)))

  private def isPlanned: Boolean = state.now().position != TeamDailyPosition.ChooseMembers || state.now().candidates.nonEmpty

  // ---- editing

  private def defaultEntry(key: TeamDailyEntryKey): TeamDailyEntryDraft = {
    val task = state.now().plan.flatMap(_.tasks).find(_.taskId == key.taskId)
    task.flatMap(_.todayEntry) match {
      case Some(existing) => TeamDailyEntryDraft(existing.status, existing.textExternal)
      case None           => TeamDailyEntryDraft(task.fold[LogEntryStatus](LogEntryStatus.InProgress)(_.previousStatus), "")
    }
  }

  private def edit(key: TeamDailyEntryKey)(change: TeamDailyEntryDraft => TeamDailyEntryDraft): Unit =
    state.update(current =>
      current.copy(entries = current.entries.updated(key, change(current.entries.getOrElse(key, defaultEntry(key)))))
    )

  private def toggleMember(id: UUID, on: Boolean): Unit =
    state.update(current => current.copy(selected = if (on) current.selected + id else current.selected - id))

  // ---- submit

  private def logEntryOf(key: TeamDailyEntryKey, draft: TeamDailyEntryDraft): LogEntryDto = {
    val existing = state.now().plan.flatMap(_.tasks).find(_.taskId == key.taskId).flatMap(_.todayEntry)
    existing.fold(newLogEntryOf(key, draft))(entry =>
      entry.copy(
        latestVersion = UUID.randomUUID(),
        textExternal = draft.textExternal,
        status = draft.status,
        updatedAt = tgClock.currentInstant
      )
    )
  }

  private def newLogEntryOf(key: TeamDailyEntryKey, draft: TeamDailyEntryDraft): LogEntryDto = {
    val today = tgClock.currentDate()
    val createdAt =
      if (key.date == today) tgClock.currentInstant else tgClock.instantFrom(key.date, LocalTime.NOON, ZoneOffset.UTC)
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
            val dto = logEntryOf(key, draft)
            val isUpdate = state.now().plan.flatMap(_.tasks).exists(t => t.taskId == key.taskId && t.todayEntry.nonEmpty)
            (if (isUpdate) taskService.updateLogEntry(dto).map(_.isRight) else taskService.saveLogEntry(dto).map(_.isRight))
              .map(ok => key -> ok)
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

  private def levelOne: Signal[HtmlElement] =
    state.signal.map(_.position).distinct.map { position =>
      val current = position match {
        case TeamDailyPosition.ChooseMembers => 0
        case TeamDailyPosition.ChooseTasks   => 1
        case _: TeamDailyPosition.Fill       => 2
        case TeamDailyPosition.Review        => 3
      }
      breadcrumb(ProcessSteps.map(TgStep(_)), current, {
        case 0                 => jumpTo(_ == TeamDailyPosition.ChooseMembers)
        // from choosing members this is the Next button, which loads the tasks of the members chosen now
        case 1 if state.now().position == TeamDailyPosition.ChooseMembers => if (state.now().selected.nonEmpty) next()
        case 1 if isPlanned    => jumpTo(_ == TeamDailyPosition.ChooseTasks)
        case 2 if isPlanned    => jumpTo(_.isInstanceOf[TeamDailyPosition.Fill])
        case 3 if isPlanned    => jumpTo(_ == TeamDailyPosition.Review)
        case _                 => ()
      })
    }

  private def levelTwo: Signal[Option[HtmlElement]] =
    state.signal.map(s => (s.plan, s.plan.map(s.isFilled), s.position)).distinct.map {
      case (plan, filled, TeamDailyPosition.Fill(memberId, _, _)) =>
        val current = plan.indexWhere(_.member.id == memberId)
        Some(
          breadcrumb(
            plan.zip(filled).map { case (member, done) => TgStep(member.member.fullName, done) },
            current,
            index => jumpTo { case TeamDailyPosition.Fill(m, _, _) => m == plan(index).member.id; case _ => false }
          )
        )
      case _ => None
    }

  private def levelThree: Signal[Option[HtmlElement]] =
    state.signal.map(s => (s.plan, s.plan.flatMap(m => m.tasks.map(s.isFilled(m.member.id, _))), s.position)).distinct.map {
      case (plan, _, TeamDailyPosition.Fill(memberId, taskId, _)) =>
        plan.find(_.member.id == memberId).map { member =>
          val filled = state.now()
          breadcrumb(
            member.tasks.map(task => TgStep(task.name, filled.isFilled(member.member.id, task))),
            member.tasks.indexWhere(_.taskId == taskId),
            index => jumpTo { case TeamDailyPosition.Fill(_, t, _) => t == member.tasks(index).taskId; case _ => false }
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

  // ---- step 1: members

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
      label(cls(labelCls), "Who is this team daily for?"),
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
        cls("rounded-base border border-default-medium p-3"),
        children <-- state.signal.map(_.members).distinct.map(_.map(memberRow))
      )
    )

  // ---- step 2: choose tasks

  private def setTasksSelected(taskIds: Set[UUID], on: Boolean): Unit =
    state.update { current =>
      val all = TeamDailyPlan.taskIds(current.candidates)
      val chosen = current.selectedTasks.getOrElse(all)
      val updated = if (on) chosen ++ taskIds else chosen -- taskIds
      current.copy(selectedTasks = if (updated == all) None else Some(updated))
    }

  private def setTaskSelected(taskId: UUID, on: Boolean): Unit = setTasksSelected(Set(taskId), on)

  /** Like the read-only row of a bulk task card, with a border on each badge. */
  private def badgeRow(specs: Seq[BadgeSpec]): HtmlElement =
    div(
      cls("flex flex-wrap items-center gap-1.5"),
      specs.map { spec =>
        val neutral = spec.color == TgBadgeColor.Gray || spec.color == TgBadgeColor.Alternative
        TgBadge(text = spec.text, color = spec.color, bordered = true, extraCls = if (neutral) "dark:text-body" else "").element
      }
    )

  private def taskRow(task: TeamDailyTaskPlan): HtmlElement =
    label(
      cls("flex items-start gap-2 py-1 text-sm text-body cursor-pointer"),
      input(
        `type`("checkbox"),
        cls(checkboxCls),
        cls("mt-0.5 shrink-0"),
        checked <-- state.signal.map(_.isTaskSelected(task.taskId)).distinct,
        onChange.mapToChecked --> (on => setTaskSelected(task.taskId, on))
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
          span(cls("text-body-subtle"), if (task.todayEntry.isEmpty) "Not logged today" else "Logged today")
        ),
        badgeRow(TeamDailyTaskBadges.milestoneAndPriority(task, state.now().milestones))
      )
    )

  private def statusGroup(group: TeamDailyStatusGroup): HtmlElement =
    div(
      cls("mb-2 ms-3"),
      h5(cls("mb-1 text-sm font-medium text-body"), s"${group.statusLabel} (${group.tasks.size})"),
      group.tasks.map(taskRow)
    )

  private def memberGroup(member: TeamDailyMemberPlan): HtmlElement =
    div(
      cls("mb-4"),
      div(
        cls("mb-1 flex flex-wrap items-center gap-2"),
        h4(cls("text-base font-medium text-heading"), member.member.fullName),
        button(
          `type`("button"),
          cls(smallButtonCls),
          "Select all",
          onClick.mapToUnit --> (_ => setTasksSelected(member.tasks.map(_.taskId).toSet, on = true))
        ),
        button(
          `type`("button"),
          cls(smallButtonCls),
          "Unselect all",
          onClick.mapToUnit --> (_ => setTasksSelected(member.tasks.map(_.taskId).toSet, on = false))
        )
      ),
      TeamDailyPlan.byStatus(member.tasks).map(statusGroup)
    )

  private val chooseTasks: HtmlElement =
    div(
      label(cls(labelCls), "Which tasks do you want to add a status for?"),
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
        child.maybe <-- state.signal.map(_.candidates).distinct.map(candidates =>
          Option.when(candidates.isEmpty)(
            p(cls("text-sm text-body"), "The chosen members have no open tasks.")
          )
        ),
        children <-- state.signal.map(_.candidates).distinct.map(_.map(memberGroup))
      )
    )

  // ---- step 3: fill in

  private def fillForm(memberId: UUID, taskId: UUID, date: LocalDate): HtmlElement = {
    val key = TeamDailyEntryKey(memberId, taskId, date)
    val current = state.now().entries.getOrElse(key, defaultEntry(key))
    val task = state.now().plan.flatMap(_.tasks).find(_.taskId == taskId)
    val latest = task.flatMap(_.latestEntry)
    // the repeat button gets the focus when it is the quick way to fill in a day nobody has touched yet
    val canRepeat = latest.exists(entry => isRepeatable(task, entry))
    val focusRepeat = canRepeat && !state.now().entries.contains(key) && task.forall(_.todayEntry.isEmpty)
    // one box per task, so the entry to fill in is clearly the one of the task above it
    div(
      cls("rounded-base border border-default-medium p-4"),
      task.map(taskHeader(_, headingCls = "text-base font-medium text-heading")),
      div(
        cls("flex flex-col gap-4 lg:flex-row"),
        div(
          cls("min-w-0 flex-1"),
          new TeamDailyEntryForm(
            taskId,
            date,
            current,
            tgDateFormater,
            draft => edit(key)(_ => draft),
            focusEditor = !focusRepeat,
            authorName = currentAccountName,
            title = s"Today's status for ${task.fold("the task")(_.name)}"
          ).element
        ),
        latest.map(entry => div(cls("lg:w-[34rem] lg:shrink-0"), lastStatus(key, task, entry, canRepeat, focusRepeat)))
      )
    )
  }

  /** The task as the fill form and the review show it: name and labels, then milestone, priority and status, then dates. */
  private def taskHeader(task: TeamDailyTaskPlan, headingCls: String): HtmlElement =
    div(
      cls("mb-3 flex flex-col gap-1.5"),
      div(
        cls("flex flex-wrap items-center gap-2"),
        h4(cls(headingCls), task.name),
        if (task.labels.isEmpty) span(cls("text-sm"), aria.label("No labels"), "🏷️")
        else badgeRow(TeamDailyTaskBadges.labels(task)),
        TaskProgressComponent
          .fromDescription(Val(task.description), ringSizeCls = "w-3 h-3", textSizeCls = "text-xs")
          .element
      ),
      badgeRow(
        TeamDailyTaskBadges.milestoneAndPriority(task, state.now().milestones) :+
          BadgeSpec(task.statusLabel, TaskStatusBadgeColor.of(task.statusName))
      ),
      badgeRow(
        TeamDailyTaskBadges.dates(task, tgClock.currentDate(), projectWorkDays, tgDateFormater.asAmericanDateWithDayAbrv)
      )
    )

  /** The work days of the chosen project, the calendar default until the projects are loaded. */
  private def projectWorkDays: Set[DayOfWeek] = {
    val current = state.now()
    current.projects
      .find(project => current.projectId.contains(project.id))
      .map(_.workDays.map(DayOfWeek.of))
      .filter(_.nonEmpty)
      .getOrElse(WorkDayCalendar.DefaultWorkDays)
  }

  /** The last entry is worth repeating when it says blocked or paused, or when the task itself is waiting or blocked. */
  private def isRepeatable(task: Option[TeamDailyTaskPlan], entry: LogEntryDto): Boolean =
    TeamDailyComponent.RepeatableStatuses.contains(entry.status) ||
      task.exists(t => TeamDailyStatusOrder.isOnHold(t.statusName))

  /** Fills today with the same status and text, and shows the form again so the editor takes them over. */
  private def repeat(key: TeamDailyEntryKey, entry: LogEntryDto): Unit = {
    edit(key)(_ => TeamDailyEntryDraft(entry.status, entry.textExternal))
    formRefresh.update(_ + 1)
  }

  /** What was logged last (yesterday, or the last work day), above the form for today. Clicking a day of the tiny
    * calendar beside it shows what was logged that day instead, and "Last status" goes back.
    */
  private def lastStatus(
    key: TeamDailyEntryKey,
    task: Option[TeamDailyTaskPlan],
    entry: LogEntryDto,
    canRepeat: Boolean,
    focusRepeat: Boolean
  ): HtmlElement = {
    val selectedDay = Var(Option.empty[LocalDate])
    val dayEntries = (day: LocalDate) =>
      task.fold(Seq.empty[LogEntryDto])(_.logEntries.filter(e => GanttLogStatus.toLocalDate(e.createdAt) == day).sortBy(_.createdAt))
    div(
      cls("flex flex-col items-start gap-6 rounded-base border border-default-medium bg-neutral-secondary-soft p-3"),
      div(
        cls("min-w-0 flex-1"),
        div(
          cls("mb-2 flex flex-wrap items-center gap-1.5"),
          statusTab("Last status", selectedDay.signal.map(_.isEmpty), () => selectedDay.set(None)),
          child.maybe <-- selectedDay.signal.map(_.map(day =>
            statusTab(tgDateFormater.asAmericanDateWithDayAbrv(day), Val(true), () => ())
          )),
          // only the last status can be repeated, so the button goes with its tab
          child.maybe <-- selectedDay.signal.map(selected =>
            Option.when(canRepeat && selected.isEmpty)(repeatButton(key, entry, focusRepeat))
          )
        ),
        child <-- selectedDay.signal.map {
          case None      => entryView(entry)
          case Some(day) => dayView(day, dayEntries(day))
        }
      ),
      // the recent days of the task at a glance, small enough to sit beside the text
      task.map(t =>
        new LogEntryCalendarComponent(
          t.logEntries,
          tgDateFormater,
          projectWorkDays,
          selectedDay,
          startDate = Some(t.startDate),
          dueDate = Some(t.dueDate)
        ).tinyElement
          .amend(cls("order-first shrink-0 self-center"))
      )
    )
  }

  private def statusTab(text: String, active: Signal[Boolean], onSelect: () => Unit): HtmlElement =
    button(
      `type`("button"),
      cls(smallButtonCls),
      cls("border-brand font-semibold text-fg-brand") <-- active,
      text,
      onClick.mapToUnit --> (_ => onSelect())
    )

  private def repeatButton(key: TeamDailyEntryKey, entry: LogEntryDto, focusRepeat: Boolean): HtmlElement =
    button(
      `type`("button"),
      cls(smallButtonCls),
      // a clear ring, since this button often has the focus when the day opens
      cls("focus:outline-none focus:ring-2 focus:ring-brand focus:border-brand focus:text-fg-brand"),
      "Repeat for today",
      onMountCallback(context => if (focusRepeat) context.thisNode.ref.focus()),
      onClick.mapToUnit --> (_ => repeat(key, entry))
    )

  /** One log entry: its date, its status, and the text. */
  private def entryView(entry: LogEntryDto): HtmlElement =
    div(
      cls("mb-2 rounded-base border-s-4 border-brand p-2 ps-3 shadow-sm dark:shadow-white/10"),
      div(
        cls("flex items-center gap-2 mb-2"),
        TgBadge(
          text = tgDateFormater.asAmericanDateWithDayAbrv(GanttLogStatus.toLocalDate(entry.createdAt)),
          bordered = true,
          color = TgBadgeColor.Gray
        ).element,
        TgBadge(text = entry.status.toStringWithEmoji, color = entry.status.badgeColor, bordered = true).element,
        LogEntryMemberBadge(entry.createdByName)
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

  // ---- step 4: review

  private def reviewView(s: TeamDailyState): HtmlElement = {
    val toSave = s.toSave.toMap
    if (s.plan.isEmpty) div(cls("text-sm text-body"), "Nothing to fill in: the chosen members have no open tasks.")
    else
      div(
        s.plan.map { member =>
          div(
            cls("mb-4"),
            h4(cls("text-base font-medium text-heading"), member.member.fullName),
            member.tasks.map { task =>
              div(
                cls("mt-2 rounded-base border border-default-medium p-3"),
                taskHeader(task, headingCls = "text-sm font-medium text-heading"),
                task.missingDays.map { date =>
                  val key = TeamDailyEntryKey(member.member.id, task.taskId, date)
                  div(
                    cls("mb-2 cursor-pointer rounded-base border-s-4 border-brand p-2 ps-3 shadow-sm dark:shadow-white/10 hover:bg-neutral-secondary-medium"),
                    div(
                      cls("flex items-center gap-2 mb-2"),
                      TgBadge(text = tgDateFormater.asAmericanDateWithDayAbrv(date), bordered = true, color = TgBadgeColor.Gray).element,
                      toSave.get(key).map(entry =>
                        TgBadge(text = entry.status.toStringWithEmoji, color = entry.status.badgeColor, bordered = true).element
                      ),
                      LogEntryMemberBadge(currentAccountName)
                    ),
                    toSave.get(key).fold[HtmlElement](p(cls("text-sm text-body-subtle"), "Not filled in, will not be saved"))(entry =>
                      tgMarkdown.toLaminarDiv(entry.textExternal)
                    ),
                    onClick.mapToUnit --> (_ => jumpTo(_ == TeamDailyPosition.Fill(member.member.id, task.taskId, date)))
                  )
                }
              )
            }
          )
        },
        p(cls("text-sm text-body"), s"${toSave.size} log entries will be saved.")
      )
  }

  /** Counts the repeats, each one builds the fill form again from the entry it has just been given. */
  private val formRefresh = Var(0)

  private val body: Signal[HtmlElement] =
    state.signal.map(_.position).distinct.flatMapSwitch {
      case TeamDailyPosition.ChooseMembers   => Val(chooseMembers)
      case TeamDailyPosition.ChooseTasks     => Val(chooseTasks)
      case TeamDailyPosition.Fill(m, t, d)   => formRefresh.signal.map(_ => fillForm(m, t, d))
      case TeamDailyPosition.Review          => state.signal.map(reviewView)
    }

  // ---- footer

  private val onReview = state.signal.map(_.position == TeamDailyPosition.Review)
  private val canGoBack = state.signal.map(s => s.positions.indexOf(s.position) > 0)
  private val canGoNext = state.signal.map { s =>
    s.position match {
      case TeamDailyPosition.ChooseMembers => s.selected.nonEmpty
      case TeamDailyPosition.ChooseTasks   => s.plan.nonEmpty
      case _                               => true
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
        onClick.mapToUnit --> (_ => if (state.now().position == TeamDailyPosition.Review) submit() else next())
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
      span(cls("text-sm text-body"), "Your team daily is saved as a draft."),
      button(
        `type`("button"),
        cls(secondaryButtonCls),
        "Keep editing",
        onClick.mapToUnit --> (_ => confirmingClose.set(false))
      ),
      button(
        `type`("button"),
        cls(secondaryButtonCls),
        "Discard",
        onClick.mapToUnit --> (_ => closeDiscardingDraft())
      ),
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
      cls("fixed inset-0 z-[100] flex items-start justify-center overflow-y-auto pt-16 pb-8 px-4 bg-white/90 dark:bg-neutral-primary/90"),
      onClick.filter(event => event.target == event.currentTarget).mapToUnit --> (_ => requestClose()),
      onMountCallback { _ =>
        loadProjects()
        state.now().projectId.foreach(loadProject)
      },
      state.signal.map(_.projectId).distinct.changes.collect { case Some(id) => id } --> (id => loadProject(id)),
      state.signal.map(_.draft).distinct.changes.debounce(DraftSaveDebounce.toMillis.toInt) --> (_ => saveDraft()),
      windowEvents(_.onBeforeUnload) --> (_ => saveDraft()),
      documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit -->
        (_ => if (confirmingClose.now()) confirmingClose.set(false) else requestClose()),
      div(
        cls(ModalStyles.contentBoxCls),
        cls("flex w-full max-w-[96rem] flex-col min-h-[75vh] max-h-[calc(100vh-6rem)]"),
        div(
          cls(ModalStyles.headerCls),
          h3(cls(ModalStyles.titleCls), "Team daily"),
          button(
            `type`("button"),
            cls(ModalStyles.closeButtonCls),
            aria.label("Close"),
            TgIcons.closeIcon("w-4 h-4"),
            onClick.mapToUnit --> (_ => requestClose())
          )
        ),
        stepper,
        // the steps scroll in the space between the stepper and the footer, which stays at the bottom
        div(cls("min-h-0 flex-1 overflow-y-auto"), child <-- body),
        footer
      )
    )
}

object TeamDailyComponent {

  /** Statuses that likely still hold the next day, for the same reason. */
  val RepeatableStatuses: Set[LogEntryStatus] = Set(LogEntryStatus.Blocked, LogEntryStatus.Paused)

  /** How many steps a click on an arrow of a stepper row scrolls by. */
  val DefaultStepsPerArrowClick = 2
}
