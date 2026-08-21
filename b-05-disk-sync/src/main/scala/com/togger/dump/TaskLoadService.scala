package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.logging.TgLogging
import com.togger.project.Project
import com.togger.project.ProjectError.{NotAssignedToProject, ProjectError}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.Transactor

import java.util.UUID

class TaskLoadService(
  taskDumpRepo: TaskDumpRepo,
  taskLoadRepo: TaskLoadRepo,
  taskDumpDiskWriter: TaskDumpDiskWriter,
  transactor: Transactor[IO]
) extends TgLogging {

  def loadProject(projectId: UUID, accountId: UUID, dumpPath: String): IO[Either[ProjectError, TaskLoadResult]] = {
    logger.info(s"Loading tasks for ${sp(projectId)} from ${sp(dumpPath)} requested by ${sp(accountId)}")
    findProjectIfAssigned(projectId, accountId)
      .transact(transactor)
      .flatMap {
        case Left(error) => IO.pure(Left(error))
        case Right(project) =>
          val diskTasksById = taskDumpDiskWriter.readDumpedTasks(dumpPath, project)
          diskTasksById.toList
            .traverse {
              case (taskId, Left(parseError)) =>
                IO.pure(TaskLoadTaskResult(taskId, None, TaskLoadTaskStatus.Malformed, 0, 0, 0, Some(parseError)))
              case (taskId, Right(diskData)) =>
                taskLoadRepo
                  .mergeTask(diskData)
                  .transact(transactor)
                  .attempt
                  .map {
                    case Right(result) => result
                    case Left(e) =>
                      TaskLoadTaskResult(
                        taskId = taskId,
                        taskName = diskData.taskVersions.headOption.map(_.name),
                        status = TaskLoadTaskStatus.Failed,
                        versionsAdded = 0,
                        linksAdded = 0,
                        logEntriesAdded = 0,
                        error = Some(e.getMessage)
                      )
                  }
            }
            .map(results => Right(TaskLoadResult(dumpPath, results.size, results)))
      }
  }

  private def findProjectIfAssigned(projectId: UUID, accountId: UUID) =
    taskDumpRepo.isAssigned(projectId, accountId).flatMap { assigned =>
      if (!assigned)
        (NotAssignedToProject: ProjectError).asLeft[Project].pure[doobie.ConnectionIO]
      else
        taskDumpRepo.getProjectAndTaskStatuses(projectId).map { case (project, _) => project.asRight[ProjectError] }
    }

}
