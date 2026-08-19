package com.togger.controllers.task

import com.togger.account.Account
import com.togger.common.TgError._
import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.controllers.logentry.LogEntryDtoMapper.toDto
import com.togger.controllers.task.LinkDtoMapper.{toDto => toLinkDto}
import com.togger.controllers.task.TaskDtoMapper.{toDto => toTaskDto, toVersionDto}
import com.togger.dto._
import com.togger.task._

import java.util.UUID

class TaskController(
  taskService: TaskService,
  taskVersionService: TaskVersionService,
  taskEndpoints: TaskEndpoints,
  tgClock: TgClock
) {

  import taskEndpoints._

  val findById =
    findByIdEndpoint.serverLogic { auth => taskId =>
      taskService
        .findById(taskId)
        .map { case (taskWithVersions, links, logEntries) =>
          TgJsonSuccess.justBodyRight(
            FindTaskResp(
              task = toTaskDto(taskWithVersions.task, taskWithVersions.latestVersion),
              links = links.map(link => toLinkDto(link.link, link.latestVersion)),
              logEntries = logEntries.map(entry => toDto(entry.logEntry, entry.latestVersion))
            )
          )
        }
    }

  val findByProjectId =
    findByProjectIdEndpoint.serverLogic { auth => projectId =>
      taskService
        .findByProjectId(projectId)
        .map(tasksLogsAndLinks =>
          TgJsonSuccess.justBodyRight(
            tasksLogsAndLinks
              .map { case (taskFull, links, logEntries) =>
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
                  createdAt = taskFull.createdAt,
                  updatedAt = taskFull.updatedAt,
                  latestVersion = taskFull.latestVersion,
                  logEntries = logEntries.map(entry => toDto(entry.logEntry, entry.versions.head)),
                  links = links.map(link => toLinkDto(link.link, link.latestVersion))
                )
              }
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

  val save = saveEndpoint.serverLogic { auth => dto =>
    val taskDto = dto.task
    val linksDto = dto.links

    val task = Task(
      id = UUID.randomUUID(),
      projectId = taskDto.projectId,
      createdBy = auth.body.get.id,
      createdAt = taskDto.createdAt
    )

    val firstVersion = TaskVersion(
      version = UUID.randomUUID(),
      taskId = task.id,
      name = taskDto.name,
      description = taskDto.description,
      assignedTo = taskDto.assignedTo,
      closedBy = None,
      taskStatusId = taskDto.taskStatusId,
      createdAt = taskDto.createdAt,
      updatedAt = taskDto.createdAt
    )

    val now = tgClock.currentInstant
    val newLinks =
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

    taskService.createTask(task, firstVersion, newLinks).map { case (savedTask, savedLinks) =>
      val savedDto = FindTaskResp(
        task = toTaskDto(savedTask.task, savedTask.version),
        links = savedLinks.map(link => toLinkDto(link.link, link.version)),
        logEntries = Seq.empty
      )
      TgJsonSuccess.justBodyRight(savedDto)
    }
  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    val taskDto = dto.task
    val linksForUpdate = dto.linksForUpdate
    val linksToCreate = dto.linksToCreate

    val newUpdatedAt = tgClock.currentInstant

    val newVersionContent = TaskVersion(
      version = UUID.randomUUID(),
      taskId = taskDto.id,
      name = taskDto.name,
      description = taskDto.description,
      assignedTo = taskDto.assignedTo,
      closedBy = None,
      taskStatusId = taskDto.taskStatusId,
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

    taskService.updateTask(taskDto.id, newVersionContent, linkVersionUpdates, newLinks).map {
      case (savedTask, updatedLinks, savedLinks) =>
        val savedDto = FindTaskResp(
          task = toTaskDto(savedTask.task, savedTask.version),
          links = (updatedLinks ++ savedLinks).map(link => toLinkDto(link.link, link.version)),
          logEntries = Seq.empty
        )
        TgJsonSuccess.justBodyRight(savedDto)
    }
  }

  val listVersions = listVersionsEndpoint.serverLogic { auth => taskId =>
    taskVersionService
      .listVersions(taskId)
      .map(versions => TgJsonSuccess.justBodyRight(versions.map(toVersionDto)))
  }

  val updateVersion = updateVersionEndpoint.serverLogic { auth => dto =>
    taskVersionService
      .updateVersionContent(dto.version, dto.name, dto.description, dto.assignedTo, dto.closedBy, dto.taskStatusId)
      .map {
        case Right(version) => TgJsonSuccess.justBodyRight(toVersionDto(version))
        case Left(error)    => error.toBadRequestLeft[TaskVersionDto]
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
