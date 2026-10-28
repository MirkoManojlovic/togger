package com.togger.ui.task

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common._
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.logentry.LogEntryEndpoints
import com.togger.routes.Pages.EditTaskPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.task.{DefaultTaskStatus, LinkEndpoints, TaskEndpoints}
import com.togger.toast.ToastMessages
import com.togger.ui.milestone.MilestoneSummaryRangeCalendar
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.MultiSelectItem
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.tasktemplate.{TaskTemplateFeService, TaskTemplatePicker, TaskTemplateSelection}
import com.togger.ui.task.bulk.{BulkTask, BulkTaskDefaults}
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.time.DayOfWeek
import java.util.UUID

case class CreateTaskForm(
  name: String = "",
  description: String = ""
)

case class TaskStatusesState(
  taskStatuses: Seq[FindTaskStatusResp] = Seq.empty
) {
  lazy val asTaskStatusSelect =
    taskStatusesSorted.map(dto => TaskStatusSelect(id = dto.id, name = dto.badgeText))

  /** Statuses can be renamed, so a name that is not a default one sorts after the default ones, alphabetically. */
  private def orderOf(status: FindTaskStatusResp): Int =
    DefaultTaskStatus.values.find(_.statusName == status.name).map(_.i).getOrElse(DefaultTaskStatus.values.size)

  lazy val idToOrder: Map[UUID, Int] =
    taskStatuses.map(dto => dto.id -> orderOf(dto)).toMap

  lazy val taskStatusesSorted: Seq[FindTaskStatusResp] =
    taskStatuses.sortBy(status => (orderOf(status), status.name))

}

case class TaskStatusSelect(id: UUID, name: String) {
  lazy val asTuple: (String, String) = id.toString -> name
}

/** Deliberately does not mirror [[TaskStatusesState.idToOrder]]'s name-matching-against-a-fixed-enum pattern - labels
  * are expected to be renamed/added to more freely than statuses, so sorting is plain alphabetical.
  */
case class TaskLabelsState(taskLabels: Seq[FindTaskLabelResp] = Seq.empty) {
  lazy val asMultiSelectItems: Seq[MultiSelectItem] =
    taskLabels.sortBy(_.name).map(dto => MultiSelectItem(id = dto.id.toString, name = dto.badgeText, isSelected = false))
}

