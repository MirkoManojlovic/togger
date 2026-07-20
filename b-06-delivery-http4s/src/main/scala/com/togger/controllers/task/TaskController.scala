package com.togger.controllers.task

import com.togger.account.Account
import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto._
import com.togger.task._

class TaskController(taskService: TaskService, taskEndpoints: TaskEndpoints, tgClock: TgClock) {

  import taskEndpoints._

  val findById =
    findByIdEndpoint.serverLogic { auth => taskId =>
      taskService
        .findById(taskId)
        .map { case (task, links, logEntries) =>
          TgJsonSuccess.justBodyRight(
            FindTaskResp(
              task = FindTaskResp1(
                id = task.id,
                projectId = task.projectId,
                name = task.name,
                description = task.description,
                assignedTo = task.assignedTo,
                createdBy = task.createdBy,
                taskStatusId = task.taskStatusId,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt
              ),
              links = links.map(link =>
                FindLinkResp(
                  id = link.id,
                  name = link.name,
                  url = link.url
                )
              ),
              logEntries = logEntries.map(logEntry =>
                LogEntryDto(
                  id = logEntry.id,
                  textInternal = logEntry.textInternal,
                  textExternal = logEntry.textExternal,
                  status = logEntry.status,
                  taskId = logEntry.taskId,
                  createdBy = logEntry.createdBy,
                  createdAt = logEntry.createdAt,
                  updatedAt = logEntry.updatedAt
                )
              )
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
                  logEntries = logEntries.map(logEntry =>
                    LogEntryDto(
                      id = logEntry.id,
                      textInternal = logEntry.textInternal,
                      textExternal = logEntry.textExternal,
                      status = logEntry.status,
                      taskId = logEntry.taskId,
                      createdBy = logEntry.createdBy,
                      createdAt = logEntry.createdAt,
                      updatedAt = logEntry.updatedAt
                    )
                  ),
                  links = links.map(link =>
                    FindLinkResp(
                      id = link.id,
                      name = link.name,
                      url = link.url
                    )
                  )
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
      id = -1,
      projectId = taskDto.projectId,
      name = taskDto.name,
      description = taskDto.description,
      assignedTo = taskDto.assignedTo,
      createdBy = auth.body.get.id,
      taskStatusId = taskDto.taskStatusId,
      createdAt = taskDto.createdAt,
      updatedAt = taskDto.createdAt
    )

    val now = tgClock.currentInstant
    val linksForDb =
      linksDto.map(dto =>
        Link(
          id = -1,
          name = dto.name,
          url = dto.url,
          taskId = -1,
          createdAt = now,
          updatedAt = now
        )
      )

    taskService.createTask(task, linksForDb).map { case (savedTask, savedLinks) =>
      val savedDto = FindTaskResp(
        task = FindTaskResp1(
          id = savedTask.id,
          projectId = savedTask.projectId,
          name = savedTask.name,
          description = savedTask.description,
          assignedTo = savedTask.assignedTo,
          createdBy = savedTask.createdBy,
          taskStatusId = task.taskStatusId,
          createdAt = savedTask.createdAt,
          updatedAt = savedTask.updatedAt
        ),
        links =
          savedLinks.map(savedLink => FindLinkResp(id = savedLink.id, url = savedLink.url, name = savedLink.name)),
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

    val task = Task(
      id = taskDto.id,
      projectId = taskDto.projectId,
      name = taskDto.name,
      description = taskDto.description,
      assignedTo = taskDto.assignedTo,
      createdBy = auth.body.get.id,
      taskStatusId = taskDto.taskStatusId,
      createdAt = taskDto.createdAt,
      updatedAt = newUpdatedAt
    )

    val linksForUpdateDb =
      linksForUpdate.map(dto =>
        Link(
          id = dto.id,
          name = dto.name,
          url = dto.url,
          taskId = taskDto.id,
          createdAt = newUpdatedAt,
          updatedAt = newUpdatedAt
        )
      )

    val linksToCreateDb =
      linksToCreate.map(dto =>
        Link(
          id = -1,
          name = dto.name,
          url = dto.url,
          taskId = taskDto.id,
          createdAt = newUpdatedAt,
          updatedAt = newUpdatedAt
        )
      )

    taskService.updateTask(task, linksForUpdateDb, linksToCreateDb).map { case (savedTask, updatedLinks, savedLinks) =>
      val savedDto = FindTaskResp(
        task = FindTaskResp1(
          id = savedTask.id,
          projectId = savedTask.projectId,
          name = savedTask.name,
          description = savedTask.description,
          assignedTo = savedTask.assignedTo,
          createdBy = savedTask.createdBy,
          taskStatusId = taskDto.taskStatusId,
          createdAt = savedTask.createdAt,
          updatedAt = savedTask.updatedAt
        ),
        links = (updatedLinks ++ savedLinks)
          .map(savedLink => FindLinkResp(id = savedLink.id, url = savedLink.url, name = savedLink.name)),
        logEntries = Seq.empty
      )
      TgJsonSuccess.justBodyRight(savedDto)
    }
  }

}
