package com.togger.controllers.project

import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.dto.{
  TaskDumpPathResultResp,
  TaskDumpPathStatusResp,
  TaskDumpResp,
  TaskDumpStatusResp,
  TaskDumpSyncStatusResp,
  TaskDumpTaskStatusResp
}
import com.togger.dump.{TaskDumpEndpoints, TaskDumpService, TaskDumpSyncStatus, TaskDumpTaskStatus}

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

  val checkStatus = taskDumpEndpoints.checkStatusEndpoint.serverLogic { auth => projectId =>
    taskDumpService
      .checkStatus(projectId, auth.body.get.id)
      .map {
        case Right(result) =>
          val pathStatuses = result.pathStatuses.map(p => TaskDumpPathStatusResp(p.dumpPath, p.taskStatuses.map(toResp)))
          TgJsonSuccess.justBodyRight(TaskDumpStatusResp(projectId, pathStatuses))
        case Left(error) => error.toBadRequestLeft[TaskDumpStatusResp]
      }
  }

  private def toResp(s: TaskDumpTaskStatus): TaskDumpTaskStatusResp =
    TaskDumpTaskStatusResp(s.taskId, toRespStatus(s.status), s.differences)

  private def toRespStatus(s: TaskDumpSyncStatus): TaskDumpSyncStatusResp = s match {
    case TaskDumpSyncStatus.InSync          => TaskDumpSyncStatusResp.InSync
    case TaskDumpSyncStatus.OutOfSync       => TaskDumpSyncStatusResp.OutOfSync
    case TaskDumpSyncStatus.MissingFromDump => TaskDumpSyncStatusResp.MissingFromDump
    case TaskDumpSyncStatus.MissingFromDb   => TaskDumpSyncStatusResp.MissingFromDb
    case TaskDumpSyncStatus.Malformed       => TaskDumpSyncStatusResp.Malformed
  }

}
