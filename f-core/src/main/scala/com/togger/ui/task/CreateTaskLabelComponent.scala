package com.togger.ui.task

import cats.effect.unsafe.IORuntime
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgClock
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.task.TaskLabelEndpoints
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.emoji.TgEmojiField.TgEmojiFieldSyntax
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class CreateTaskLabelForm(
  projectId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  emoji: String = ""
)

class CreateTaskLabelComponent(
  taskLabelService: TaskLabelFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectId: UUID,
  localStorage: LocalStorage,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateTaskLabelForm(projectId = projectId))

  lazy val submitter = Observer[CreateTaskLabelForm] { form =>
    val dto = CreateTaskLabelReq(
      projectId = form.projectId,
      name = form.name,
      description = form.description,
      emoji = form.emoji
    )
    spinnerService
      .runWithSpinner(taskLabelService.createTaskLabel(dto))
      .toastMessageJustMessage("Task label created successfully")
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
              nameInput.htmlElement.amend(cls("mb-4")),
              emojiInput.htmlElement.amend(cls("mb-4")),
              descriptionInput.htmlElement.amend(cls("mb-4")),
              TgHtmlButton.create(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}

class TaskLabelFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val taskLabelEndpoints = new TaskLabelEndpoints(securedEndpoint)

  def createTaskLabel(dto: CreateTaskLabelReq): TgRespIO[CreateTaskLabelResp] =
    protectedBackendClient.protectedHttpCall(taskLabelEndpoints.saveEndpoint)(dto)

  def updateTaskLabel(dto: UpdateTaskLabelResp): TgRespIO[FindTaskLabelResp] =
    protectedBackendClient.protectedHttpCall(taskLabelEndpoints.updateEndpoint)(dto)

  def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindTaskLabelResp]] =
    protectedBackendClient.protectedHttpCall(taskLabelEndpoints.findByProjectIdEndpoint)((projectId, page))

  /** Every label of the project, for the pickers and filters offering all of them; see [[PaginatedLoader]] for the
    * bound.
    */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[FindTaskLabelResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))

  def findTaskLabelById(id: UUID): TgRespIO[FindTaskLabelResp] =
    protectedBackendClient.protectedHttpCall(taskLabelEndpoints.findByIdEndpoint)(id)

  def deleteTaskLabelById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(taskLabelEndpoints.deleteByIdEndpoint)(id)
}
