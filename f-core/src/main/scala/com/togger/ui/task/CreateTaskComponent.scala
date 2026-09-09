package com.togger.ui.task

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{TgClock, TgJsonFailure, TgJsonSuccess}
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.logentry.LogEntryEndpoints
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.task.{DefaultTaskStatus, LinkEndpoints, TaskEndpoints}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID
import scala.collection.immutable.ListMap

case class CreateTaskForm(
  name: String = "",
  description: String = "",
  taskStatusSelected: String = "",
  assignedToSelected: String = ""
)

case class TaskStatusesState(
  taskStatuses: Seq[FindTaskStatusResp] = Seq.empty
) {
  lazy val asTaskStatusSelect =
    taskStatusesSorted.map(dto => TaskStatusSelect(id = dto.id, name = dto.name))

  lazy val idToName: Map[UUID, String] =
    taskStatuses.map(dto => dto.id -> dto.name).toMap

  lazy val idToOrder: Map[UUID, Int] =
    taskStatuses.map(dto => dto.id -> DefaultTaskStatus.values.find(_.statusName == dto.name).get.i).toMap

  lazy val taskStatusesSorted: Seq[FindTaskStatusResp] =
    taskStatuses
      .map(taskStatusResp =>
        DefaultTaskStatus.values.find(enum1 => enum1.statusName == taskStatusResp.name).get -> taskStatusResp
      )
      .sortBy { case (enum, _) => enum.i }
      .map(_._2)

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
  projectService: ProjectService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(
    CreateTaskForm(assignedToSelected = accountService.get.map(_.id.toString).getOrElse(""))
  )
  val statusesVar = Var(TaskStatusesState())
  val membersVar = Var(Seq.empty[SearchAccountDto])

  val firstLink = new TaskLinkComponent()
  val linksVar = Var(Seq(firstLink))

  spinnerService
    .runWithSpinner {
      val call1: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskStatusResp]]]] =
        taskStatusFeService.findByProjectId(projectId)

      val call2: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[SearchAccountDto]]]] =
        projectService.findAllMembers(projectId)

      (call1, call2).parMapN { (findTaskStatusRespEither, searchAccountDtoEither) =>
        findTaskStatusRespEither.map(success => statusesVar.set(TaskStatusesState(success.body.get)))
        searchAccountDtoEither.map(success => membersVar.set(success.body.get))
      }
    }
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
        assignedTo = Option.when(form.assignedToSelected.nonEmpty)(UUID.fromString(form.assignedToSelected)),
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

  def renderElement(selectValues: Seq[TaskStatusSelect], members: Seq[SearchAccountDto]) = {
    val statusInputSelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.taskStatusSelected),
      labelValue = "Status",
      valuesAndDisplays = ListMap.from(selectValues.map(_.asTuple))
    )

    val assignedToSelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.assignedToSelected),
      labelValue = "Assigned to",
      emptyValue = Some("Unassigned"),
      valuesAndDisplays = ListMap.from(members.map(member => member.id.toString -> member.fullName))
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
                assignedToSelect.htmlElement.amend(cls("mb-4")),
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
    child <-- statusesVar.signal.combineWith(membersVar.signal).map { case (state, members) =>
      renderElement(state.asTaskStatusSelect, members)
    }
  )

}

class TaskFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val taskEndpoints = new TaskEndpoints(securedEndpoint)
  val logEntryEndpoints = new LogEntryEndpoints(securedEndpoint)
  val linkEndpoints = new LinkEndpoints(securedEndpoint)

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

  def listLinkVersions(linkId: UUID) =
    protectedBackendClient.protectedHttpCall(linkEndpoints.listVersionsEndpoint)(linkId)

  def updateLinkVersion(dto: LinkVersionDto) =
    protectedBackendClient.protectedHttpCall(linkEndpoints.updateVersionEndpoint)(dto)

  def deleteLinkVersion(version: UUID) =
    protectedBackendClient.protectedHttpCall(linkEndpoints.deleteVersionEndpoint)(version)

  def listTaskVersions(taskId: UUID) =
    protectedBackendClient.protectedHttpCall(taskEndpoints.listVersionsEndpoint)(taskId)

  def updateTaskVersion(dto: TaskVersionDto) =
    protectedBackendClient.protectedHttpCall(taskEndpoints.updateVersionEndpoint)(dto)

  def deleteTaskVersion(version: UUID) =
    protectedBackendClient.protectedHttpCall(taskEndpoints.deleteVersionEndpoint)(version)

}
