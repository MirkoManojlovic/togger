package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.common.TgClock
import com.togger.common.WorkDayCalendar
import com.togger.dto.FindMilestoneStepResp
import com.togger.dto.FindTaskRespFull
import com.togger.milestone.DefaultMilestoneStep
import com.togger.routes.TaskListParams
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.components.TgHtmlTable
import com.togger.ui.components.TgHtmlTable.createTd
import com.togger.ui.components.TgTab
import com.togger.ui.components.TgTabs
import com.togger.ui.modal.ModalStyles
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task._
import com.togger.ui.task.bulk._
import com.togger.ui.timebox._
import com.togger.ui.timebox.gantt.TimeboxGanttComponent
import com.togger.ui.toast.Toast
import org.scalajs.dom
import org.scalajs.dom.HTMLTableCellElement

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import scala.util.Try

/** Plans the tasks of a milestone into its steps, the way a timebox is planned: the tasks of the project to pick from,
  * the tasks staged in each step with the capacity of the step, and a Gantt chart per step to position them. Nothing is
  * saved until Save: a staged task is then put in its step (with the dates it has on the chart) and a task taken out of
  * every step falls back to the Shelf step of the milestone. The Shelf step itself is not planned.
  *
  * The staged tasks of a step are kept in the staging store under the id of the step.
  */
