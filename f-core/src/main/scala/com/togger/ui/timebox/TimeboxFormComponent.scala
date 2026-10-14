package com.togger.ui.timebox

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.dto.{CreateTimeboxReq, FindTaskRespFull, FindTimeboxResp, SaveTimeboxVersionReq}
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TaskListParams, TgRouter}
import com.togger.timebox.{TimeboxDefault, TimeboxDefaults}
import com.togger.toast.ToastMessages
import com.togger.components.button.TgHtmlButton
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgCard
import com.togger.ui.auth.TgInput.tgInputStyle
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgHtmlTable.createTd
import com.togger.ui.components.{CalendarPickerPopup, TgBadge, TgBadgeColor, TgHtmlTable}
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{
  TaskFeService,
  TaskLabelFeService,
  TaskListBrowser,
  TaskListLookups,
  TaskStatusFeService,
  TgDateFormater
}
import com.togger.ui.task.bulk.BulkTaskBadges
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLTableCellElement

import java.time.{DayOfWeek, LocalDate}
import scala.util.Try
import java.util.UUID

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
  description: String = ""
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
  val toast: Toast
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  // Only a new timebox is remembered; a new version starts from the timebox's latest version instead.
  private val isCreate = target match {
    case TimeboxFormTarget.Create(_)     => true
    case TimeboxFormTarget.NewVersion(_, _) => false
  }

  private val formState = Var(if (isCreate) draftStore.load(target.projectId).getOrElse(TimeboxForm()) else TimeboxForm())

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
        timeboxService.saveNewVersion(SaveTimeboxVersionReq(timeboxId, start, end, description, taskIds))
    }
    spinnerService
      .runWithSpinner(call)
      .toastMessageCodeBlock("Timebox saved successfully") {
        stagingStore.clear(projectId)
        draftStore.clear(projectId)
        backToList()
      }
      .unsafeRunAndForget()
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

  private def taskCell(dto: FindTaskRespFull) =
    createTd(
      div(
        cls("flex flex-col gap-1"),
        div(
          cls("flex flex-wrap items-center gap-x-2 gap-y-1"),
          span(cls("text-base font-semibold text-heading"), dto.name),
          if (dto.taskLabels.isEmpty) Seq(TgBadge(BulkTaskBadges.EmptyLabels, TgBadgeColor.Gray).element)
          else dto.taskLabels.sortBy(_.name).map(label => TgBadge(label.name, label.badgeColor).element)
        ),
        div(
          cls("flex flex-wrap items-center gap-1.5"),
          dto.milestone
            .flatMap(id => lookups.milestonesVar.now().find(_.id == id))
            .fold(TgBadge(BulkTaskBadges.EmptyMilestone, TgBadgeColor.Gray).element)(milestone =>
              TgBadge(milestone.name, TgBadgeColor.Alternative).element
            ),
          TgBadge(dto.priority.shortCode, dto.priority.badgeColor).element,
          TgBadge(dto.taskStatus.name, dto.taskStatus.badgeColor).element,
          TgBadge(dto.assignedTo.fold("Not assigned")(_.fullName), TgBadgeColor.Gray).element,
          TgBadge(tgDateFormater.asAmericanDateWithDayAbrv(dto.startDate), TgBadgeColor.Gray).element,
          TgBadge(tgDateFormater.asAmericanDateWithDayAbrv(dto.dueDate), TgBadgeColor.Gray).element,
          TgBadge(durationLabel(dto), TgBadgeColor.Gray).element
        )
      )
    ).amend(
      cls("cursor-pointer"),
      cls("bg-neutral-tertiary-medium") <-- stagingStore.stagedIds(projectId).map(_.contains(dto.id)),
      onClick.mapToUnit --> (_ => stagingStore.toggle(projectId, dto))
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
      .staged(projectId)
      .combineWith(lookups.projectVar.signal, formDatesSignal)
      .map { case (tasks, project, dates) =>
        dates.map(TimeboxCapacity.of(tasks, project.members, project.workDays.map(DayOfWeek.of), _))
      }
      .distinct

  private val overCapacitySignal: Signal[Boolean] = capacitySignal.map(_.exists(_.isOverCapacity)).distinct

  private val cannotSaveSignal: Signal[Boolean] =
    overCapacitySignal.combineWith(datesInvalidSignal).map { case (overCapacity, datesInvalid) =>
      overCapacity || datesInvalid
    }

  private val stagedTasks = new StagedTasksComponent(
    stagingStore,
    projectId,
    lookups.projectVar.signal.map(_.workDays.map(DayOfWeek.of)),
    formDatesSignal,
    tgDateFormater,
    capacitySignal
  )

  private val taskPicker = div(
    cls("mb-4"),
    browser.filterBindings,
    // Enter in a search box searches; it must not submit the timebox form around the picker.
    onKeyDown.filter(_.key == "Enter").preventDefault --> Observer.empty,
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
            case TimeboxFormTarget.Create(_)     => "Create a timebox"
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
            child.maybe <-- endBeforeStartError.map(_.map(message => p(cls("mb-4 text-xs text-fg-danger-strong"), message))),
            descriptionEditor.htmlElement.amend(cls("mb-4")),
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
      .amend(heightAndWidthFitContent)
  )

}
