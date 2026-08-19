package com.togger.controllers.project

import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.dto.TaskDumpResp
import com.togger.dump.{TaskDumpEndpoints, TaskDumpService}

class TaskDumpController(
  taskDumpEndpoints: TaskDumpEndpoints,
  taskDumpService: TaskDumpService
) {

  val dumpTasks = taskDumpEndpoints.dumpEndpoint.serverLogic { auth => projectId =>
    taskDumpService
      .dumpProject(projectId, auth.body.get.id)
      .map {
        case Right(result) =>
          TgJsonSuccess.justBodyRight(TaskDumpResp(projectId, result.dumpPath, result.taskCount))
        case Left(error) => error.toBadRequestLeft[TaskDumpResp]
      }
  }

}
