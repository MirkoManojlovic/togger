package com.togger.ui.task

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.logentry.LogEntryEndpoints
import com.togger.task.{LinkEndpoints, TaskEndpoints}
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait TaskFeService {
  def createTask(dto: CreateTaskAndLinksReq): TgRespIO[FindTaskResp]

  def createTasks(dto: CreateTasksBulkReq): TgRespIO[Seq[FindTaskResp]]

  def updateTask(dto: UpdateTaskReq): TgRespIO[FindTaskResp]

  def deleteTask(id: UUID): TgRespIO[TgEmptyBody]

  def findByProjectId(
    projectId: UUID,
    filter: TaskFilter,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindTaskRespFull]]

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
  ): TgRespIO[PaginatedResponse[LogEntryDto]]

  /** Every log entry of the project in the date range, for calendars and charts over a window; see [[PaginatedLoader]]
    * for the bound.
    */
  def findAllLogEntriesByProjectId(
    projectId: UUID,
    filter: LogEntryFilter = LogEntryFilter.empty
  ): TgRespIO[Seq[LogEntryDto]] =
    PaginatedLoader.loadAll(findLogEntriesByProjectId(projectId, filter, _))

  def findLogEntriesByTaskId(taskId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[LogEntryDto]]

  /** Every log entry of the task; see [[PaginatedLoader]] for the bound. */
  def findAllLogEntriesByTaskId(taskId: UUID): TgRespIO[Seq[LogEntryDto]] =
    PaginatedLoader.loadAll(findLogEntriesByTaskId(taskId, _))

  def findTaskById(id: UUID): TgRespIO[FindTaskResp]

  def saveLogEntry(dto: LogEntryDto): TgRespIO[LogEntryDto]

  def updateLogEntry(dto: LogEntryDto): TgRespIO[LogEntryDto]

  def listLogEntryVersions(logEntryId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[LogEntryVersionDto]]

  def updateLogEntryVersion(dto: LogEntryVersionDto): TgRespIO[LogEntryVersionDto]

  def deleteLogEntryVersion(version: UUID): TgRespIO[TgEmptyBody]

  def listLinkVersions(linkId: UUID): TgRespIO[List[LinkVersionDto]]

  def updateLinkVersion(dto: LinkVersionDto): TgRespIO[LinkVersionDto]

  def deleteLinkVersion(version: UUID): TgRespIO[TgEmptyBody]

  def listTaskVersions(taskId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TaskVersionDto]]

  def updateTaskVersion(dto: TaskVersionDto): TgRespIO[TaskVersionDto]

  def deleteTaskVersion(version: UUID): TgRespIO[TgEmptyBody]
}

class TaskFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends TaskFeService {
  val taskEndpoints = new TaskEndpoints(securedEndpoint)
  val logEntryEndpoints = new LogEntryEndpoints(securedEndpoint)
  val linkEndpoints = new LinkEndpoints(securedEndpoint)

  override def createTask(dto: CreateTaskAndLinksReq): TgRespIO[FindTaskResp] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.saveEndpoint)(dto)

  override def createTasks(dto: CreateTasksBulkReq): TgRespIO[Seq[FindTaskResp]] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.saveBulkEndpoint)(dto)

  override def updateTask(dto: UpdateTaskReq): TgRespIO[FindTaskResp] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.updateEndpoint)(dto)

  override def deleteTask(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.deleteEndpoint)(id)

  override def findByProjectId(
    projectId: UUID,
    filter: TaskFilter,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindTaskRespFull]] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.findByProjectIdEndpoint)((projectId, filter, page))

  override def findLogEntriesByProjectId(
    projectId: UUID,
    filter: LogEntryFilter,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[LogEntryDto]] =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.findByProjectIdEndpoint)((projectId, filter, page))

  override def findLogEntriesByTaskId(taskId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[LogEntryDto]] =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.findByTaskId)((taskId, page))

  override def findTaskById(id: UUID): TgRespIO[FindTaskResp] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.findByIdEndpoint)(id)

  override def saveLogEntry(dto: LogEntryDto): TgRespIO[LogEntryDto] =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.saveEndpoint)(dto)

  override def updateLogEntry(dto: LogEntryDto): TgRespIO[LogEntryDto] =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.updateEndpoint)(dto)

  override def listLogEntryVersions(
    logEntryId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[LogEntryVersionDto]] =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.listVersionsEndpoint)((logEntryId, page))

  override def updateLogEntryVersion(dto: LogEntryVersionDto): TgRespIO[LogEntryVersionDto] =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.updateVersionEndpoint)(dto)

  override def deleteLogEntryVersion(version: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(logEntryEndpoints.deleteVersionEndpoint)(version)

  override def listLinkVersions(linkId: UUID): TgRespIO[List[LinkVersionDto]] =
    protectedBackendClient.protectedHttpCall(linkEndpoints.listVersionsEndpoint)(linkId)

  override def updateLinkVersion(dto: LinkVersionDto): TgRespIO[LinkVersionDto] =
    protectedBackendClient.protectedHttpCall(linkEndpoints.updateVersionEndpoint)(dto)

  override def deleteLinkVersion(version: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(linkEndpoints.deleteVersionEndpoint)(version)

  override def listTaskVersions(taskId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TaskVersionDto]] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.listVersionsEndpoint)((taskId, page))

  override def updateTaskVersion(dto: TaskVersionDto): TgRespIO[TaskVersionDto] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.updateVersionEndpoint)(dto)

  override def deleteTaskVersion(version: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(taskEndpoints.deleteVersionEndpoint)(version)
}
