package com.togger.ui.tasktemplate

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, TgJsonFailure, TgJsonSuccess}
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskLabelFeService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

class CreateTaskTemplateComponent(
  taskTemplateService: TaskTemplateFeService,
  taskLabelService: TaskLabelFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(TaskTemplateForm(projectId = projectId))

  private val selectableLabelsVar = Var(Seq.empty[FindTaskLabelResp])

  spinnerService
    .runWithSpinner {
      val labelsCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskLabelResp]]]] =
        taskLabelService.findAllByProjectId(projectId)
      val templatesCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskTemplateResp]]]] =
        taskTemplateService.findAllByProjectId(projectId)
      (labelsCall, templatesCall).parMapN { (labelsEither, templatesEither) =>
        val labels = labelsEither.toOption.flatMap(_.body).getOrElse(Seq.empty)
        val templates = templatesEither.toOption.flatMap(_.body).getOrElse(Seq.empty)
        selectableLabelsVar.set(TaskTemplateFormSupport.selectableLabels(labels, templates, None))
      }
    }
    .unsafeRunAndForget()

  lazy val submitter = Observer[TaskTemplateForm] { form =>
    val dto = CreateTaskTemplateReq(
      projectId = form.projectId,
      name = form.name,
      body = form.body,
      taskLabelId = form.taskLabelId
    )
    spinnerService
      .runWithSpinner(taskTemplateService.createTaskTemplate(dto))
      .toastMessageJustMessage("Task template created successfully")
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

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a task template", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              div(cls("mb-4"), child <-- TaskTemplateFormSupport.labelPicker(selectableLabelsVar.signal, formState)),
              bodyEditor.htmlElement.amend(cls("mb-4")),
              div(cls("flex justify-end"), TgHtmlButton.createSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
      .amend(cls("min-w-0 lg:w-[80rem] lg:shrink-0"))
  )

}
