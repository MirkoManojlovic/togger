package com.togger.ui.tasktemplate

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.markdown.TgMarkdown
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskLabelFeService, TgDateFormater}
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

class EditTaskTemplateComponent(
  taskTemplateService: TaskTemplateFeService,
  taskLabelService: TaskLabelFeService,
  spinnerService: SpinnerService,
  tgMarkdown: TgMarkdown,
  tgDateFormater: TgDateFormater,
  tgClock: TgClock,
  accountService: AccountService,
  projectId: UUID,
  taskTemplateId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(TaskTemplateForm(id = taskTemplateId, projectId = projectId))

  private val selectableLabelsVar = Var(Seq.empty[FindTaskLabelResp])
  private val versionsVar = Var(Seq.empty[TaskTemplateVersionDto])

  private def loadVersions(): IO[Unit] =
    taskTemplateService.listAllVersions(taskTemplateId).map {
      case Left(failure) => println(failure)
      case Right(success) =>
        versionsVar.set(success.body.getOrElse(Seq.empty).sortBy(_.createdAt).reverse)
    }

  private def loadForm(): IO[Unit] =
    taskTemplateService.findTaskTemplateById(taskTemplateId).flatMap {
      case Left(failure) => IO(println(failure))
      case Right(success) =>
        val dto = success.body.get
        formState.set(
          TaskTemplateForm(
            projectId = dto.projectId,
            id = dto.id,
            name = dto.name,
            body = dto.body,
            taskLabelId = dto.taskLabelId
          )
        )
        loadSelectableLabels(dto)
    }

  private def loadSelectableLabels(own: FindTaskTemplateResp): IO[Unit] =
    for {
      labelsEither <- taskLabelService.findAllByProjectId(own.projectId)
      templatesEither <- taskTemplateService.findAllByProjectId(own.projectId)
    } yield {
      val labels = labelsEither.toOption.flatMap(_.body).getOrElse(Seq.empty)
      val templates = templatesEither.toOption.flatMap(_.body).getOrElse(Seq.empty)
      selectableLabelsVar.set(TaskTemplateFormSupport.selectableLabels(labels, templates, own.taskLabelId))
    }

  spinnerService.runWithSpinner(loadForm().flatMap(_ => loadVersions())).unsafeRunAndForget()

  lazy val submitter = Observer[TaskTemplateForm] { form =>
    val dto = UpdateTaskTemplateReq(id = form.id, name = form.name, body = form.body, taskLabelId = form.taskLabelId)
    spinnerService
      .runWithSpinner(taskTemplateService.updateTaskTemplate(dto).flatTap {
        case Right(_) => loadVersions()
        case Left(_)  => IO.unit
      })
      .toastMessageJustMessage("Task template saved as a new version")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Template name",
    tgInputType = TgInputType.Text
  )

  private val bodyEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.body),
    labelValue = "Template body",
    placeholderValue = TaskTemplateFormSupport.bodyHint
  )

  /** Read-only: old versions are listed and shown rendered, never edited or deleted. */
  private def historyPanel: HtmlElement =
    div(
      cls("mt-6"),
      h4(cls("mb-2 text-sm font-medium text-heading"), "Version history"),
      children <-- versionsVar.signal.map(_.map { version =>
        div(
          cls("mb-3 p-3 border border-default rounded-base bg-neutral-primary-soft"),
          div(
            cls("mb-2 text-xs text-body-subtle"),
            s"${version.name} - ${tgDateFormater.yearMonthDayTimeFormat(version.createdAt)}"
          ),
          tgMarkdown.toLaminarDiv(version.body)
        )
      })
    )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit task template", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              div(cls("mb-4"), child <-- TaskTemplateFormSupport.labelPicker(selectableLabelsVar.signal, formState)),
              bodyEditor.htmlElement.amend(cls("mb-4")),
              TgHtmlButton.update(EventStream.fromValue(false))
            ),
            historyPanel
          )
        )
      )
      .element
  )

}
