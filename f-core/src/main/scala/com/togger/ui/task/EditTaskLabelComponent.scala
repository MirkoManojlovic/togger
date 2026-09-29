package com.togger.ui.task

import cats.effect.unsafe.IORuntime
import com.togger.common.TgClock
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class EditTaskLabelForm(
  projectId: UUID = UUID.randomUUID(),
  taskLabelId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = ""
)

class EditTaskLabelComponent(
  taskLabelService: TaskLabelFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectId: UUID,
  taskLabelId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditTaskLabelForm(taskLabelId = taskLabelId, projectId = projectId))

  spinnerService
    .runWithSpinner(taskLabelService.findTaskLabelById(taskLabelId))
    .map(
      _.fold(
        error => println(error),
        success => {
          val dto = success.body.get
          formState.set(
            EditTaskLabelForm(
              projectId = dto.projectId,
              taskLabelId = dto.id,
              name = dto.name,
              description = dto.description
            )
          )
        }
      )
    )
    .unsafeRunAndForget()

  lazy val submitter = Observer[EditTaskLabelForm] { form =>
    val dto = UpdateTaskLabelResp(
      projectId = form.projectId,
      id = form.taskLabelId,
      name = form.name,
      description = form.description
    )
    spinnerService
      .runWithSpinner(taskLabelService.updateTaskLabel(dto))
      .toastMessageJustMessage("Task label updated successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Label name",
    tgInputType = TgInputType.Text
  )

  private val descriptionInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Label description",
    tgInputType = TgInputType.Text
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a task label", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              descriptionInput.htmlElement.amend(cls("mb-4")),
              TgHtmlButton.update(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
