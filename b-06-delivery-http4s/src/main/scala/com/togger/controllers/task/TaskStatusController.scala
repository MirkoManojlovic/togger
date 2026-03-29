package com.togger.controllers.task

import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto._
import com.togger.task._

class TaskStatusController(
  taskStatusService: TaskStatusService,
  taskStatusEndpoints: TaskStatusEndpoints,
  tgClock: TgClock
) {

  import taskStatusEndpoints._

  val findById =
    findByIdEndpoint.serverLogic { auth => taskStatusId =>
      taskStatusService
        .findById(taskStatusId)
        .map { taskStatus =>
          TgJsonSuccess.justBodyRight(
            FindTaskStatusResp(
              id = taskStatus.id,
              projectId = taskStatus.projectId,
              name = taskStatus.name,
              description = taskStatus.description
            )
          )
        }
    }

  val findByProjectId =
    findByProjectIdEndpoint.serverLogic { auth => projectId =>
      taskStatusService
        .findByProjectId(projectId)
        .map(taskStatuses =>
          TgJsonSuccess.justBodyRight(
            taskStatuses
              .map(taskStatus =>
                FindTaskStatusResp(
                  id = taskStatus.id,
                  projectId = taskStatus.projectId,
                  name = taskStatus.name,
                  description = taskStatus.description
                )
              )
          )
        )
    }

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val task = TaskStatus(
      id = -1,
      projectId = dto.projectId,
      name = dto.name,
      description = dto.description,
      createdAt = now,
      updatedAt = now
    )

    taskStatusService.createTaskStatus(task).map { taskStatus =>
      val respDto = CreateTaskStatusResp(
        id = taskStatus.id,
        projectId = taskStatus.projectId,
        name = taskStatus.name,
        description = dto.description
      )
      TgJsonSuccess.justBodyRight(respDto)
    }
  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val taskStatus = TaskStatus(
      id = dto.id,
      projectId = dto.projectId,
      name = dto.name,
      description = dto.description,
      createdAt = now,
      updatedAt = now
    )

    taskStatusService.updateTask(taskStatus).map { taskStatus =>
      TgJsonSuccess.justBodyRight(
        FindTaskStatusResp(
          id = taskStatus.id,
          projectId = taskStatus.projectId,
          name = taskStatus.name,
          description = taskStatus.description
        )
      )
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    taskStatusService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Task status deleted successfully"))
  }

}
