package com.togger.controllers.project

import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.dto.{TaskDumpPathResultResp, TaskDumpResp}
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
          val results = result.pathResults.map(r => TaskDumpPathResultResp(r.dumpPath, r.error))
          TgJsonSuccess.justBodyRight(TaskDumpResp(projectId, result.taskCount, results))
        case Left(error) => error.toBadRequestLeft[TaskDumpResp]
      }
  }

}
