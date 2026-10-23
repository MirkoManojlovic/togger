package com.togger.ui.timebox

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{CreateTimeboxReq, FindTaskRespFull, FindTimeboxResp, SaveTimeboxVersionReq}
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TaskListParams, TgRouter}
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.timebox.{TimeboxDefault, TimeboxDefaults}
import com.togger.toast.ToastMessages
import com.togger.ui.task.TaskProgressComponent
import com.togger.ui.auth.TgCard
import com.togger.ui.auth.TgInput.tgInputStyle
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgHtmlTable.createTd
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.components.{CalendarPickerPopup, TgBadgeColor, TgHtmlTable}
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.bulk._
import com.togger.ui.task._
import com.togger.ui.timebox.gantt.TimeboxGanttComponent
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom
import org.scalajs.dom.HTMLTableCellElement

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID
import scala.util.Try

sealed trait TimeboxFormTarget {
  def projectId: UUID
}

object TimeboxFormTarget {
  case class Create(projectId: UUID) extends TimeboxFormTarget
  case class NewVersion(projectId: UUID, timeboxId: UUID) extends TimeboxFormTarget
}

case class TimeboxForm(
  startDate: String = "",
  endDate: String = "",
  description: String = "",
  changeComment: String = ""
)

/** Creates a timebox, or saves a new version of an existing one (a full snapshot, versions are never edited). */
class TimeboxFormComponent(
  target: TimeboxFormTarget,
  timeboxService: TimeboxFeService,
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  milestoneService: MilestoneFeService,
  projectService: ProjectService,
  tgDateFormater: TgDateFormater,
  stagingStore: TimeboxStagingStore,
  draftStore: TimeboxFormDraftStore,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgClock: TgClock,
  isDark: Signal[Boolean],
  val toast: Toast
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  // Only a new timebox is remembered; a new version starts from the timebox's latest version instead.
  private val isCreate = target match {
    case TimeboxFormTarget.Create(_)        => true
    case TimeboxFormTarget.NewVersion(_, _) => false
  }

  private val formState = Var(
    if (isCreate) draftStore.load(target.projectId).getOrElse(TimeboxForm()) else TimeboxForm()
  )

  private val draftSaver = Observer[TimeboxForm](form => if (isCreate) draftStore.save(target.projectId, form))

  private val lookups = new TaskListLookups(
    target.projectId,
    projectService,
    taskStatusFeService,
    taskLabelFeService,
    milestoneService
  )

  // The list is paged, searched and filtered here, in the form, so its params are kept in a Var rather than in the URL.
  private val paramsVar = Var(TaskListParams.default)

  private def showList(newParams: TaskListParams): Unit = {
    paramsVar.set(newParams)
    spinnerService.runWithSpinner(browser.loadPage()).unsafeRunAndForget()
  }

  private val browser = new TaskListBrowser(taskService, lookups, target.projectId, paramsVar, showList)

  private val presets: List[TimeboxDefault] = TimeboxDefaults.defaults(tgClock.currentDate())

  private val projectId = target.projectId

  stagingStore.open(projectId)

  /** Fills the form from the timebox's latest version, and stages that version's tasks (a stage left over from an
    * earlier visit gives way to what the version has).
    */
  private def prefill(timebox: FindTimeboxResp, projectTasks: Seq[FindTaskRespFull]): Unit = {
    val version = timebox.latestVersion
    formState.set(
      TimeboxForm(
        startDate = version.startDate.toString,
        endDate = version.endDate.toString,
        description = version.description.getOrElse("")
      )
    )
    val versionTaskIds = version.tasks.map(_.taskId).toSet
    stagingStore.set(projectId, projectTasks.filter(task => versionTaskIds.contains(task.id)))
  }

  spinnerService
    .runWithSpinner {
      for {
        _ <- lookups.load()
        _ <- browser.loadPage()
        projectTasks <- taskService.findAllByProjectId(projectId)
        _ <- IO(projectTasks.foreach(success => stagingStore.refresh(projectId, success.body.get)))
        _ <- target match {
          case TimeboxFormTarget.Create(_) => IO.unit
          case TimeboxFormTarget.NewVersion(_, timeboxId) =>
            timeboxService
              .findById(timeboxId)
              .map(_.foreach(success => projectTasks.foreach(tasks => prefill(success.body.get, tasks.body.get))))
        }
      } yield ()
    }
    .unsafeRunAndForget()

  private def backToList(): Unit = router.route(ShowTimeboxesPage(target.projectId))

  private val submitter = Observer[TimeboxForm] { form =>
    val description = Option(form.description.trim).filter(_.nonEmpty)
    val start = LocalDate.parse(form.startDate)
    val end = LocalDate.parse(form.endDate)
    val taskIds = stagingStore.now(projectId).map(_.id)
    val call = target match {
      case TimeboxFormTarget.Create(projectId) =>
        timeboxService.createTimebox(CreateTimeboxReq(projectId, start, end, description, taskIds))
      case TimeboxFormTarget.NewVersion(_, timeboxId) =>
        timeboxService.saveNewVersion(
          SaveTimeboxVersionReq(timeboxId, start, end, description, form.changeComment.trim, taskIds)
        )
    }
    // The tasks come first so the timebox picks up their new dates; if one fails the timebox is not saved.
    val saveTimebox = call.toastMessageCodeBlock("Timebox saved successfully") {
      stagingStore.clear(projectId)
      draftStore.clear(projectId)
      backToList()
    }
    spinnerService
      .runWithSpinner(saveChangedTaskDates().flatMap(saved => if (saved) saveTimebox else IO.unit))
      .unsafeRunAndForget()
  }

  /** Saves the dates changed on the Gantt chart, one task after the other, and stops at the first that fails. */
  private def saveChangedTaskDates(): IO[Boolean] =
    stagingStore.withChangedDatesNow(projectId).foldLeft(IO.pure(true)) { (previous, task) =>
      previous.flatMap {
        case false => IO.pure(false)
        case true =>
          taskService.updateTask(ShowAllTasksBulkMapping.toUpdateRequest(task, tgClock.currentInstant)).flatMap {
            case Left(error) => IO(toast.error(error)).as(false)
            case Right(_)    => IO.pure(true)
          }
      }
    }

  private def parsedDate(raw: String): Option[LocalDate] = Try(LocalDate.parse(raw)).toOption

  /** A date shown as a button that opens the app's calendar picker, in place of the browser's own date input. */
  private def dateField(
    labelValue: String,
    selector: TimeboxForm => String,
    updater: (TimeboxForm, String) => TimeboxForm,
    minDate: => Option[LocalDate]
  ): HtmlElement = {
    val isOpen = Var(false)
    val today = tgClock.currentDate()
    div(
      cls("relative"),
      label(cls("block mb-2.5 text-sm font-medium text-heading"), labelValue),
      button(
        typ("button"),
        cls(tgInputStyle),
        cls("cursor-pointer text-left"),
        child.text <-- formState.signal
          .map(form => parsedDate(selector(form)).fold("Select a date")(tgDateFormater.asAmericanDateWithDayAbrv))
          .distinct,
        // stopPropagation keeps this click from reaching the picker's own outside-click listener, which starts
        // listening synchronously within this same click when the picker opens
        onClick.stopPropagation.mapToUnit --> (_ => isOpen.update(!_))
      ),
      child.maybe <-- isOpen.signal.map { open =>
        Option.when(open) {
          val current = parsedDate(selector(formState.now()))
          CalendarPickerPopup(
            initialMonth = current.getOrElse(today),
            today = today,
            tgDateFormater = tgDateFormater,
            selected = current,
            minDate = minDate,
            onPick = day => formState.update(updater(_, day.toString)),
            onClose = () => isOpen.set(false)
          ).element
        }
      }
    )
  }

  private val startDateInput = dateField("Start date", _.startDate, (form, value) => form.copy(startDate = value), None)

  private val endDateInput = dateField(
    "End date",
    _.endDate,
    (form, value) => form.copy(endDate = value),
    parsedDate(formState.now().startDate)
  )

  private val endBeforeStartError: Signal[Option[String]] = formState.signal.map { form =>
    val ends = for {
      start <- parsedDate(form.startDate)
      end <- parsedDate(form.endDate)
    } yield end.isBefore(start)
    Option.when(ends.contains(true))("End date must be on or after start date")
  }

  /** Dates are required, and the end can't come before the start. */
  private val datesInvalidSignal: Signal[Boolean] = formState.signal.map { form =>
    (for {
      start <- parsedDate(form.startDate)
      end <- parsedDate(form.endDate)
    } yield end.isBefore(start)).getOrElse(true)
  }.distinct

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Description (optional)",
    minWidthRem = 0
  )

  private val changeCommentEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.changeComment),
    labelValue = "Change comment",
    minWidthRem = 0
  )

  private val changeCommentMissingSignal: Signal[Boolean] =
    formState.signal.map(form => !isCreate && form.changeComment.trim.isEmpty).distinct

  private def clearAll(): Unit = {
    formState.set(TimeboxForm())
    stagingStore.clear(projectId)
    draftStore.clear(projectId)
  }

  private val clearAllButton = TgHtmlButton
    .extraSmall("Clear all")
    .amend(
      typ("button"),
      title("Clear the dates, description and staged tasks"),
      onClick.mapToUnit --> (_ => clearAll())
    )

  private val presetButtons = div(
    cls("mb-4 flex flex-wrap gap-2"),
    presets.map { preset =>
      TgHtmlButton
        .extraSmall(preset.label)
        .amend(
          typ("button"),
          title(s"${preset.startDate} - ${preset.endDate}"),
          onClick --> (_ =>
            formState.update(_.copy(startDate = preset.startDate.toString, endDate = preset.endDate.toString))
          )
        )
    }
  )

  private def durationLabel(dto: FindTaskRespFull): String = {
    val workDays = lookups.projectVar.now().workDays.map(DayOfWeek.of)
    val days = WorkDayCalendar.countWorkDays(dto.startDate, dto.dueDate, workDays)
    if (days == 1) "1 day" else s"$days days"
  }

  private def labelRow(dto: FindTaskRespFull): Seq[BadgeSpec] =
    if (dto.taskLabels.isEmpty) Seq(BadgeSpec(BulkTaskBadges.EmptyLabels, TgBadgeColor.Gray, Some(BulkEditKind.Labels)))
    else dto.taskLabels.sortBy(_.name).map(label => BadgeSpec(label.name, label.badgeColor, Some(BulkEditKind.Labels)))

  private def primaryRow(dto: FindTaskRespFull): Seq[BadgeSpec] =
    Seq(
      dto.milestone
        .flatMap(id => lookups.milestonesVar.now().find(_.id == id))
        .map(milestone => BadgeSpec(milestone.name, TgBadgeColor.Alternative, Some(BulkEditKind.Milestone)))
        .getOrElse(BadgeSpec(BulkTaskBadges.EmptyMilestone, TgBadgeColor.Gray, Some(BulkEditKind.Milestone))),
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
      workDays = lookups.projectVar.now().workDays.map(DayOfWeek.of)
    )

  private val lookupsSignal: Signal[BulkLookups] =
    lookups.statusesVar.signal
      .combineWith(lookups.taskLabelsVar.signal, lookups.projectVar.signal, lookups.milestonesVar.signal)
      .map { case (statuses, labels, project, milestones) =>
        BulkLookups(
          statuses = statuses.taskStatuses,
          labels = labels.taskLabels,
          members = project.members,
          milestones = milestones,
          workDays = project.workDays.map(DayOfWeek.of)
        )
      }

  /** Tasks whose update is in flight; they can't be edited again until it is done. */
  private val savingVar = Var(Set.empty[UUID])

  private val popups: BulkEditPopups = new BulkEditPopups(
    context = scope =>
      BulkEditContext(
        lookupsNow(),
        editableTasks(scope).map(ShowAllTasksBulkMapping.toBulkTask),
        tgClock.currentDate()
      ),
    onEdit = saveEdit
  )

  /** The task a badge edits: as it is staged, with the dates it has on the Gantt chart, or else as the page shows it.
    */
  private def editableTasks(scope: Option[UUID]): Seq[FindTaskRespFull] =
    scope.toSeq.flatMap { id =>
      stagingStore.stagedWithDatesNow(projectId).find(_.id == id).orElse(browser.rowsVar.now().find(_.id == id))
    }

  /** Saves the edit for the task, which a task the edit would not change (the value it already has) never reaches. The
    * page and the staged copies are read again afterwards, so both show what the backend has.
    */
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
        .runWithSpinner(save >> browser.loadPage() >> refreshStaged())
        .guarantee(IO(savingVar.update(_ - updated.id)))
        .unsafeRunAndForget()
    }
  }

  private def refreshStaged(): IO[Unit] =
    taskService
      .findAllByProjectId(projectId)
      .flatMap(projectTasks => IO(projectTasks.foreach(success => stagingStore.refresh(projectId, success.body.get))))

  private def isInteractive(target: dom.EventTarget): Boolean =
    target match {
      case element: dom.Element => element.closest("button, input, a") != null
      case _                    => false
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
      cls("bg-neutral-tertiary-medium") <-- stagingStore.stagedIds(projectId).map(_.contains(dto.id)),
      onClick.filter(event => !isInteractive(event.target)).mapToUnit --> (_ => stagingStore.toggle(projectId, dto))
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

  private val formDatesSignal: Signal[Option[(LocalDate, LocalDate)]] =
    formState.signal.map { form =>
      for {
        start <- Try(LocalDate.parse(form.startDate)).toOption
        end <- Try(LocalDate.parse(form.endDate)).toOption
        if !end.isBefore(start)
      } yield (start, end)
    }.distinct

  private val capacitySignal: Signal[Option[TimeboxCapacity]] =
    stagingStore
      .stagedWithDates(projectId)
      .combineWith(lookups.projectVar.signal, formDatesSignal)
      .map { case (tasks, project, dates) =>
        dates.map(TimeboxCapacity.of(tasks, project.members, project.workDays.map(DayOfWeek.of), _))
      }
      .distinct

  private val overCapacitySignal: Signal[Boolean] = capacitySignal.map(_.exists(_.isOverCapacity)).distinct

  private val cannotSaveSignal: Signal[Boolean] =
    overCapacitySignal.combineWith(datesInvalidSignal, changeCommentMissingSignal).map {
      case (overCapacity, datesInvalid, changeCommentMissing) => overCapacity || datesInvalid || changeCommentMissing
    }

  private val stagedTasks = new StagedTasksComponent(
    stagingStore,
    projectId,
    lookups.projectVar.signal.map(_.workDays.map(DayOfWeek.of)),
    formDatesSignal,
    tgDateFormater,
    capacitySignal,
    lookups.milestonesVar.signal,
    isDark,
    badgesAt("staged")
  )

  private val ganttChart = new TimeboxGanttComponent(
    stagingStore,
    projectId,
    lookups.projectVar.signal.map(_.workDays.map(DayOfWeek.of)),
    formDatesSignal,
    tgDateFormater,
    tgClock,
    lookupsSignal,
    badgesAt("gantt")
  )

  private val taskPicker = div(
    cls("mb-4"),
    browser.filterBindings,
    // Enter in a search box searches; it must not submit the timebox form around the picker.
    onKeyDown.filter(_.key == "Enter").preventDefault --> Observer.empty,
    div(cls("mb-4"), ganttChart.element),
    div(
      cls("grid grid-cols-1 items-start gap-4 lg:grid-cols-2"),
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Planned tasks", TgCard.CardHeaderSize.H5),
          body = TgCard.HtmlCardContent(div(browser.searchBar, taskTable, browser.pagination))
        )
        .element,
      stagedTasks.element
    )
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText(
          target match {
            case TimeboxFormTarget.Create(_)        => "Create a timebox"
            case TimeboxFormTarget.NewVersion(_, _) => "Save a new timebox version"
          },
          TgCard.CardHeaderSize.H3
        ),
        body = TgCard.HtmlCardContent(
          form(
            formState.signal.changes --> draftSaver,
            onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
            presetButtons,
            startDateInput.amend(cls("mb-4")),
            endDateInput.amend(cls("mb-4")),
            child.maybe <-- endBeforeStartError.map(
              _.map(message => p(cls("mb-4 text-xs text-fg-danger-strong"), message))
            ),
            descriptionEditor.htmlElement.amend(cls("mb-4")),
            if (isCreate) emptyNode
            else
              div(
                changeCommentEditor.htmlElement.amend(cls("mb-4")),
                child.maybe <-- changeCommentMissingSignal.map(
                  Option.when(_)(p(cls("mb-4 text-xs text-fg-danger-strong"), "Change comment is required"))
                )
              ),
            taskPicker,
            div(
              cls("flex items-center justify-end gap-2"),
              if (isCreate) clearAllButton else emptyNode,
              TgHtmlButton.createSmall(
                EventStream.merge(EventStream.fromValue(()).sample(cannotSaveSignal), cannotSaveSignal.changes)
              )
            )
          )
        )
      )
      .element
  )

}
