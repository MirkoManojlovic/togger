package com.togger.ui.task

import cats.effect.IO
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{DateRangeValidation, PageRequest, PaginatedResponse, TgError, TgIds}
import com.togger.dto._
import com.togger.preview._
import com.togger.task.{BulkTaskValidation, DefaultTaskStatus, LinkValidation}

import java.time.{DayOfWeek, Instant}
import java.util.UUID

class TaskFeServicePreview(
  ctx: PreviewContext,
  projects: ProjectsPreviewData,
  taskConfig: TaskConfigPreviewData,
  data: TasksPreviewData,
  dateRangeValidation: DateRangeValidation = new DateRangeValidation
) extends TaskFeService {

  private val TaskNotFound = "Task not found"
  private val VersionNotFound = "Task version not found"
  private val CannotEditLatest = "Cannot edit the latest version directly, save a new version instead"

  private def secured[A](response: UUID => TgRespIO[A]): TgRespIO[A] =
    PreviewResponses.authenticated(ctx.loginRepo)(accountId => IO.defer(response(accountId)))

  override def createTask(dto: CreateTaskAndLinksReq): TgRespIO[FindTaskResp] =
    secured { accountId =>
      val task = dto.task
      projects.projects.find(task.projectId) match {
        case None => PreviewResponses.notFound[FindTaskResp]
        case Some(project) =>
          dateRangeValidation
            .validate(Some(task.startDate), Some(task.dueDate), None, workDays(project))
            .flatMap(_ => LinkValidation.validateAll(dto.links, Seq.empty)) match {
            case Left(errors) => errors.toBadRequestLeftIO[FindTaskResp]
            case Right(_) =>
              checkStatus(task.projectId, task.taskStatusId).fold(PreviewResponses.badRequest[FindTaskResp](_), _ => {
                val (created, links) = insertTask(accountId, dto, task.createdAt, ctx.clock.currentInstant)
                PreviewResponses.ok(savedDto(created, links))
              })
          }
      }
    }

  override def createTasks(dto: CreateTasksBulkReq): TgRespIO[Seq[FindTaskResp]] =
    secured { accountId =>
      BulkTaskValidation.validate(dto) match {
        case Left(errors) => errors.toBadRequestLeftIO[Seq[FindTaskResp]]
        case Right(_) =>
          val projectId = dto.tasks.head.task.projectId
          projects.projects.find(projectId) match {
            case None => PreviewResponses.notFound[Seq[FindTaskResp]]
            case Some(project) =>
              BulkTaskValidation
                .validateDates(dto, workDays(project), dateRangeValidation)
                .flatMap(_ => BulkTaskValidation.validateLinks(dto)) match {
                case Left(errors) => errors.toBadRequestLeftIO[Seq[FindTaskResp]]
                case Right(_) =>
                  dto.tasks.map(_.task.taskStatusId).distinct.flatMap(checkStatus(projectId, _).left.toOption).headOption match {
                    case Some(error) => PreviewResponses.badRequest[Seq[FindTaskResp]](error)
                    case None =>
                      val now = ctx.clock.currentInstant
                      val created = dto.tasks.zipWithIndex.map { case (taskAndLinks, index) =>
                        val (task, links) =
                          insertTask(accountId, taskAndLinks, now.plusMillis(index.toLong), now)
                        savedDto(task, links)
                      }
                      PreviewResponses.ok(created)
                  }
              }
          }
      }
    }

  override def updateTask(dto: UpdateTaskReq): TgRespIO[FindTaskResp] =
    secured { accountId =>
      val update = dto.task
      data.tasks.find(update.id).zip(data.latestTaskVersion(update.id)) match {
        case None => PreviewResponses.notFound[FindTaskResp]
        case Some((task, current)) =>
          val startDate = update.startDate.getOrElse(current.startDate)
          val dueDate = update.dueDate.getOrElse(current.dueDate)
          val endDate = update.endDate.orElse(current.endDate)
          val linksToCreate = LinkValidation.withoutBlankLinks(dto.linksToCreate)
          val workDaysOfProject = projects.projects.find(task.projectId).fold(allDays)(workDays)
          dateRangeValidation
            .validate(Some(startDate), Some(dueDate), endDate, workDaysOfProject)
            .flatMap(_ => LinkValidation.validateAll(linksToCreate, dto.linksForUpdate)) match {
            case Left(errors) => errors.toBadRequestLeftIO[FindTaskResp]
            case Right(_) =>
              checkStatus(task.projectId, update.taskStatusId).fold(
                PreviewResponses.badRequest[FindTaskResp](_),
                _ => {
                  val now = ctx.clock.currentInstant
                  val candidate = current.copy(
                    version = UUID.randomUUID(),
                    name = update.name,
                    description = update.description,
                    assignedTo = update.assignedTo,
                    closedBy = closedByFor(update.taskStatusId, current.closedBy, accountId),
                    taskStatusId = update.taskStatusId,
                    priority = update.priority,
                    milestone = update.milestone,
                    startDate = startDate,
                    dueDate = dueDate,
                    endDate = endDate,
                    createdAt = now,
                    updatedAt = now
                  )
                  val saved = if (sameContent(current, candidate)) current else data.taskVersions.upsert(candidate)
                  val labelIds = existingLabelIds(task.projectId, update.taskLabelIds)
                  val storedTask = data.tasks.upsert(task.copy(archived = update.archived, labelIds = labelIds))
                  val updatedLinks = dto.linksForUpdate.flatMap(updateLink(task.id, _, now))
                  val createdLinks = linksToCreate.map(createLink(task.id, _, now))
                  deleteOwnLinks(task.id, dto.linksToDelete)
                  PreviewResponses.ok(
                    FindTaskResp(toDto(storedTask, saved), (updatedLinks ++ createdLinks).map(toLinkDto), Seq.empty)
                  )
                }
              )
          }
      }
    }

  override def deleteTask(id: UUID): TgRespIO[TgEmptyBody] =
    secured { _ =>
      if (data.tasks.find(id).isEmpty) PreviewResponses.badRequest[TgEmptyBody](TaskNotFound)
      else {
        data.deleteTask(id)
        PreviewResponses.okEmpty("Task deleted successfully")
      }
    }

  override def findByProjectId(
    projectId: UUID,
    filter: TaskFilter,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindTaskRespFull]] =
    secured { _ =>
      val timeboxTaskIds = filter.timeboxId.map(plannedTaskIds)
      val matching = data.tasks
        .filter(_.projectId == projectId)
        .filter(task => filter.includeArchived || !task.archived)
        .flatMap(task => data.latestTaskVersion(task.id).map(task -> _))
        .filter { case (task, version) => matches(filter, timeboxTaskIds, task, version) }
        .sortBy { case (task, _) => (-task.createdAt.toEpochMilli, task.id.toString) }
      val paged = PreviewPaging.page(matching, page)
      PreviewResponses.ok(paged.copy(items = paged.items.flatMap { case (task, version) => toFull(task, version) }))
    }

  override def findLogEntriesByProjectId(
    projectId: UUID,
    filter: LogEntryFilter,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[LogEntryDto]] =
    secured { _ =>
      val taskIds = data.tasks.filter(_.projectId == projectId).map(_.id).toSet
      val matching = data.logEntries
        .filter(entry => taskIds.contains(entry.taskId))
        .filter(entry => filter.from.forall(!entry.createdAt.isBefore(_)) && filter.to.forall(entry.createdAt.isBefore))
        .sortBy(entry => (-entry.createdAt.toEpochMilli, entry.id.toString))
      PreviewResponses.paged(matching.flatMap(toLogEntryDto), page)
    }

  override def findLogEntriesByTaskId(taskId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[LogEntryDto]] =
    secured { _ =>
      val entries = data.logEntriesOf(taskId).sortBy(entry => (-entry.createdAt.toEpochMilli, entry.id.toString))
      PreviewResponses.paged(entries.flatMap(toLogEntryDto), page)
    }

  override def findTaskById(id: UUID): TgRespIO[FindTaskResp] =
    secured { _ =>
      data.tasks.find(id).zip(data.latestTaskVersion(id)) match {
        case None => PreviewResponses.notFound[FindTaskResp]
        case Some((task, version)) =>
          PreviewResponses.ok(
            FindTaskResp(
              toDto(task, version),
              data.linksOf(id).flatMap(link => data.latestLinkVersion(link.id).map(toLinkDto(link, _))),
              data.logEntriesOf(id).sortBy(entry => (-entry.createdAt.toEpochMilli, entry.id.toString)).flatMap(toLogEntryDto)
            )
          )
      }
    }

  override def saveLogEntry(dto: LogEntryDto): TgRespIO[LogEntryDto] =
    secured { _ =>
      if (data.tasks.find(dto.taskId).isEmpty) PreviewResponses.notFound[LogEntryDto]
      else if (data.logEntriesOf(dto.taskId).exists(entry => data.dayOf(entry.createdAt) == data.dayOf(dto.createdAt)))
        PreviewResponses.badRequest[LogEntryDto]("The task already has a log entry for that day")
      else {
        val entry = data.logEntries.upsert(PreviewLogEntry(UUID.randomUUID(), dto.taskId, dto.createdBy, dto.createdAt))
        val version = data.logEntryVersions.upsert(newLogEntryVersion(entry.id, dto))
        PreviewResponses.ok(logEntryDto(entry, version))
      }
    }

  override def updateLogEntry(dto: LogEntryDto): TgRespIO[LogEntryDto] =
    secured { _ =>
      data.logEntries.find(dto.id).fold(PreviewResponses.badRequest[LogEntryDto]("Log entry not found")) { entry =>
        val version = data.logEntryVersions.upsert(newLogEntryVersion(entry.id, dto))
        PreviewResponses.ok(logEntryDto(entry, version))
      }
    }

  override def listLogEntryVersions(
    logEntryId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[LogEntryVersionDto]] =
    secured(_ => PreviewResponses.paged(data.versionsOfLogEntry(logEntryId).reverse, page))

  override def updateLogEntryVersion(dto: LogEntryVersionDto): TgRespIO[LogEntryVersionDto] =
    secured { _ =>
      data.logEntryVersions.find(dto.version) match {
        case None => PreviewResponses.badRequest[LogEntryVersionDto]("Log entry version not found")
        case Some(existing) if data.latestLogEntryVersion(existing.logEntryId).exists(_.version == existing.version) =>
          PreviewResponses.badRequest[LogEntryVersionDto](CannotEditLatest)
        case Some(existing) =>
          PreviewResponses.ok(
            data.logEntryVersions
              .upsert(existing.copy(textInternal = dto.textInternal, textExternal = dto.textExternal, status = dto.status))
          )
      }
    }

  override def deleteLogEntryVersion(version: UUID): TgRespIO[TgEmptyBody] =
    secured { _ =>
      data.logEntryVersions.find(version) match {
        case None => PreviewResponses.badRequest[TgEmptyBody]("Log entry version not found")
        case Some(existing) =>
          val isLast = data.versionsOfLogEntry(existing.logEntryId).size <= 1
          if (isLast && data.latestLogEntryVersion(existing.logEntryId).exists(_.version == version))
            PreviewResponses.badRequest[TgEmptyBody]("Cannot delete the only remaining version of a log entry")
          else {
            data.logEntryVersions.delete(version)
            PreviewResponses.okEmpty("Log entry version deleted successfully")
          }
      }
    }

  override def listLinkVersions(linkId: UUID): TgRespIO[List[LinkVersionDto]] =
    secured(_ => PreviewResponses.ok(data.linkVersions.filter(_.linkId == linkId).reverse))

  override def updateLinkVersion(dto: LinkVersionDto): TgRespIO[LinkVersionDto] =
    secured { _ =>
      data.linkVersions.find(dto.version) match {
        case None => PreviewResponses.badRequest[LinkVersionDto]("Link version not found")
        case Some(existing) if data.latestLinkVersion(existing.linkId).exists(_.version == existing.version) =>
          PreviewResponses.badRequest[LinkVersionDto](CannotEditLatest)
        case Some(existing) =>
          PreviewResponses.ok(data.linkVersions.upsert(existing.copy(name = dto.name, url = dto.url)))
      }
    }

  override def deleteLinkVersion(version: UUID): TgRespIO[TgEmptyBody] =
    secured { _ =>
      data.linkVersions.find(version) match {
        case None => PreviewResponses.badRequest[TgEmptyBody]("Link version not found")
        case Some(existing) =>
          val onlyOne = data.linkVersions.filter(_.linkId == existing.linkId).size <= 1
          if (onlyOne) PreviewResponses.badRequest[TgEmptyBody]("Cannot delete the only remaining version of a link")
          else {
            data.linkVersions.delete(version)
            PreviewResponses.okEmpty("Link version deleted successfully")
          }
      }
    }

  override def listTaskVersions(taskId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TaskVersionDto]] =
    secured(_ => PreviewResponses.paged(data.versionsOfTask(taskId).reverse, page))

  override def updateTaskVersion(dto: TaskVersionDto): TgRespIO[TaskVersionDto] =
    secured { _ =>
      data.taskVersions.find(dto.version) match {
        case None => PreviewResponses.badRequest[TaskVersionDto](VersionNotFound)
        case Some(existing) =>
          val task = data.tasks.find(existing.taskId)
          val status = taskConfig.statuses.find(dto.taskStatusId)
          val validation = for {
            _ <- dateRangeValidation.validate(
              Some(dto.startDate),
              Some(dto.dueDate),
              dto.endDate,
              task.flatMap(t => projects.projects.find(t.projectId)).fold(allDays)(workDays)
            )
            _ <- dateRangeValidation.validateEndDateWhenDone(
              status.exists(_.name == DefaultTaskStatus.Done.statusName),
              dto.endDate
            )
          } yield ()
          validation match {
            case Left(errors) => errors.toBadRequestLeftIO[TaskVersionDto]
            case Right(_) if data.latestTaskVersion(existing.taskId).exists(_.version == existing.version) =>
              PreviewResponses.badRequest[TaskVersionDto](CannotEditLatest)
            case Right(_) if status.isEmpty => PreviewResponses.badRequest[TaskVersionDto]("Task status not found")
            case Right(_) =>
              PreviewResponses.ok(
                data.taskVersions.upsert(
                  existing.copy(
                    name = dto.name,
                    description = dto.description,
                    assignedTo = dto.assignedTo,
                    closedBy = dto.closedBy,
                    taskStatusId = dto.taskStatusId,
                    priority = dto.priority,
                    milestone = dto.milestone,
                    startDate = dto.startDate,
                    dueDate = dto.dueDate,
                    endDate = dto.endDate
                  )
                )
              )
          }
      }
    }

  override def deleteTaskVersion(version: UUID): TgRespIO[TgEmptyBody] =
    secured { _ =>
      data.taskVersions.find(version) match {
        case None => PreviewResponses.badRequest[TgEmptyBody](VersionNotFound)
        case Some(existing) =>
          val isLatest = data.latestTaskVersion(existing.taskId).exists(_.version == version)
          if (isLatest && data.versionsOfTask(existing.taskId).size <= 1)
            PreviewResponses.badRequest[TgEmptyBody]("Cannot delete the only remaining version of a task")
          else {
            data.deleteTaskVersion(version)
            PreviewResponses.okEmpty("Task version deleted successfully")
          }
      }
    }

  private val allDays: Set[DayOfWeek] = DayOfWeek.values().toSet

  private def workDays(project: PreviewProject): Set[DayOfWeek] = project.workDays.map(DayOfWeek.of)

  private def checkStatus(projectId: UUID, statusId: UUID): Either[String, Unit] =
    Either.cond(taskConfig.statuses.find(statusId).exists(_.projectId == projectId), (), "Task status not found")

  private def existingLabelIds(projectId: UUID, labelIds: Seq[UUID]): List[UUID] =
    labelIds.distinct.filter(id => taskConfig.labels.find(id).exists(_.projectId == projectId)).toList

  private def closedByFor(statusId: UUID, previous: Option[UUID], accountId: UUID): Option[UUID] =
    if (taskConfig.statuses.find(statusId).exists(_.name == DefaultTaskStatus.Done.statusName)) previous.orElse(Some(accountId))
    else None

  private def sameContent(a: TaskVersionDto, b: TaskVersionDto): Boolean =
    a.copy(version = b.version, createdAt = b.createdAt, updatedAt = b.updatedAt) == b

  private def insertTask(
    accountId: UUID,
    dto: CreateTaskAndLinksReq,
    createdAt: Instant,
    linksAt: Instant
  ): (PreviewTask, List[(PreviewLink, LinkVersionDto)]) = {
    val req = dto.task
    val task = data.tasks.upsert(
      PreviewTask(
        UUID.randomUUID(),
        req.projectId,
        accountId,
        createdAt,
        archived = false,
        existingLabelIds(req.projectId, req.taskLabelIds)
      )
    )
    data.taskVersions.upsert(
      TaskVersionDto(
        UUID.randomUUID(),
        task.id,
        req.name,
        req.description,
        req.assignedTo,
        None,
        req.taskStatusId,
        createdAt,
        createdAt,
        req.priority,
        req.milestone,
        req.startDate,
        req.dueDate,
        None
      )
    )
    task -> LinkValidation.withoutBlankLinks(dto.links).map(createLink(task.id, _, linksAt)).toList
  }

  private def createLink(taskId: UUID, req: CreateLinkReq, now: Instant): (PreviewLink, LinkVersionDto) = {
    val link = data.links.upsert(PreviewLink(UUID.randomUUID(), taskId, now))
    link -> data.linkVersions.upsert(LinkVersionDto(UUID.randomUUID(), link.id, req.name, req.url, now, now))
  }

  private def updateLink(taskId: UUID, req: UpdateLinkReq, now: Instant): Option[(PreviewLink, LinkVersionDto)] =
    data.links.find(req.id).filter(_.taskId == taskId).flatMap { link =>
      data.latestLinkVersion(link.id).map { current =>
        val changed = current.name != req.name || current.url != req.url
        link -> (if (changed) data.linkVersions.upsert(LinkVersionDto(UUID.randomUUID(), link.id, req.name, req.url, now, now)) else current)
      }
    }

  private def deleteOwnLinks(taskId: UUID, linkIds: Seq[UUID]): Unit =
    data.linksOf(taskId).map(_.id).filter(linkIds.contains).foreach { linkId =>
      data.linkVersions.deleteWhere(_.linkId == linkId)
      data.links.delete(linkId)
    }

  private def savedDto(task: PreviewTask, links: List[(PreviewLink, LinkVersionDto)]): FindTaskResp =
    FindTaskResp(toDto(task, data.latestTaskVersion(task.id).get), links.map(toLinkDto), Seq.empty)

  private def toDto(task: PreviewTask, version: TaskVersionDto): FindTaskResp1 =
    FindTaskResp1(
      task.id,
      task.projectId,
      version.name,
      version.description,
      version.assignedTo,
      task.createdBy,
      version.taskStatusId,
      task.createdAt,
      version.updatedAt,
      version.version,
      version.priority,
      version.milestone,
      version.startDate,
      version.dueDate,
      version.endDate,
      task.labelIds,
      task.archived
    )

  private def toLinkDto(link: (PreviewLink, LinkVersionDto)): FindLinkResp = toLinkDto(link._1, link._2)

  private def toLinkDto(link: PreviewLink, version: LinkVersionDto): FindLinkResp =
    FindLinkResp(link.id, link.taskId, link.createdAt, version.version, version.name, version.url, version.updatedAt)

  private def newLogEntryVersion(entryId: UUID, dto: LogEntryDto): LogEntryVersionDto =
    LogEntryVersionDto(
      UUID.randomUUID(),
      entryId,
      dto.textInternal,
      dto.textExternal,
      dto.status,
      dto.createdBy,
      dto.createdAt,
      dto.updatedAt
    )

  private def logEntryDto(entry: PreviewLogEntry, version: LogEntryVersionDto): LogEntryDto =
    LogEntryDto(
      entry.id,
      entry.taskId,
      entry.createdBy,
      version.createdAt,
      version.version,
      version.textInternal,
      version.textExternal,
      version.status,
      version.updatedAt,
      data.accountName(entry.createdBy)
    )

  private def toLogEntryDto(entry: PreviewLogEntry): Option[LogEntryDto] =
    data.latestLogEntryVersion(entry.id).map(logEntryDto(entry, _))

  private def toFull(task: PreviewTask, version: TaskVersionDto): Option[FindTaskRespFull] =
    taskConfig.statuses.find(version.taskStatusId).map { status =>
      FindTaskRespFull(
        id = task.id,
        projectId = task.projectId,
        name = version.name,
        description = version.description,
        assignedTo = version.assignedTo.map(data.searchAccount),
        createdBy = data.searchAccount(task.createdBy),
        closedBy = version.closedBy.map(data.searchAccount),
        taskStatus = status,
        priority = version.priority,
        createdAt = task.createdAt,
        updatedAt = version.updatedAt,
        latestVersion = version.version,
        logEntries = data.logEntriesOf(task.id).sortBy(entry => (-entry.createdAt.toEpochMilli, entry.id.toString)).flatMap(toLogEntryDto),
        links = data.linksOf(task.id).flatMap(link => data.latestLinkVersion(link.id).map(toLinkDto(link, _))),
        milestone = version.milestone,
        startDate = version.startDate,
        dueDate = version.dueDate,
        endDate = version.endDate,
        taskLabels = task.labelIds.flatMap(taskConfig.labels.find).sortBy(label => (label.name, label.id.toString)),
        archived = task.archived
      )
    }

  private def plannedTaskIds(timeboxId: UUID): Set[UUID] =
    data
      .latestTimeboxVersion(timeboxId)
      .fold(Set.empty[UUID])(_.taskVersionIds.flatMap(data.taskVersions.find).map(_.taskId).toSet)

  private def matches(
    filter: TaskFilter,
    timeboxTaskIds: Option[Set[UUID]],
    task: PreviewTask,
    version: TaskVersionDto
  ): Boolean = {
    val search = filter.search.map(_.trim.toLowerCase).filter(_.nonEmpty)
    (filter.statusIds.isEmpty || filter.statusIds.contains(version.taskStatusId)) &&
    (filter.priorities.isEmpty || filter.priorities.contains(version.priority)) &&
    (filter.labelIds.isEmpty || task.labelIds.exists(filter.labelIds.contains)) &&
    inOrNone(filter.assigneeIds, version.assignedTo, TgIds.UNASSIGNED_UUID) &&
    inOrNone(filter.milestoneIds, version.milestone, TgIds.NO_MILESTONE_UUID) &&
    search.forall(term => version.name.toLowerCase.contains(term) || version.description.toLowerCase.contains(term)) &&
    timeboxTaskIds.forall(_.contains(task.id))
  }

  private def inOrNone(wanted: List[UUID], actual: Option[UUID], noneMarker: UUID): Boolean =
    wanted.isEmpty || actual.fold(wanted.contains(noneMarker))(wanted.contains)
}
