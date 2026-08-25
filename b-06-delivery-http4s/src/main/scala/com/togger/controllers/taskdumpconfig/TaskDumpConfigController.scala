package com.togger.controllers.taskdumpconfig

import cats.effect.IO
import com.togger.common.{TgClock, TgJsonFailure, TgJsonSuccess}
import com.togger.dto.taskdumpconfig.TaskDumpConfigResp
import com.togger.taskdumpconfig.{TaskDumpConfig, TaskDumpConfigEndpoints, TaskDumpConfigService}

import java.util.UUID

class TaskDumpConfigController(
  taskDumpConfigEndpoints: TaskDumpConfigEndpoints,
  taskDumpConfigService: TaskDumpConfigService,
  taskDumpConfigVerification: TaskDumpConfigVerification,
  tgClock: TgClock
) {

  import taskDumpConfigEndpoints._

  private def toResp(taskDumpConfig: TaskDumpConfig): TaskDumpConfigResp =
    TaskDumpConfigResp(
      id = taskDumpConfig.id,
      projectId = taskDumpConfig.projectId,
      accountId = taskDumpConfig.accountId,
      name = taskDumpConfig.name,
      dumpPath = taskDumpConfig.dumpPath,
      createdAt = taskDumpConfig.createdAt,
      updatedAt = taskDumpConfig.updatedAt
    )

  val findById =
    findByIdEndpoint.serverLogic { auth => id =>
      taskDumpConfigService
        .findById(id)
        .map(taskDumpConfig => TgJsonSuccess.justBodyRight(toResp(taskDumpConfig)))
    }

  val save = saveEndpoint.serverLogic { auth => dto =>
    taskDumpConfigVerification
      .verify(dto.name, dto.dumpPath)
      .fold(
        error => IO(TgJsonFailure.badRequestStringLeft(error.message)),
        _ => {
          val now = tgClock.currentInstant

          val taskDumpConfig = TaskDumpConfig(
            id = UUID.randomUUID(),
            projectId = dto.projectId,
            accountId = auth.body.get.id,
            name = dto.name,
            dumpPath = dto.dumpPath,
            createdAt = now,
            updatedAt = now
          )

          taskDumpConfigService
            .create(taskDumpConfig)
            .map(saved => TgJsonSuccess.justBodyRight(toResp(saved)))
        }
      )

  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    taskDumpConfigVerification
      .verify(dto.name, dto.dumpPath)
      .fold(
        error => IO(TgJsonFailure.badRequestStringLeft(error.message)),
        _ => {
          val now = tgClock.currentInstant

          val taskDumpConfig = TaskDumpConfig(
            id = dto.id,
            projectId = dto.projectId,
            accountId = auth.body.get.id,
            name = dto.name,
            dumpPath = dto.dumpPath,
            createdAt = now,
            updatedAt = now
          )

          taskDumpConfigService
            .update(taskDumpConfig)
            .map(updated => TgJsonSuccess.justBodyRight(toResp(updated)))
        }
      )

  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    taskDumpConfigService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Task dump config deleted successfully"))
  }

  val findByProjectId =
    findByProjectIdEndpoint.serverLogic { auth => projectId =>
      taskDumpConfigService
        .findByProjectId(projectId)
        .map(configs => TgJsonSuccess.justBodyRight(configs.map(toResp)))
    }

  val findByAccountId =
    findByAccountIdEndpoint.serverLogic { auth => accountId =>
      taskDumpConfigService
        .findByAccountId(accountId)
        .map(configs => TgJsonSuccess.justBodyRight(configs.map(toResp)))
    }

}
