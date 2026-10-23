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

case class EditTaskStatusForm(
  projectId: UUID = UUID.randomUUID(),
  taskStatusId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  emoji: String = ""
)

class EditTaskStatusComponent(
  taskStatusService: TaskStatusFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectId: UUID,
  taskStatusId: UUID,
  localStorage: LocalStorage,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditTaskStatusForm(taskStatusId = taskStatusId, projectId = projectId))

  spinnerService
    .runWithSpinner(taskStatusService.findTaskStatusById(taskStatusId))
    .map(
      _.fold(
        error => println(error),
        success => {
          val dto = success.body.get
          formState.set(
            EditTaskStatusForm(
              projectId = dto.projectId,
              taskStatusId = dto.id,
              name = dto.name,
              description = dto.description,
              emoji = dto.emoji
            )
          )
        }
      )
    )
    .unsafeRunAndForget()

  lazy val submitter = Observer[EditTaskStatusForm] { form =>
    val dto = UpdateTaskStatusResp(
      projectId = form.projectId,
      id = form.taskStatusId,
      name = form.name,
      description = form.description,
      emoji = form.emoji
    )
    spinnerService
      .runWithSpinner(taskStatusService.updateTaskStatus(dto))
      .toastMessageJustMessage("Task status updated successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Status name",
    tgInputType = TgInputType.Text
  )

  private val descriptionInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Status description",
    tgInputType = TgInputType.Text
  )

  private val emojiInput = formState.tgEmojiPicker(
    inputAndUpdater = selectorAndUpdaterMacro(_.emoji),
    errors = EventStream.empty,
    labelValue = "Status emoji",
    localStorage = localStorage
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a task status", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              descriptionInput.htmlElement.amend(cls("mb-4")),
              emojiInput.htmlElement.amend(cls("mb-4")),
              TgHtmlButton.update(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
