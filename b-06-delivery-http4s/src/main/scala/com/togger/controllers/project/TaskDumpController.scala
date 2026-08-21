package com.togger.controllers.project

import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.dto.{
  TaskDumpPathResultResp,
  TaskDumpPathStatusResp,
  TaskDumpResp,
  TaskDumpStatusResp,
  TaskDumpSyncStatusResp,
  TaskDumpTaskStatusResp,
  TaskLoadResp,
  TaskLoadStatusResp,
  TaskLoadTaskResultResp
}
import com.togger.dump.{
  TaskDumpEndpoints,
  TaskDumpService,
  TaskDumpSyncStatus,
  TaskDumpTaskStatus,
  TaskLoadService,
  TaskLoadTaskResult,
  TaskLoadTaskStatus
}

class TaskDumpController(
  taskDumpEndpoints: TaskDumpEndpoints,
  taskDumpService: TaskDumpService,
  taskLoadService: TaskLoadService
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

  val loadTasks = taskDumpEndpoints.loadEndpoint.serverLogic { auth =>
    { case (projectId, req) =>
      taskLoadService
        .loadProject(projectId, auth.body.get.id, req.dumpPath)
        .map {
          case Right(result) =>
            val results = result.taskResults.map(toResp)
            TgJsonSuccess.justBodyRight(TaskLoadResp(projectId, result.dumpPath, result.taskCount, results))
          case Left(error) => error.toBadRequestLeft[TaskLoadResp]
        }
    }
  }

  private def toResp(r: TaskLoadTaskResult): TaskLoadTaskResultResp =
    TaskLoadTaskResultResp(
      r.taskId,
      r.taskName,
      toRespStatus(r.status),
      r.versionsAdded,
      r.linksAdded,
      r.logEntriesAdded,
      r.error
    )

  private def toRespStatus(s: TaskLoadTaskStatus): TaskLoadStatusResp = s match {
    case TaskLoadTaskStatus.Inserted  => TaskLoadStatusResp.Inserted
    case TaskLoadTaskStatus.Merged    => TaskLoadStatusResp.Merged
    case TaskLoadTaskStatus.Unchanged => TaskLoadStatusResp.Unchanged
    case TaskLoadTaskStatus.Malformed => TaskLoadStatusResp.Malformed
    case TaskLoadTaskStatus.Failed    => TaskLoadStatusResp.Failed
  }

  private def toResp(s: TaskDumpTaskStatus): TaskDumpTaskStatusResp =
    TaskDumpTaskStatusResp(s.taskId, s.taskName, toRespStatus(s.status), s.differences)

  private def toRespStatus(s: TaskDumpSyncStatus): TaskDumpSyncStatusResp = s match {
    case TaskDumpSyncStatus.InSync          => TaskDumpSyncStatusResp.InSync
    case TaskDumpSyncStatus.OutOfSync       => TaskDumpSyncStatusResp.OutOfSync
    case TaskDumpSyncStatus.MissingFromDump => TaskDumpSyncStatusResp.MissingFromDump
    case TaskDumpSyncStatus.MissingFromDb   => TaskDumpSyncStatusResp.MissingFromDb
    case TaskDumpSyncStatus.Malformed       => TaskDumpSyncStatusResp.Malformed
  }

}
