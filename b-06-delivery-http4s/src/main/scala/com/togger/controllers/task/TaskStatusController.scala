package com.togger.controllers.task

import com.togger.common.TgClock
import com.togger.common.TgJsonSuccess
import com.togger.controllers.common.PageRequestValidation
import com.togger.dto._
import com.togger.task._

import java.util.UUID

class TaskStatusController(
  taskStatusService: TaskStatusService,
  taskStatusEndpoints: TaskStatusEndpoints,
  tgClock: TgClock
) {

  import taskStatusEndpoints._

  val findById =
    findByIdEndpoint.serverLogic { _ => taskStatusId =>
      taskStatusService
        .findById(taskStatusId)
        .map { taskStatus =>
          TgJsonSuccess.justBodyRight(
            FindTaskStatusResp(
              id = taskStatus.id,
              projectId = taskStatus.projectId,
              name = taskStatus.name,
              description = taskStatus.description,
              emoji = taskStatus.emoji
            )
          )
        }
    }

  val findByProjectId =
    findByProjectIdEndpoint.serverLogic { _ => request =>
      val (projectId, pageRequest) = request
      PageRequestValidation.withPage(pageRequest) { page =>
        taskStatusService
          .findPaginatedByProjectId(projectId, page)
          .map(statusesPage =>
            TgJsonSuccess.justBodyRight(
              statusesPage.mapItem(taskStatus =>
                FindTaskStatusResp(
                  id = taskStatus.id,
                  projectId = taskStatus.projectId,
                  name = taskStatus.name,
                  description = taskStatus.description,
                  emoji = taskStatus.emoji
                )
              )
            )
          )
      }
    }

  val save = saveEndpoint.serverLogic { _ => dto =>
    val now = tgClock.currentInstant

    val task = TaskStatus(
      id = UUID.randomUUID(),
      projectId = dto.projectId,
      name = dto.name,
      description = dto.description,
      emoji = dto.emoji,
      createdAt = now,
      updatedAt = now
    )

    taskStatusService.createTaskStatus(task).map { taskStatus =>
      val respDto = CreateTaskStatusResp(
        id = taskStatus.id,
        projectId = taskStatus.projectId,
        name = taskStatus.name,
        description = dto.description,
        emoji = taskStatus.emoji
      )
      TgJsonSuccess.justBodyRight(respDto)
    }
  }

  val update = updateEndpoint.serverLogic { _ => dto =>
    val now = tgClock.currentInstant

    val taskStatus = TaskStatus(
      id = dto.id,
      projectId = dto.projectId,
      name = dto.name,
      description = dto.description,
      emoji = dto.emoji,
      createdAt = now,
      updatedAt = now
    )

    taskStatusService.updateTask(taskStatus).map { taskStatus =>
      TgJsonSuccess.justBodyRight(
        FindTaskStatusResp(
          id = taskStatus.id,
          projectId = taskStatus.projectId,
          name = taskStatus.name,
          description = taskStatus.description,
          emoji = taskStatus.emoji
        )
      )
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { _ => id =>
    taskStatusService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Task status deleted successfully"))
  }

}
