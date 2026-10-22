package com.togger.controllers.taskloadconfig

import com.togger.common.{TgClock, TgJsonError, TgJsonFailure, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.dto.taskloadconfig.TaskLoadConfigResp
import com.togger.dump.DataExportLoadConfigVerification
import com.togger.taskloadconfig.{TaskLoadConfig, TaskLoadConfigEndpoints, TaskLoadConfigService}

import java.util.UUID

class TaskLoadConfigController(
  taskLoadConfigEndpoints: TaskLoadConfigEndpoints,
  taskLoadConfigService: TaskLoadConfigService,
  taskLoadConfigVerification: DataExportLoadConfigVerification,
  tgClock: TgClock
) {

  import taskLoadConfigEndpoints._

  private def toResp(taskLoadConfig: TaskLoadConfig): TaskLoadConfigResp =
    TaskLoadConfigResp(
      id = taskLoadConfig.id,
      name = taskLoadConfig.name,
      projectId = taskLoadConfig.projectId,
      accountId = taskLoadConfig.accountId,
      dumpPath = taskLoadConfig.dumpPath,
      createdAt = taskLoadConfig.createdAt,
      updatedAt = taskLoadConfig.updatedAt
    )

  val findById =
    findByIdEndpoint.serverLogic { auth => id =>
      taskLoadConfigService
        .findById(id)
        .map(taskLoadConfig => TgJsonSuccess.justBodyRight(toResp(taskLoadConfig)))
    }

  val save = saveEndpoint.serverLogic { auth => dto =>
    taskLoadConfigVerification
      .verify(dto.name, dto.dumpPath)
      .fold(
        errors => TgJsonFailure.badRequestLeftIO(TgJsonError.fromErrorsNec(errors)),
        _ => {
          val now = tgClock.currentInstant

          val taskLoadConfig = TaskLoadConfig(
            id = UUID.randomUUID(),
            name = dto.name,
            dumpPath = dto.dumpPath,
            projectId = dto.projectId,
            accountId = auth.body.get.id,
            createdAt = now,
            updatedAt = now
          )

          taskLoadConfigService
            .create(taskLoadConfig)
            .map(saved => TgJsonSuccess.justBodyRight(toResp(saved)))
        }
      )

  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    taskLoadConfigVerification
      .verify(dto.name, dto.dumpPath)
      .fold(
        errors => TgJsonFailure.badRequestLeftIO(TgJsonError.fromErrorsNec(errors)),
        _ => {
          val now = tgClock.currentInstant

          val taskLoadConfig = TaskLoadConfig(
            id = dto.id,
            name = dto.name,
            dumpPath = dto.dumpPath,
            projectId = dto.projectId,
            accountId = auth.body.get.id,
            createdAt = now,
            updatedAt = now
          )

          taskLoadConfigService
            .update(taskLoadConfig)
            .map(updated => TgJsonSuccess.justBodyRight(toResp(updated)))
        }
      )

  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    taskLoadConfigService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Task load config deleted successfully"))
  }

  val findByProjectId =
    findByProjectIdEndpoint.serverLogic { auth => request =>
      val (projectId, pageRequest) = request
      PageRequestValidation.withPage(pageRequest) { page =>
        taskLoadConfigService
          .findPaginatedByProjectId(projectId, page)
          .map(configsPage => TgJsonSuccess.justBodyRight(configsPage.mapItem(toResp)))
      }
    }

}
