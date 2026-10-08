package com.togger.controllers.task

import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.dto._
import com.togger.task._

import java.util.UUID

class TaskLabelController(
  taskLabelService: TaskLabelService,
  taskLabelEndpoints: TaskLabelEndpoints,
  tgClock: TgClock
) {

  import taskLabelEndpoints._

  val findById =
    findByIdEndpoint.serverLogic { auth => taskLabelId =>
      taskLabelService
        .findById(taskLabelId)
        .map { taskLabel =>
          TgJsonSuccess.justBodyRight(
            FindTaskLabelResp(
              id = taskLabel.id,
              projectId = taskLabel.projectId,
              name = taskLabel.name,
              description = taskLabel.description
            )
          )
        }
    }

  val findByProjectId =
    findByProjectIdEndpoint.serverLogic { auth => request =>
      val (projectId, pageRequest) = request
      PageRequestValidation.withPage(pageRequest) { page =>
        taskLabelService
          .findPaginatedByProjectId(projectId, page)
          .map(labelsPage =>
            TgJsonSuccess.justBodyRight(
              labelsPage.mapItem(taskLabel =>
                FindTaskLabelResp(
                  id = taskLabel.id,
                  projectId = taskLabel.projectId,
                  name = taskLabel.name,
                  description = taskLabel.description
                )
              )
            )
          )
      }
    }

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val taskLabel = TaskLabel(
      id = UUID.randomUUID(),
      projectId = dto.projectId,
      name = dto.name,
      description = dto.description,
      createdAt = now,
      updatedAt = now
    )

    taskLabelService.createTaskLabel(taskLabel).map { taskLabel =>
      val respDto = CreateTaskLabelResp(
        id = taskLabel.id,
        projectId = taskLabel.projectId,
        name = taskLabel.name,
        description = dto.description
      )
      TgJsonSuccess.justBodyRight(respDto)
    }
  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val taskLabel = TaskLabel(
      id = dto.id,
      projectId = dto.projectId,
      name = dto.name,
      description = dto.description,
      createdAt = now,
      updatedAt = now
    )

    taskLabelService.updateTask(taskLabel).map { taskLabel =>
      TgJsonSuccess.justBodyRight(
        FindTaskLabelResp(
          id = taskLabel.id,
          projectId = taskLabel.projectId,
          name = taskLabel.name,
          description = taskLabel.description
        )
      )
    }
  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    taskLabelService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Task label deleted successfully"))
  }

}
