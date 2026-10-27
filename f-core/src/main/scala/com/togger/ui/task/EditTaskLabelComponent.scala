package com.togger.ui.task

import cats.effect.unsafe.IORuntime
import com.togger.common.TgClock
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.emoji.TgEmojiField.TgEmojiFieldSyntax
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class EditTaskLabelForm(
  projectId: UUID = UUID.randomUUID(),
  taskLabelId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  emoji: String = ""
)

class EditTaskLabelComponent(
  taskLabelService: TaskLabelFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectId: UUID,
  taskLabelId: UUID,
  localStorage: LocalStorage,
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
              description = dto.description,
              emoji = dto.emoji
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
      description = form.description,
      emoji = form.emoji
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

  private val emojiInput = formState.tgEmojiPicker(
    inputAndUpdater = selectorAndUpdaterMacro(_.emoji),
    errors = EventStream.empty,
    labelValue = "Label emoji",
    localStorage = localStorage
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
              nameInput.htmlElement.amend(cls("mb-4 w-[60ch] max-w-full")),
              emojiInput.htmlElement.amend(cls("mb-4")),
              descriptionInput.htmlElement.amend(cls("mb-4 w-[60ch] max-w-full")),
              div(cls("flex justify-end"), TgHtmlButton.updateSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
