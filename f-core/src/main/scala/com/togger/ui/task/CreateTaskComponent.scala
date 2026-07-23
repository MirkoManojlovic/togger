package com.togger.ui.task

import cats.effect.unsafe.IORuntime
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgClock
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.logentry.LogEntryEndpoints
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.task.TaskEndpoints
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID
import scala.collection.immutable.ListMap

case class CreateTaskForm(
  name: String = "",
  description: String = "",
  taskStatusSelected: String = ""
)

case class TaskStatusesState(
  taskStatuses: Seq[FindTaskStatusResp] = Seq.empty
) {
  lazy val asTaskStatusSelect =
    taskStatuses.map(dto => TaskStatusSelect(id = dto.id, name = dto.name))

  lazy val idToName: Map[UUID, String] =
    taskStatuses.map(dto => dto.id -> dto.name).toMap

}

case class TaskStatusSelect(id: UUID, name: String) {
  lazy val asTuple: (String, String) = id.toString -> name
}

class CreateTaskComponent(
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateTaskForm())
  val statusesVar = Var(TaskStatusesState())

  val firstLink = new TaskLinkComponent()
  val linksVar = Var(Seq(firstLink))

  spinnerService
    .runWithSpinner(
      taskStatusFeService
        .findByProjectId(projectId)
        .map(response => response.map(success => statusesVar.set(TaskStatusesState(success.body.get))))
    )
    .unsafeRunAndForget()

  lazy val addNewLink =
    TgHtmlButton
      .extraSmall("+")
      .amend(
        typ("button"),
        cls("mb-4"),
        onClick --> (_ => linksVar.update(state => state :+ new TaskLinkComponent()))
      )

  lazy val submitter = Observer[(CreateTaskForm, Seq[TaskLinkComponent])] { case (form, linkComponents) =>
    val now = tgClock.currentInstant

    val dto = CreateTaskAndLinksReq(
      task = CreateTaskReq(
        projectId = projectId,
        name = form.name,
        description = form.description,
        assignedTo = Some(accountService.get.get.id),
        createdBy = accountService.get.get.id,
        taskStatusId = UUID.fromString(form.taskStatusSelected),
        createdAt = now,
        updatedAt = now
      ),
      links = linkComponents.map { linkComponent =>
        val linkForm = linkComponent.formState.now()
        CreateLinkReq(
          name = linkForm.name,
          url = linkForm.url
        )
      }
    )
    spinnerService
      .runWithSpinner(taskService.createTask(dto))
      .toastMessageJustMessage("Task created successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Task name",
    tgInputType = TgInputType.Text
  )

  private val descriptionTextArea = formState.tgTextAreaManualErrorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Task name"
  )

  def renderElement(selectValues: Seq[TaskStatusSelect]) = {
    val statusInputSelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.taskStatusSelected),
      labelValue = "Status",
      valuesAndDisplays = ListMap.from(selectValues.map(_.asTuple))
    )

    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Create a task", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              form(
                onSubmit.preventDefault.mapTo(formState.now() -> linksVar.now()) --> submitter,
                nameInput.htmlElement.amend(cls("mb-4")),
                descriptionTextArea.htmlElement.amend(cls("mb-4")),
                statusInputSelect.htmlElement.amend(cls("mb-4")),
                child <-- linksVar.signal.map(taskLinkComponent =>
                  div(addNewLink, taskLinkComponent.map(_.htmlElement))
                ),
                TgHtmlButton.create(EventStream.fromValue(false))
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    )
  }

  val element = div(
    child <-- statusesVar.signal.map(state => renderElement(state.asTaskStatusSelect))
  )

}

class TaskFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val taskEndpoints = new TaskEndpoints(securedEndpoint)
  val logEntryEndpoints = new LogEntryEndpoints(securedEndpoint)

  def createTask(dto: CreateTaskAndLinksReq): TgRespIO[FindTaskResp] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.saveEndpoint)(dto)

  def updateTask(dto: UpdateTaskReq): TgRespIO[FindTaskResp] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.updateEndpoint)(dto)

  def findByProjectId(projectId: UUID): TgRespIO[Seq[FindTaskRespFull]] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.findByProjectIdEndpoint)(projectId)

  def findTaskById(id: UUID): TgRespIO[FindTaskResp] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.findByIdEndpoint)(id)

  def saveLogEntry(dto: LogEntryDto) =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.saveEndpoint)(dto)

  def updateLogEntry(dto: LogEntryDto) =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.updateEndpoint)(dto)

  def listLogEntryVersions(logEntryId: UUID) =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.listVersionsEndpoint)(logEntryId)

  def updateLogEntryVersion(dto: LogEntryVersionDto) =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.updateVersionEndpoint)(dto)

  def deleteLogEntryVersion(version: UUID) =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.deleteVersionEndpoint)(version)

}