class MilestoneStepTasksComponent(
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  milestoneService: MilestoneFeService,
  milestoneStepService: MilestoneStepFeService,
  projectService: ProjectService,
  tgDateFormater: TgDateFormater,
  stagingStore: TimeboxStagingStore,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  isDark: Signal[Boolean],
  projectId: UUID,
  milestoneId: UUID,
  milestoneSteps: Signal[Seq[FindMilestoneStepResp]],
  onSaved: IO[Unit],
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  /** A step as the planning needs it. The Shelf step is left out. */
  private case class PlanStep(id: UUID, name: String, startDate: LocalDate, dueDate: LocalDate) {
    def range: (LocalDate, LocalDate) = (startDate, dueDate)
  }

  private val milestoneStepsVar = Var(Seq.empty[FindMilestoneStepResp])

  private val planSteps: Signal[Seq[PlanStep]] =
    milestoneSteps
      .map(
        _.filterNot(step => DefaultMilestoneStep.isDefaultName(step.name))
          .map(step => PlanStep(step.id, step.name, step.startDate, step.dueDate))
      )
      .distinct

  private val planStepsVar = Var(Seq.empty[PlanStep])

  private val lookups =
    new TaskListLookups(projectId, projectService, taskStatusFeService, taskLabelFeService, milestoneService)

  // The list starts with the tasks of this milestone; the milestone filter can be widened to the other tasks.
  private val paramsVar = Var(TaskListParams.default.copy(milestones = Some(Set(milestoneId.toString))))

  private def showList(newParams: TaskListParams): Unit = {
    paramsVar.set(newParams)
    spinnerService.runWithSpinner(browser.loadPage()).unsafeRunAndForget()
  }

  private val browser = new TaskListBrowser(taskService, lookups, projectId, paramsVar, showList)

  /** The tasks of the project as the backend has them: where each is and its dates when last saved. */
  private val projectTasksVar = Var(Seq.empty[FindTaskRespFull])

  /** The project tasks as last loaded from the backend; staging starts again from them, which an edit of one task must
    * not do, as it would drop the unsaved plan.
    */
  private val loadedTasksVar = Var(Seq.empty[FindTaskRespFull])

  /** Every step of the project, to name the step a task is in. */
  private val allStepsVar = Var(Seq.empty[FindMilestoneStepResp])

  private val selectedStepVar = Var("")

  private val savingVar = Var(Set.empty[UUID])

  private val workDays: Signal[Set[DayOfWeek]] = lookups.projectVar.signal.map(_.workDays.map(DayOfWeek.of)).distinct

  private def loadProjectTasks(): IO[Unit] =
    taskService
      .findAllByProjectId(projectId)
      .map(_.foreach { success =>
        projectTasksVar.set(success.body.get)
        loadedTasksVar.set(success.body.get)
      })

  private def load(): IO[Unit] =
    for {
      _ <- lookups.load()
      _ <- browser.loadPage()
      _ <- loadProjectTasks()
      _ <- milestoneStepService.findByProjectId(projectId).map(_.foreach(success => allStepsVar.set(success.body.get)))
    } yield ()

  spinnerService.runWithSpinner(load()).unsafeRunAndForget()

  /** A step stages the tasks that are in it. Staged tasks left over from an earlier visit give way to them. */
  private def stageSavedTasks(steps: Seq[PlanStep], tasks: Seq[FindTaskRespFull]): Unit =
    steps.foreach(step => stagingStore.set(step.id, tasks.filter(_.milestoneStepId.contains(step.id))))

  private val stageSavedTasksOnLoad: Modifier[HtmlElement] =
    planSteps.combineWith(loadedTasksVar.signal) --> Observer[(Seq[PlanStep], Seq[FindTaskRespFull])] {
      case (steps, tasks) => stageSavedTasks(steps, tasks)
    }

  private val rememberSteps: Seq[Modifier[HtmlElement]] = Seq(
    planSteps --> planStepsVar,
    milestoneSteps --> milestoneStepsVar
  )

  // ---- the badges of a task, as in the timebox form ----

  private def durationLabel(dto: FindTaskRespFull): String = {
    val days = WorkDayCalendar.countWorkDays(dto.startDate, dto.dueDate, workDaysNow())
    if (days == 1) "1 day" else s"$days days"
  }

  private def workDaysNow(): Set[DayOfWeek] = lookups.projectVar.now().workDays.map(DayOfWeek.of)

  private def labelRow(dto: FindTaskRespFull): Seq[BadgeSpec] =
    if (dto.taskLabels.isEmpty) Seq(BadgeSpec(BulkTaskBadges.EmptyLabels, TgBadgeColor.Gray, Some(BulkEditKind.Labels)))
    else
      dto.taskLabels
        .sortBy(_.name)
        .map(label => BadgeSpec(label.badgeText, label.badgeColor, Some(BulkEditKind.Labels)))

  /** The milestone of the step the task is saved in; the milestone of the task itself when its step is not known. */
  private def milestoneBadge(dto: FindTaskRespFull): BadgeSpec =
    dto.milestoneStepId
      .flatMap(id => MilestoneStepLabels.milestoneOf(id, allStepsVar.now(), lookups.milestonesVar.now()))
      .orElse(dto.milestone.flatMap(id => lookups.milestonesVar.now().find(_.id == id)))
      .map(milestone => BadgeSpec(milestone.name, TgBadgeColor.Alternative, Some(BulkEditKind.Milestone)))
      .getOrElse(BadgeSpec(BulkTaskBadges.EmptyMilestone, TgBadgeColor.Gray, Some(BulkEditKind.Milestone)))

  private def stepBadge(dto: FindTaskRespFull): BadgeSpec =
    MilestoneStepBadges
      .of(dto.milestoneStepId, allStepsVar.now(), editable = true)
      .getOrElse(BadgeSpec(BulkTaskBadges.EmptyMilestoneStep, TgBadgeColor.Gray, Some(BulkEditKind.MilestoneStep)))

  private def primaryRow(dto: FindTaskRespFull): Seq[BadgeSpec] =
    Seq(
      milestoneBadge(dto),
      stepBadge(dto),
      BadgeSpec(dto.priority.shortCode, dto.priority.badgeColor, Some(BulkEditKind.Priority)),
      BadgeSpec(dto.taskStatus.badgeText, dto.taskStatus.badgeColor, Some(BulkEditKind.Status)),
      dto.assignedTo
        .map(member => BadgeSpec(member.fullName, TgBadgeColor.Gray, Some(BulkEditKind.Assignee)))
        .getOrElse(BadgeSpec(BulkTaskBadges.EmptyAssignee, TgBadgeColor.Gray, Some(BulkEditKind.Assignee)))
    )

  private def dateRow(dto: FindTaskRespFull): Seq[BadgeSpec] =
    Seq(
      BadgeSpec(
        tgDateFormater.asAmericanDateWithDayAbrv(dto.startDate),
        TgBadgeColor.Gray,
        Some(BulkEditKind.StartDate)
      ),
      BadgeSpec(tgDateFormater.asAmericanDateWithDayAbrv(dto.dueDate), TgBadgeColor.Gray, Some(BulkEditKind.DueDate)),
      BadgeSpec(durationLabel(dto), TgBadgeColor.Gray)
    )

  private def badgeRow(taskId: UUID, specs: Seq[BadgeSpec], origin: String = "") =
    div(cls("flex flex-wrap items-center gap-1.5"), BulkTaskCard.row(taskId, specs, popups, origin))

  /** The badges of a task shown apart from the table, which open their popups apart from the ones of the same task in
    * the table, and from those of the other places the task is shown in.
    */
  private def badgesAt(origin: String)(dto: FindTaskRespFull): StagedTaskBadges = {
    def row(specs: Seq[BadgeSpec]) =
      badgeRow(dto.id, specs, origin)
        .amend(cls("opacity-60 pointer-events-none") <-- savingVar.signal.map(_.contains(dto.id)))
    StagedTaskBadges(row(labelRow(dto)), row(primaryRow(dto)), row(dateRow(dto)))
  }

  private def lookupsNow(): BulkLookups =
    BulkLookups(
      statuses = lookups.statusesVar.now().taskStatuses,
      labels = lookups.taskLabelsVar.now().taskLabels,
      members = lookups.projectVar.now().members,
      milestones = lookups.milestonesVar.now(),
      workDays = workDaysNow(),
      steps = allStepsVar.now()
    )

  private val lookupsSignal: Signal[BulkLookups] =
    lookups.statusesVar.signal
      .combineWith(
        lookups.taskLabelsVar.signal,
        lookups.projectVar.signal,
        lookups.milestonesVar.signal,
        allStepsVar.signal
      )
      .map { case (statuses, labels, project, milestones, steps) =>
        BulkLookups(
          statuses = statuses.taskStatuses,
          labels = labels.taskLabels,
          members = project.members,
          milestones = milestones,
          workDays = project.workDays.map(DayOfWeek.of),
          steps = steps
        )
      }

  private val popups: BulkEditPopups = new BulkEditPopups(
    context = scope =>
      BulkEditContext(
        lookupsNow(),
        editableTasks(scope).map(ShowAllTasksBulkMapping.toBulkTask),
        tgClock.currentDate()
      ),
    onEdit = saveEdit
  )

  private def stagedTasksNow(): Seq[FindTaskRespFull] =
    planStepsVar.now().flatMap(step => stagingStore.stagedWithDatesNow(step.id))

  /** The task a badge edits: as it is staged, with the dates it has on the Gantt chart, or else as the page shows it.
    */
  private def editableTasks(scope: Option[UUID]): Seq[FindTaskRespFull] =
    scope.toSeq.flatMap { id =>
      stagedTasksNow().find(_.id == id).orElse(browser.rowsVar.now().find(_.id == id))
    }

  /** Saves the edit for the task at once, as the timebox form does, and reads the page and the staged copies again. */
  private def saveEdit(scope: Option[UUID], edit: BulkEdit): Unit = {
    val current = lookupsNow()
    val changed = editableTasks(scope)
      .filter(dto => !savingVar.now().contains(dto.id))
      .flatMap { dto =>
        val before = ShowAllTasksBulkMapping.toBulkTask(dto)
        val after = BulkTaskEditor(Seq(before), Set(dto.id), edit, current.workDays).head
        Option.when(after != before)(ShowAllTasksBulkMapping.applyTo(dto, after, current))
      }
    changed.foreach { updated =>
      savingVar.update(_ + updated.id)
      val save =
        taskService.updateTask(ShowAllTasksBulkMapping.toUpdateRequest(updated, tgClock.currentInstant)).flatMap {
          case Left(error) => IO(toast.error(error))
          case Right(_)    => IO.unit
        }
      spinnerService
        .runWithSpinner(save >> browser.loadPage() >> refreshStaged(updated.id))
        .guarantee(IO(savingVar.update(_ - updated.id)))
        .unsafeRunAndForget()
    }
  }

  /** Swaps the staged tasks for their saved state, and, when the edit moved the edited task to another step, puts it in
    * the step it is now saved in: in none when it left the milestone. The saved state the changes are counted from
    * follows, without the unsaved plan being dropped.
    */
  private def refreshStaged(editedId: UUID): IO[Unit] =
    taskService
      .findAllByProjectId(projectId)
      .flatMap(projectTasks =>
        IO(projectTasks.foreach { success =>
          val current = success.body.get
          val stepBefore = projectTasksVar.now().find(_.id == editedId).flatMap(_.milestoneStepId)
          projectTasksVar.set(current)
          val steps = planStepsVar.now()
          steps.foreach(step => stagingStore.refresh(step.id, current))
          current.find(_.id == editedId).filter(_.milestoneStepId != stepBefore).foreach { edited =>
            steps.foreach(step => stagingStore.unstage(step.id, editedId))
            steps.find(step => edited.milestoneStepId.contains(step.id)).foreach { step =>
              stagingStore.set(step.id, stagingStore.now(step.id) :+ edited)
            }
          }
        })
      )

  // ---- planned tasks ----

  private def isInteractive(target: dom.EventTarget): Boolean =
    target match {
      case element: dom.Element => element.closest("button, input, a") != null
      case _                    => false
    }

  private val stagedAnywhere: Signal[Set[UUID]] =
    planSteps
      .flatMapSwitch(steps => Signal.combineSeq(steps.map(step => stagingStore.stagedIds(step.id))))
      .map(_.flatten.toSet)

  /** Stages the task in the step of the open tab, or unstages it from there. A task is in one step only, so staging it
    * takes it out of the others.
    */
  private def toggleInSelectedStep(task: FindTaskRespFull): Unit =
    Try(UUID.fromString(selectedStepVar.now())).toOption.filter(id => planStepsVar.now().exists(_.id == id)).foreach {
      stepId =>
        if (!stagingStore.now(stepId).exists(_.id == task.id))
          planStepsVar.now().filter(_.id != stepId).foreach(step => stagingStore.unstage(step.id, task.id))
        stagingStore.toggle(stepId, task)
    }

  private def taskCell(dto: FindTaskRespFull) =
    createTd(
      div(
        cls("flex flex-col gap-1"),
        cls("opacity-60 pointer-events-none") <-- savingVar.signal.map(_.contains(dto.id)),
        div(
          cls("flex flex-wrap items-center gap-x-2 gap-y-1"),
          span(cls("text-base font-semibold text-heading"), dto.name),
          badgeRow(dto.id, labelRow(dto)),
          TaskProgressComponent
            .fromDescription(Val(dto.description), ringSizeCls = "w-3 h-3", textSizeCls = "text-xs")
            .element
        ),
        badgeRow(dto.id, primaryRow(dto)),
        badgeRow(dto.id, dateRow(dto))
      )
    ).amend(
      cls("cursor-pointer"),
      cls("bg-neutral-tertiary-medium") <-- stagedAnywhere.map(_.contains(dto.id)),
      onClick.filter(event => !isInteractive(event.target)).mapToUnit --> (_ => toggleInSelectedStep(dto))
    )

  private val noTasksRow: Seq[ReactiveHtmlElement[HTMLTableCellElement]] =
    Seq(td(cls("px-6 py-16 text-center text-lg text-body"), "No tasks found"))

  private val taskTable = TgHtmlTable.tableElement(
    Seq[Modifier[ReactiveHtmlElement[HTMLTableCellElement]]](
      div(cls("flex items-center justify-between gap-4"), "Name", browser.filterButtons)
    ),
    striped = false
  )(
    browser.visibleRowsSignal.map(rows => if (rows.isEmpty) Seq(noTasksRow) else rows.map(dto => Seq(taskCell(dto))))
  )

  // ---- the steps ----

  private def capacityOf(step: PlanStep): Signal[Option[TimeboxCapacity]] =
    stagingStore
      .stagedWithDates(step.id)
      .combineWith(lookups.projectVar.signal)
      .map { case (tasks, project) =>
        Some(TimeboxCapacity.of(tasks, project.members, project.workDays.map(DayOfWeek.of), step.range))
      }
      .distinct

  private def ganttOf(step: PlanStep): TimeboxGanttComponent =
    new TimeboxGanttComponent(
      stagingStore,
      step.id,
      workDays,
      Val(Some(step.range)),
      tgDateFormater,
      tgClock,
      lookupsSignal,
      badgesAt(s"gantt-${step.id}")
    )

  private val StepGroupings: Seq[StagedGrouping] = StagedGrouping.all.filterNot(_ == StagedGrouping.ByMilestone)

  private def stagedTasksOf(step: PlanStep): HtmlElement =
    new StagedTasksComponent(
      stagingStore,
      step.id,
      workDays,
      Val(Some(step.range)),
      capacityOf(step),
      Val(Seq.empty),
      isDark,
      badgesAt(s"staged-${step.id}"),
      StepGroupings,
      s"Staged tasks of ${step.name}"
    ).element.amend(cls("hidden") <-- selectedStepVar.signal.map(_ != step.id.toString))

  private val overCapacity: Signal[Boolean] =
    planSteps
      .flatMapSwitch(steps => Signal.combineSeq(steps.map(capacityOf)))
      .map(_.exists(_.exists(_.isOverCapacity)))
      .distinct

  // ---- saving ----

  private def shelfStepId(): Option[UUID] =
    milestoneStepsVar.now().find(step => DefaultMilestoneStep.isDefaultName(step.name)).map(_.id)

  /** The tasks to save: those staged in a step they are not saved in or with other dates, in that step; and those that
    * are saved in a step but staged in none, in the Shelf step.
    */
  private def tasksToSave(): Seq[FindTaskRespFull] = {
    val steps = planStepsVar.now()
    val saved = projectTasksVar.now()
    val placed = steps.flatMap { step =>
      stagingStore.stagedWithDatesNow(step.id).flatMap { task =>
        val before = saved.find(_.id == task.id)
        val unchanged = before.exists(b =>
          b.milestoneStepId.contains(step.id) && b.startDate == task.startDate && b.dueDate == task.dueDate
        )
        Option.when(!unchanged)(task.copy(milestoneStepId = Some(step.id), milestone = Some(milestoneId)))
      }
    }
    val stagedIds = stagedTasksNow().map(_.id).toSet
    val stepIds = steps.map(_.id).toSet
    val released = saved
      .filter(task => task.milestoneStepId.exists(stepIds.contains) && !stagedIds.contains(task.id))
      .map(_.copy(milestoneStepId = shelfStepId(), milestone = Some(milestoneId)))
    placed ++ released
  }

  private val pendingChanges: Signal[Int] =
    planSteps
      .flatMapSwitch(steps => Signal.combineSeq(steps.map(step => stagingStore.stagedWithDates(step.id))))
      .combineWith(projectTasksVar.signal, milestoneSteps)
      .map(_ => tasksToSave().size)
      .distinct

  /** Saves the tasks one after the other and stops at the first that fails. */
  private def save(): Unit = {
    val tasks = tasksToSave()
    val saveAll = tasks.foldLeft(IO.pure(true)) { (previous, task) =>
      previous.flatMap {
        case false => IO.pure(false)
        case true =>
          taskService.updateTask(ShowAllTasksBulkMapping.toUpdateRequest(task, tgClock.currentInstant)).flatMap {
            case Left(error) => IO(toast.error(error)).as(false)
            case Right(_)    => IO.pure(true)
          }
      }
    }
    spinnerService
      .runWithSpinner(
        saveAll.flatMap(saved =>
          IO(if (saved) toast.success("Tasks saved in the steps")) >> loadProjectTasks() >> browser
            .loadPage() >> onSaved
        )
      )
      .unsafeRunAndForget()
  }

  private val saveBar: HtmlElement =
    div(
      cls("flex items-center justify-end gap-3"),
      child.maybe <-- overCapacity.map(
        Option.when(_)(
          span(
            cls("text-sm font-medium text-fg-danger-strong"),
            "A step is over capacity: unstage tasks to be able to save."
          )
        )
      ),
      button(
        typ("button"),
        cls(ModalStyles.primaryButtonCls),
        child.text <-- pendingChanges.map(count => if (count == 0) "Save tasks" else s"Save tasks ($count)"),
        disabled <-- pendingChanges.combineWith(overCapacity).map { case (count, over) => count == 0 || over },
        onClick.mapToUnit --> (_ => save())
      )
    )

  private val ganttTabs: Modifier[HtmlElement] =
    child <-- planSteps.map { steps =>
      if (steps.isEmpty) div(cls("text-sm text-body"), "Add a step to plan tasks in it.")
      else
        new TgTabs(
          steps.map(step =>
            TgTab(
              step.id.toString,
              step.name,
              ganttOf(step).element,
              badge = Some(stagingStore.staged(step.id).map(_.size))
            )
          ),
          selectedStepVar
        ).element
    }

  val element: HtmlElement =
    div(
      stageSavedTasksOnLoad,
      rememberSteps,
      browser.filterBindings,
      // Enter in a search box searches; it must not submit a form around the picker.
      onKeyDown.filter(_.key == "Enter").preventDefault --> Observer.empty,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Plan tasks in the steps", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              div(cls("mb-4"), ganttTabs),
              div(
                cls("grid grid-cols-1 items-start gap-4 lg:grid-cols-2"),
                TgCard
                  .headerNoFooter(
                    header = TgCard.CardHeader.plainText("Planned tasks", TgCard.CardHeaderSize.H5),
                    body = TgCard
                      .HtmlCardContent(div(browser.searchBar, taskTable, browser.pagination(alwaysShowSize = true)))
                  )
                  .element,
                div(children <-- planSteps.map(_.map(stagedTasksOf)))
              ),
              saveBar.amend(cls("mt-4"))
            )
          )
        )
        .element
    )
}