class CreateTaskComponent(
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  taskTemplateFeService: TaskTemplateFeService,
  milestoneService: MilestoneFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectService: ProjectService,
  projectId: UUID,
  router: TgRouter[FrontendPage],
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateTaskForm())

  private val badges = new TaskFormBadges(
    BulkTask
      .withDefaults(
        UUID.randomUUID(),
        "",
        BulkTaskDefaults.of(tgClock.currentDate(), Set.empty, Seq.empty)
      )
      .copy(assigneeId = accountService.get.map(_.id)),
    tgClock,
    Some(
      TaskProgressComponent
        .fromDescription(formState.signal.map(_.description), ringSizeCls = "w-3 h-3", textSizeCls = "text-xs")
        .element
    )
  )

  private val templatesVar = Var(Seq.empty[FindTaskTemplateResp])
  private val selectedTemplateVar = Var(Option.empty[UUID])
  private val appliedBodyVar = Var(Option.empty[String])
  private val previousLabelIdsVar = Var(Set.empty[UUID])

  private def applyTemplate(template: FindTaskTemplateResp): Unit = {
    formState.update(_.copy(description = template.body))
    appliedBodyVar.set(Some(template.body))
    selectedTemplateVar.set(Some(template.id))
  }

  private val onLabelsChanged = Observer[Set[UUID]] { labelIds =>
    TaskTemplateSelection
      .onLabelsChanged(
        templatesVar.now(),
        previousLabelIdsVar.now(),
        labelIds,
        formState.now().description,
        appliedBodyVar.now()
      )
      .foreach(applyTemplate)
    previousLabelIdsVar.set(labelIds)
  }

  private val onTemplatePicked = Observer[Option[FindTaskTemplateResp]] { choice =>
    val body = TaskTemplateSelection.descriptionFor(choice)
    formState.update(_.copy(description = body))
    appliedBodyVar.set(choice.map(_.body))
    selectedTemplateVar.set(choice.map(_.id))
  }

  private val linksSection = new TaskLinksSection
  val linksVar: Var[Seq[TaskLinkComponent]] = linksSection.linksVar

  spinnerService
    .runWithSpinner {
      // The status picker offers every status, read page by page (bounded by PaginatedLoader).
      val call1: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskStatusResp]]]] =
        taskStatusFeService.findAllByProjectId(projectId)

      // The assignee picker offers every member, read page by page (bounded by PaginatedLoader).
      val call2: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[SearchAccountDto]]]] =
        projectService.findAllMembers(projectId)

      // The label picker offers every label, read page by page (bounded by PaginatedLoader).
      val call3: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskLabelResp]]]] =
        taskLabelFeService.findAllByProjectId(projectId)

      val call4: IO[Either[TgJsonFailure, TgJsonSuccess[FindProjectResp]]] =
        projectService.findById(projectId)

      // The milestone picker offers every milestone, read page by page (bounded by PaginatedLoader).
      val call5: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindMilestoneResp]]]] =
        milestoneService.findAllByProjectId(projectId)

      // The template picker offers every template, read page by page (bounded by PaginatedLoader).
      val call6: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskTemplateResp]]]] =
        taskTemplateFeService.findAllByProjectId(projectId)

      (call1, call2, call3, call4, call5, call6).parMapN {
        (
          findTaskStatusRespEither,
          searchAccountDtoEither,
          findTaskLabelRespEither,
          findProjectRespEither,
          findMilestoneRespEither,
          findTaskTemplateRespEither
        ) =>
          findTaskTemplateRespEither.map(success => templatesVar.set(success.body.get))
          findTaskStatusRespEither.map(success => badges.setStatuses(success.body.get))
          searchAccountDtoEither.map(success => badges.setMembers(success.body.get))
          findTaskLabelRespEither.map(success => badges.setLabels(success.body.get))
          findMilestoneRespEither.map(success => badges.setMilestones(success.body.get))
          findProjectRespEither.map(success => badges.setWorkDays(success.body.get.workDays.map(DayOfWeek.of)))
          applyDefaults(findTaskStatusRespEither.map(_.body.get).getOrElse(Seq.empty))
      }
    }
    .unsafeRunAndForget()

  /** The defaults of a new task need the project's work days and statuses, so they are set once these are loaded. */
  private def applyDefaults(statuses: Seq[FindTaskStatusResp]): Unit = {
    val defaults = BulkTaskDefaults.of(tgClock.currentDate(), badges.lookupsNow().workDays, statuses)
    badges.taskVar.update(
      _.copy(
        priority = defaults.priority,
        statusId = defaults.statusId,
        startDate = defaults.startDate,
        dueDate = defaults.dueDate
      )
    )
  }

  lazy val submitter = Observer[(CreateTaskForm, Seq[TaskLinkComponent], BulkTask)] {
    case (form, linkComponents, created) =>
      val now = tgClock.currentInstant

      val dto = CreateTaskAndLinksReq(
        task = CreateTaskReq(
          projectId = projectId,
          name = form.name,
          description = form.description,
          assignedTo = created.assigneeId,
          createdBy = accountService.get.get.id,
          taskStatusId = created.statusId.get,
          createdAt = now,
          updatedAt = now,
          priority = created.priority,
          milestone = created.milestoneId,
          startDate = created.startDate,
          dueDate = created.dueDate,
          taskLabelIds = created.labelIds.toSeq
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
        .toastMessageFn("Task created successfully")(created =>
          router.route(EditTaskPage(projectId, created.task.id))
        )
        .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Task name",
    tgInputType = TgInputType.Text
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Description",
    placeholderValue = "Describe the task"
  )

  val element =
    div(
      justifyCenterFlex,
      badges.taskVar.signal.map(_.labelIds).distinct.changes --> onLabelsChanged,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Create a task", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              form(
                onSubmit.preventDefault
                  .mapTo(formState.now() -> linksVar.now())
                  .compose(_.withCurrentValueOf(badges.taskVar.signal)) --> submitter,
                nameInput.htmlElement.amend(cls("mb-4 max-w-[60ch]")),
                div(
                  cls("mb-4 flex flex-col gap-4"),
                  badges.element,
                  MilestoneSummaryRangeCalendar.element(
                    badges.taskVar.signal.map(task => task.startDate.toString -> task.dueDate.toString).distinct,
badges.workDaysSignal,
                    (start, due) =>
                      badges.taskVar.update(_.copy(startDate = start, dueDate = due, dueDateManual = true))
                  )
                ),
                linksSection.element.amend(cls("mb-4")),
                div(
                  cls("mb-4"),
                  child <-- TaskTemplatePicker(templatesVar.signal, selectedTemplateVar.signal, onTemplatePicked)
                ),
                descriptionEditor.htmlElement.amend(cls("mb-4")),
                div(cls("flex justify-end gap-2"), TgHtmlButton.createSmall(EventStream.fromValue(false)))
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
//        .amend(heightAndWidthFitContent)
    )

}

class TaskFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val taskEndpoints = new TaskEndpoints(securedEndpoint)
  val logEntryEndpoints = new LogEntryEndpoints(securedEndpoint)
  val linkEndpoints = new LinkEndpoints(securedEndpoint)

  def createTask(dto: CreateTaskAndLinksReq): TgRespIO[FindTaskResp] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.saveEndpoint)(dto)

  def createTasks(dto: CreateTasksBulkReq): TgRespIO[Seq[FindTaskResp]] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.saveBulkEndpoint)(dto)

  def updateTask(dto: UpdateTaskReq): TgRespIO[FindTaskResp] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.updateEndpoint)(dto)

  def deleteTask(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.deleteEndpoint)(id)

  def findByProjectId(
    projectId: UUID,
    filter: TaskFilter,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindTaskRespFull]] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.findByProjectIdEndpoint)((projectId, filter, page))

  /** Every task of the project, for aggregates over the whole project; see [[PaginatedLoader]] for the bound. */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[FindTaskRespFull]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, TaskFilter.empty, _))

  /** Every task planned in the latest version of the timebox, in its current state; see [[PaginatedLoader]] for the
    * bound.
    */
  def findAllByTimebox(projectId: UUID, timeboxId: UUID): TgRespIO[Seq[FindTaskRespFull]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, TaskFilter(timeboxId = Some(timeboxId)), _))

  /** Every task of the milestone; see [[PaginatedLoader]] for the bound. */
  def findAllByMilestone(projectId: UUID, milestoneId: UUID): TgRespIO[Seq[FindTaskRespFull]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, TaskFilter(milestoneIds = List(milestoneId)), _))

  def findLogEntriesByProjectId(
    projectId: UUID,
    filter: LogEntryFilter,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[LogEntryDto]] =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.findByProjectIdEndpoint)((projectId, filter, page))

  /** Every log entry of the project in the date range, for calendars and charts over a window; see [[PaginatedLoader]]
    * for the bound.
    */
  def findAllLogEntriesByProjectId(
    projectId: UUID,
    filter: LogEntryFilter = LogEntryFilter.empty
  ): TgRespIO[Seq[LogEntryDto]] =
    PaginatedLoader.loadAll(findLogEntriesByProjectId(projectId, filter, _))

  def findLogEntriesByTaskId(taskId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[LogEntryDto]] =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.findByTaskId)((taskId, page))

  /** Every log entry of the task; see [[PaginatedLoader]] for the bound. */
  def findAllLogEntriesByTaskId(taskId: UUID): TgRespIO[Seq[LogEntryDto]] =
    PaginatedLoader.loadAll(findLogEntriesByTaskId(taskId, _))

  def findTaskById(id: UUID): TgRespIO[FindTaskResp] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.findByIdEndpoint)(id)

  def saveLogEntry(dto: LogEntryDto) =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.saveEndpoint)(dto)

  def updateLogEntry(dto: LogEntryDto) =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.updateEndpoint)(dto)

  def listLogEntryVersions(logEntryId: UUID, page: PageRequest) =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.listVersionsEndpoint)((logEntryId, page))

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

  def listTaskVersions(taskId: UUID, page: PageRequest) =
    protectedBackendClient.protectedHttpCall(taskEndpoints.listVersionsEndpoint)((taskId, page))

  def updateTaskVersion(dto: TaskVersionDto) =
    protectedBackendClient.protectedHttpCall(taskEndpoints.updateVersionEndpoint)(dto)

  def deleteTaskVersion(version: UUID) =
    protectedBackendClient.protectedHttpCall(taskEndpoints.deleteVersionEndpoint)(version)

}
