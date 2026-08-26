package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.account.Account
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.project.Project
import com.togger.project.ProjectError.{NoDumpPathConfigured, NotAssignedToProject, ProjectError}
import com.togger.task.TaskStatus
import com.togger.taskdumpconfig.{TaskDumpConfig, TaskDumpConfigService}
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

  private type DumpData = (Project, List[TaskStatus], Account, List[TaskDumpData])

  def dumpProject(projectId: UUID, accountId: UUID): IO[Either[ProjectError, TaskDumpResult]] =
    dumpWithConfigs(projectId, accountId, taskDumpConfigService.findByProjectId(projectId))

  def dumpConfig(dumpConfigId: UUID, accountId: UUID): IO[Either[ProjectError, TaskDumpResult]] =
    taskDumpConfigService.findById(dumpConfigId).flatMap { config =>
      dumpWithConfigs(config.projectId, accountId, IO.pure(List(config)))
    }

  def checkStatus(projectId: UUID, accountId: UUID): IO[Either[ProjectError, TaskDumpStatusResult]] =
    checkStatusWithConfigs(projectId, accountId, taskDumpConfigService.findByProjectId(projectId))

  def checkConfigStatus(dumpConfigId: UUID, accountId: UUID): IO[Either[ProjectError, TaskDumpStatusResult]] =
    taskDumpConfigService.findById(dumpConfigId).flatMap { config =>
      checkStatusWithConfigs(config.projectId, accountId, IO.pure(List(config)))
    }

  private def dumpWithConfigs(
    projectId: UUID,
    accountId: UUID,
    configsIO: IO[List[TaskDumpConfig]]
  ): IO[Either[ProjectError, TaskDumpResult]] = {
    logger.info(s"Dumping tasks for ${sp(projectId)} requested by ${sp(accountId)}")
    getDumpData(projectId, accountId)
      .transact(transactor)
      .flatMap {
        case Left(error) => IO.pure(Left(error))
        case Right((project, taskStatuses, account, tasks)) =>
          configsIO.map { configs =>
            if (configs.isEmpty)
              Left(NoDumpPathConfigured)
            else {
              val manifest = ProjectDumpManifest(
                project = project,
                taskStatuses = taskStatuses,
                dumpedBy = AccountDumpData.from(account),
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

  private def checkStatusWithConfigs(
    projectId: UUID,
    accountId: UUID,
    configsIO: IO[List[TaskDumpConfig]]
  ): IO[Either[ProjectError, TaskDumpStatusResult]] = {
    logger.info(s"Checking dump status for ${sp(projectId)} requested by ${sp(accountId)}")
    getDumpData(projectId, accountId)
      .transact(transactor)
      .flatMap {
        case Left(error) => IO.pure(Left(error))
        case Right((project, _, _, dbTasks)) =>
          configsIO.map { configs =>
            if (configs.isEmpty)
              Left(NoDumpPathConfigured)
            else {
              val dbTasksById = dbTasks.map(t => t.task.id -> t).toMap
              val pathStatuses = configs.map(config => buildPathStatus(config.dumpPath, project, dbTasksById))
              Right(TaskDumpStatusResult(pathStatuses))
            }
          }
      }
  }

  private def buildPathStatus(
    dumpPath: String,
    project: Project,
    dbTasksById: Map[UUID, TaskDumpData]
  ): TaskDumpPathStatus = {
    val diskTasksById = taskDumpDiskWriter.readDumpedTasks(dumpPath, project)
    val commonIds = dbTasksById.keySet.intersect(diskTasksById.keySet)
    val missingFromDumpIds = dbTasksById.keySet.diff(diskTasksById.keySet)
    val missingFromDbIds = diskTasksById.keySet.diff(dbTasksById.keySet)

    def taskName(taskId: UUID): Option[String] =
      dbTasksById
        .get(taskId)
        .orElse(diskTasksById.get(taskId).flatMap(_.toOption))
        .flatMap(_.taskVersions.headOption)
        .map(_.name)

    val commonStatuses = commonIds.toList.map { taskId =>
      diskTasksById(taskId) match {
        case Left(parseError) =>
          TaskDumpTaskStatus(taskId, taskName(taskId), TaskDumpSyncStatus.Malformed, List(parseError))
        case Right(diskData) =>
          val diffs = differences(dbTasksById(taskId), diskData)
          if (diffs.isEmpty) TaskDumpTaskStatus(taskId, taskName(taskId), TaskDumpSyncStatus.InSync, Nil)
          else TaskDumpTaskStatus(taskId, taskName(taskId), TaskDumpSyncStatus.OutOfSync, diffs)
      }
    }
    val missingFromDumpStatuses =
      missingFromDumpIds.toList.map(taskId =>
        TaskDumpTaskStatus(taskId, taskName(taskId), TaskDumpSyncStatus.MissingFromDump, Nil)
      )
    val missingFromDbStatuses =
      missingFromDbIds.toList.map(taskId =>
        TaskDumpTaskStatus(taskId, taskName(taskId), TaskDumpSyncStatus.MissingFromDb, Nil)
      )

    TaskDumpPathStatus(dumpPath, commonStatuses ++ missingFromDumpStatuses ++ missingFromDbStatuses)
  }

  private def differences(db: TaskDumpData, disk: TaskDumpData): List[String] =
    List(
      Option.when(db.task != disk.task)("task fields differ"),
      Option.when(db.taskVersions != disk.taskVersions)("task versions differ"),
      Option.when(db.links != disk.links)("links differ"),
      Option.when(db.logEntries != disk.logEntries)("log entries differ")
    ).flatten

  private def getDumpData(projectId: UUID, accountId: UUID): ConnectionIO[Either[ProjectError, DumpData]] =
    for {
      assigned <- taskDumpRepo.isAssigned(projectId, accountId)
      result <-
        if (!assigned)
          (NotAssignedToProject: ProjectError).asLeft[DumpData].pure[ConnectionIO]
        else
          for {
            projectAndStatuses <- taskDumpRepo.getProjectAndTaskStatuses(projectId)
            account <- taskDumpRepo.findAccountById(accountId)
            tasks <- taskDumpRepo.getAllTasksForProject(projectId)
          } yield (projectAndStatuses._1, projectAndStatuses._2, account, tasks).asRight[ProjectError]
    } yield result

}
