package com.togger.controllers.task

import cats.effect.IO
import com.togger.account.{Account, AccountNameLookup}
import com.togger.common.TgError._
import com.togger.common.{DateRangeValidation, TgClock, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.logentry.LogEntryDtoMapper.toDto
import com.togger.controllers.task.LinkDtoMapper.{toDto => toLinkDto}
import com.togger.controllers.task.TaskDtoMapper.{toDto => toTaskDto, toVersionDto}
import com.togger.controllers.project.ProjectService
import com.togger.dto._
import com.togger.logentry.LogEntryWithVersions
import com.togger.task._

import java.time.Instant
import java.util.UUID

class TaskController(
  taskService: TaskService,
  taskVersionService: TaskVersionService,
  taskEndpoints: TaskEndpoints,
  tgClock: TgClock,
  dateRangeValidation: DateRangeValidation,
  projectService: ProjectService,
  accountNameLookup: AccountNameLookup
) {

  import taskEndpoints._

  val findById =
    findByIdEndpoint.serverLogic { auth => taskId =>
      taskService
        .findById(taskId)
        .flatMap { case (taskWithVersions, links, logEntries, taskLabels) =>
          namesOf(logEntries).map(names =>
            TgJsonSuccess.justBodyRight(
              FindTaskResp(
                task = toTaskDto(taskWithVersions.task, taskWithVersions.latestVersion, taskLabels.map(_.id)),
                links = links.map(link => toLinkDto(link.link, link.latestVersion)),
                logEntries = logEntries.map(entry => toDto(entry.logEntry, entry.latestVersion, names))
              )
            )
          )
        }
    }

  val findByProjectId =
    findByProjectIdEndpoint.serverLogic { auth => request =>
      val (projectId, filter, pageRequest) = request
      PageRequestValidation.withPage(pageRequest) { page =>
        taskService
          .findPaginatedByProjectId(projectId, filter, page)
          .flatMap(tasksPage =>
            namesOf(tasksPage.items.flatMap(_._3)).map(names =>
              TgJsonSuccess.justBodyRight(tasksPage.mapItem(toFullDto(_, names)))
            )
          )
      }
    }

  private def namesOf(logEntries: List[LogEntryWithVersions]): IO[Map[UUID, String]] =
    accountNameLookup.namesByIds(logEntries.map(_.logEntry.createdBy).toSet)

  private def toFullDto(
    item: (TaskFull, List[LinkWithVersions], List[LogEntryWithVersions]),
    names: Map[UUID, String]
  ): FindTaskRespFull = {
    val (taskFull, links, logEntries) = item
    FindTaskRespFull(
      id = taskFull.id,
      projectId = taskFull.projectId,
      name = taskFull.name,
      description = taskFull.description,
      assignedTo = taskFull.assignedTo.map(fromAccount),
      createdBy = fromAccount(taskFull.createdBy),
      closedBy = taskFull.closedBy.map(fromAccount),
      taskStatus = FindTaskStatusResp(
        id = taskFull.taskStatus.id,
        projectId = taskFull.projectId,
        name = taskFull.taskStatus.name,
        description = taskFull.taskStatus.description
      ),
      priority = taskFull.priority,
      createdAt = taskFull.createdAt,
      updatedAt = taskFull.updatedAt,
      latestVersion = taskFull.latestVersion,
      logEntries = logEntries.map(entry => toDto(entry.logEntry, entry.versions.head, names)),
      links = links.map(link => toLinkDto(link.link, link.latestVersion)),
      milestone = taskFull.milestone,
      startDate = taskFull.startDate,
      dueDate = taskFull.dueDate,
      endDate = taskFull.endDate,
      taskLabels = taskFull.taskLabels.map(l =>
        FindTaskLabelResp(id = l.id, projectId = l.projectId, name = l.name, description = l.description)
      )
    )
  }

  def fromAccount(account: Account): SearchAccountDto =
    SearchAccountDto(
      id = account.id,
      firstName = account.firstName,
      lastName = account.lastName,
      email = account.email,
      accountTypeId = account.accountTypeId
    )

  private def newTaskOf(taskDto: CreateTaskReq, createdBy: UUID, createdAt: Instant): (Task, TaskVersion) = {
    val task = Task(id = UUID.randomUUID(), projectId = taskDto.projectId, createdBy = createdBy, createdAt = createdAt)
    val firstVersion = TaskVersion(
      version = UUID.randomUUID(),
      taskId = task.id,
      name = taskDto.name,
      description = taskDto.description,
      assignedTo = taskDto.assignedTo,
      closedBy = None,
      taskStatusId = taskDto.taskStatusId,
      priority = taskDto.priority,
      milestone = taskDto.milestone,
      startDate = taskDto.startDate,
      dueDate = taskDto.dueDate,
      endDate = None,
      createdAt = createdAt,
      updatedAt = createdAt
    )
    (task, firstVersion)
  }

  private def newLinksOf(linksDto: Seq[CreateLinkReq], now: Instant): Seq[LinkWithOneVersion] =
    linksDto.map { linkDto =>
      val linkId = UUID.randomUUID()
      LinkWithOneVersion(
        link = Link(id = linkId, taskId = UUID.randomUUID(), createdAt = now),
        version = LinkVersion(
          version = UUID.randomUUID(),
          linkId = linkId,
          name = linkDto.name,
          url = linkDto.url,
          createdAt = now,
          updatedAt = now
        )
      )
    }

  private def savedDtoOf(
    saved: (TaskWithOneVersion, List[LinkWithOneVersion]),
    labelIds: Seq[UUID]
  ): FindTaskResp = {
    val (savedTask, savedLinks) = saved
    FindTaskResp(
      task = toTaskDto(savedTask.task, savedTask.version, labelIds),
      links = savedLinks.map(link => toLinkDto(link.link, link.version)),
      logEntries = Seq.empty
    )
  }

  val save = saveEndpoint.serverLogic { auth => dto =>
    val taskDto = dto.task

    projectService.findById(taskDto.projectId).flatMap { case (project, _, _) =>
      dateRangeValidation.validate(Some(taskDto.startDate), Some(taskDto.dueDate), None, project.workDays) match {
        case Left(errors) => IO.pure(errors.toBadRequestLeft[FindTaskResp])
        case Right(_) =>
          val (task, firstVersion) = newTaskOf(taskDto, auth.body.get.id, taskDto.createdAt)
          val newLinks = newLinksOf(dto.links, tgClock.currentInstant)

          taskService
            .createTask(task, firstVersion, newLinks, taskDto.taskLabelIds)
            .map(saved => TgJsonSuccess.justBodyRight(savedDtoOf(saved, taskDto.taskLabelIds)))
      }
    }
  }

  /** Creates the tasks in one transaction, in list order. Each gets a creation time a millisecond after the one before,
    * so the order of the list is the order in which the tasks were created.
    */
  val saveBulk = saveBulkEndpoint.serverLogic { auth => dto =>
    BulkTaskValidation.validate(dto) match {
      case Left(errors) => IO.pure(errors.toBadRequestLeft[Seq[FindTaskResp]])
      case Right(_) =>
        projectService.findById(dto.tasks.head.task.projectId).flatMap { case (project, _, _) =>
          BulkTaskValidation.validateDates(dto, project.workDays, dateRangeValidation) match {
            case Left(errors) => IO.pure(errors.toBadRequestLeft[Seq[FindTaskResp]])
            case Right(_) =>
              val now = tgClock.currentInstant
              val newTasks = dto.tasks.zipWithIndex.map { case (taskAndLinks, index) =>
                val (task, firstVersion) =
                  newTaskOf(taskAndLinks.task, auth.body.get.id, now.plusMillis(index.toLong))
                NewTask(task, firstVersion, newLinksOf(taskAndLinks.links, now), taskAndLinks.task.taskLabelIds)
              }

              taskService.createTasks(newTasks).map { saved =>
                TgJsonSuccess.justBodyRight(saved.zip(dto.tasks).map { case (savedTask, taskAndLinks) =>
                  savedDtoOf(savedTask, taskAndLinks.task.taskLabelIds)
                })
              }
          }
        }
    }
  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    val taskDto = dto.task
    val linksForUpdate = dto.linksForUpdate
    val linksToCreate = dto.linksToCreate

    taskService.findProjectId(taskDto.id).flatMap { projectId =>
      projectService.findById(projectId).flatMap { case (project, _, _) =>
        taskVersionService.findLatest(taskDto.id).flatMap { currentVersion =>
          val effectiveStartDate = taskDto.startDate.getOrElse(currentVersion.startDate)
          val effectiveDueDate = taskDto.dueDate.getOrElse(currentVersion.dueDate)
          val effectiveEndDate = taskDto.endDate.orElse(currentVersion.endDate)

          dateRangeValidation.validate(
            Some(effectiveStartDate),
            Some(effectiveDueDate),
            effectiveEndDate,
            project.workDays
          ) match {
            case Left(errors) => IO.pure(errors.toBadRequestLeft[FindTaskResp])
            case Right(_) =>
              val newUpdatedAt = tgClock.currentInstant

              val newVersionContent = TaskVersion(
                version = UUID.randomUUID(),
                taskId = taskDto.id,
                name = taskDto.name,
                description = taskDto.description,
                assignedTo = taskDto.assignedTo,
                closedBy = None,
                taskStatusId = taskDto.taskStatusId,
                priority = taskDto.priority,
                milestone = taskDto.milestone,
                startDate = effectiveStartDate,
                dueDate = effectiveDueDate,
                endDate = effectiveEndDate,
                createdAt = newUpdatedAt,
                updatedAt = newUpdatedAt
              )

              val linkVersionUpdates =
                linksForUpdate.map(dto =>
                  LinkVersion(
                    version = UUID.randomUUID(),
                    linkId = dto.id,
                    name = dto.name,
                    url = dto.url,
                    createdAt = newUpdatedAt,
                    updatedAt = newUpdatedAt
                  )
                )

              val newLinks =
                linksToCreate.map { linkDto =>
                  val linkId = UUID.randomUUID()
                  LinkWithOneVersion(
                    link = Link(id = linkId, taskId = taskDto.id, createdAt = newUpdatedAt),
                    version = LinkVersion(
                      version = UUID.randomUUID(),
                      linkId = linkId,
                      name = linkDto.name,
                      url = linkDto.url,
                      createdAt = newUpdatedAt,
                      updatedAt = newUpdatedAt
                    )
                  )
                }

              taskService
                .updateTask(taskDto.id, newVersionContent, linkVersionUpdates, newLinks, taskDto.taskLabelIds)
                .map { case (savedTask, updatedLinks, savedLinks) =>
                  val savedDto = FindTaskResp(
                    task = toTaskDto(savedTask.task, savedTask.version, taskDto.taskLabelIds),
                    links = (updatedLinks ++ savedLinks).map(link => toLinkDto(link.link, link.version)),
                    logEntries = Seq.empty
                  )
                  TgJsonSuccess.justBodyRight(savedDto)
                }
          }
        }
      }
    }
  }

  val delete = deleteEndpoint.serverLogic { auth => taskId =>
    taskService
      .deleteTask(taskId)
      .map {
        case Right(_)    => TgJsonSuccess.justMessageRight("Task deleted successfully")
        case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
      }
  }

  val listVersions = listVersionsEndpoint.serverLogic { auth => request =>
    val (taskId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      taskVersionService
        .listVersions(taskId, page)
        .map(versionsPage => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto)))
    }
  }

  val updateVersion = updateVersionEndpoint.serverLogic { auth => dto =>
    taskService.findProjectIdByVersion(dto.version).flatMap { projectId =>
      projectService.findById(projectId).flatMap { case (project, _, _) =>
        dateRangeValidation.validate(Some(dto.startDate), Some(dto.dueDate), dto.endDate, project.workDays) match {
          case Left(errors) => IO.pure(errors.toBadRequestLeft[TaskVersionDto])
          case Right(_) =>
            taskVersionService
              .updateVersionContent(
                dto.version,
                dto.name,
                dto.description,
                dto.assignedTo,
                dto.closedBy,
                dto.taskStatusId,
                dto.priority,
                dto.milestone,
                dto.startDate,
                dto.dueDate,
                dto.endDate
              )
              .map {
                case Right(version) => TgJsonSuccess.justBodyRight(toVersionDto(version))
                case Left(error)    => error.toBadRequestLeft[TaskVersionDto]
              }
        }
      }
    }
  }

  val deleteVersion = deleteVersionEndpoint.serverLogic { auth => version =>
    taskVersionService
      .deleteVersion(version)
      .map {
        case Right(_)    => TgJsonSuccess.justMessageRight("Task version deleted successfully")
        case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
      }
  }

}
