package com.togger.ui.timebox

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.{CreateTimeboxReq, FindTaskRespFull, FindTimeboxResp, SaveTimeboxVersionReq}
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.timebox.{TimeboxDefault, TimeboxDefaults}
import com.togger.toast.ToastMessages
import com.togger.components.button.TgHtmlButton
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{TgCard, TgCheckbox, TgInputType}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskFeService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.time.LocalDate
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
  description: String = "",
  taskIds: Set[UUID] = Set.empty
)

/** Creates a timebox, or saves a new version of an existing one (a full snapshot, versions are never edited). */
class TimeboxFormComponent(
  target: TimeboxFormTarget,
  timeboxService: TimeboxFeService,
  taskService: TaskFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgClock: TgClock,
  val toast: Toast
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  private val formState = Var(TimeboxForm())
  private val tasksVar = Var(Seq.empty[FindTaskRespFull])

  private val presets: List[TimeboxDefault] = TimeboxDefaults.defaults(tgClock.currentDate())

  private def prefill(timebox: FindTimeboxResp): Unit = {
    val version = timebox.latestVersion
    formState.set(
      TimeboxForm(
        startDate = version.startDate.toString,
        endDate = version.endDate.toString,
        description = version.description.getOrElse(""),
        taskIds = version.tasks.map(_.taskId).toSet
      )
    )
  }

  spinnerService
    .runWithSpinner {
      // The picker offers every task of the project, so all pages are read (bounded by PaginatedLoader). TODO: replace
      // with a paged search-as-you-type picker if projects grow beyond that bound.
      val tasksCall = taskService
        .findAllByProjectId(target.projectId)
        .map(_.map(success => tasksVar.set(success.body.get)))
      target match {
        case TimeboxFormTarget.Create(_) =>
          tasksCall
        case TimeboxFormTarget.NewVersion(_, timeboxId) =>
          tasksCall.flatMap(_ =>
            timeboxService.findById(timeboxId).map(_.map(success => prefill(success.body.get)))
          )
      }
    }
    .unsafeRunAndForget()

  private def backToList(): Unit = router.route(ShowTimeboxesPage(target.projectId))

  private val submitter = Observer[TimeboxForm] { form =>
    val description = Option(form.description.trim).filter(_.nonEmpty)
    val start = LocalDate.parse(form.startDate)
    val end = LocalDate.parse(form.endDate)
    val taskIds = form.taskIds.toSeq
    val call = target match {
      case TimeboxFormTarget.Create(projectId) =>
        timeboxService.createTimebox(CreateTimeboxReq(projectId, start, end, description, taskIds))
      case TimeboxFormTarget.NewVersion(_, timeboxId) =>
        timeboxService.saveNewVersion(SaveTimeboxVersionReq(timeboxId, start, end, description, taskIds))
    }
    spinnerService
      .runWithSpinner(call)
      .toastMessageCodeBlock("Timebox saved successfully")(backToList())
      .unsafeRunAndForget()
  }

  private val startDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.startDate),
    errors = EventStream.empty,
    labelValue = "Start date",
    tgInputType = TgInputType.Date
  )

  private val endDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.endDate),
    errors = formState.signal.changes.map { form =>
      if (
        form.startDate.nonEmpty && form.endDate.nonEmpty &&
        LocalDate.parse(form.endDate).isBefore(LocalDate.parse(form.startDate))
      )
        Seq("End date must be on or after start date")
      else Seq.empty
    },
    labelValue = "End date",
    tgInputType = TgInputType.Date
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Description (optional)",
    minWidthRem = 0
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

  private def taskCheckbox(task: FindTaskRespFull) =
    TgCheckbox
      .checkBox(
        inputSignal = formState.signal.map(_.taskIds.contains(task.id)),
        inputStateUpdater = Observer[Boolean] { checked =>
          formState.update(form =>
            form.copy(taskIds = if (checked) form.taskIds + task.id else form.taskIds - task.id)
          )
        },
        labelValue = task.name
      )
      .htmlElement

  private val taskPicker = div(
    cls("mb-4"),
    h5(cls("mb-2"), "Planned tasks"),
    children <-- tasksVar.signal.map(_.sortBy(_.name).map(taskCheckbox))
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
            onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
            presetButtons,
            startDateInput.htmlElement.amend(cls("mb-4")),
            endDateInput.htmlElement.amend(cls("mb-4")),
            descriptionEditor.htmlElement.amend(cls("mb-4")),
            taskPicker,
            TgHtmlButton.create(EventStream.fromValue(false))
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
