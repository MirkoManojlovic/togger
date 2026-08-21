package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.project.Project
import com.togger.project.ProjectError.{NoDumpPathConfigured, NotAssignedToProject, ProjectError}
import com.togger.task.TaskStatus
import com.togger.taskdumpconfig.TaskDumpConfigService
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID
import scala.util.{Failure, Success, Try}

case class TaskDumpPathResult(
  dumpPath: String,
  error: Option[String]
) {
  lazy val success: Boolean = error.isEmpty
}

object TaskDumpPathResult {
  def success(dumpPath: String) =
    TaskDumpPathResult(dumpPath = dumpPath, error = None)

  def error(dumpPath: String, error: String) =
    TaskDumpPathResult(dumpPath = dumpPath, error = Some(error))
}

case class TaskDumpResult(taskCount: Int, pathResults: List[TaskDumpPathResult])

class TaskDumpService(
  taskDumpRepo: TaskDumpRepo,
  taskDumpDiskWriter: TaskDumpDiskWriter,
  taskDumpConfigService: TaskDumpConfigService,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  private type DumpData = (Project, List[TaskStatus], List[TaskDumpData])

  def dumpProject(projectId: UUID, accountId: UUID): IO[Either[ProjectError, TaskDumpResult]] = {
    logger.info(s"Dumping tasks for ${sp(projectId)} requested by ${sp(accountId)}")
    getDumpData(projectId, accountId)
      .transact(transactor)
      .flatMap {
        case Left(error) => IO.pure(Left(error))
        case Right((project, taskStatuses, tasks)) =>
          taskDumpConfigService.findByProjectId(projectId).map { configs =>
            if (configs.isEmpty)
              Left(NoDumpPathConfigured)
            else {
              val manifest = ProjectDumpManifest(
                project = project,
                taskStatuses = taskStatuses,
                dumpedAt = tgClock.currentInstant,
                taskCount = tasks.size
              )
              val pathResults = configs.map { config =>
                Try(taskDumpDiskWriter.write(config.dumpPath, manifest, tasks)) match {
                  case Success(resolvedPath) => TaskDumpPathResult.success(resolvedPath)
                  case Failure(e)            => TaskDumpPathResult.error(config.dumpPath, e.getMessage)
                }
              }
              Right(TaskDumpResult(tasks.size, pathResults))
            }
          }
      }
  }

  private def getDumpData(projectId: UUID, accountId: UUID): ConnectionIO[Either[ProjectError, DumpData]] =
    for {
      assigned <- taskDumpRepo.isAssigned(projectId, accountId)
      result <-
        if (!assigned)
          (NotAssignedToProject: ProjectError).asLeft[DumpData].pure[ConnectionIO]
        else
          for {
            projectAndStatuses <- taskDumpRepo.getProjectAndTaskStatuses(projectId)
            tasks <- taskDumpRepo.getAllTasksForProject(projectId)
          } yield (projectAndStatuses._1, projectAndStatuses._2, tasks).asRight[ProjectError]
    } yield result

}
