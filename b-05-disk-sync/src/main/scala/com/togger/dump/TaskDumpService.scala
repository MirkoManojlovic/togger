package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.project.Project
import com.togger.project.ProjectError.{NotAssignedToProject, ProjectError}
import com.togger.task.TaskStatus
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

case class TaskDumpResult(dumpPath: String, taskCount: Int)

class TaskDumpService(
  taskDumpRepo: TaskDumpRepo,
  taskDumpDiskWriter: TaskDumpDiskWriter,
  dumpBasePath: String,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  private type DumpData = (Project, List[TaskStatus], List[TaskDumpData])

  def dumpProject(projectId: UUID, accountId: UUID): IO[Either[ProjectError, TaskDumpResult]] = {
    logger.info(s"Dumping tasks for ${sp(projectId)} requested by ${sp(accountId)}")
    getDumpData(projectId, accountId)
      .transact(transactor)
      .map {
        case Left(error) => Left(error)
        case Right((project, taskStatuses, tasks)) =>
          val manifest = ProjectDumpManifest(
            project = project,
            taskStatuses = taskStatuses,
            dumpedAt = tgClock.currentInstant,
            taskCount = tasks.size
          )
          val dumpPath = taskDumpDiskWriter.write(dumpBasePath, manifest, tasks)
          Right(TaskDumpResult(dumpPath, tasks.size))
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
