package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.account.Account
import com.togger.common.TgClock
import com.togger.db.TgPages
import com.togger.logging.TgLogging
import com.togger.project.Project
import com.togger.project.ProjectError.{NoExportPathConfigured, NotAssignedToProject, ProjectError}
import com.togger.task.TaskStatus
import com.togger.dataexportconfig.{DataExportConfig, DataExportConfigService}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID
import scala.util.{Failure, Success, Try}

case class DataExportPathResult(
  exportPath: String,
  error: Option[String]
) {
  lazy val success: Boolean = error.isEmpty
}

object DataExportPathResult {
  def success(exportPath: String) =
    DataExportPathResult(exportPath = exportPath, error = None)

  def error(exportPath: String, error: String) =
    DataExportPathResult(exportPath = exportPath, error = Some(error))
}

case class DataExportResult(taskCount: Int, pathResults: List[DataExportPathResult])

class DataExportService(
  dataExportRepo: DataExportRepo,
  dataExportDiskWriter: DataExportDiskWriter,
  dataExportConfigService: DataExportConfigService,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  private type DumpData = (Project, List[TaskStatus], Account, List[DataExportData], List[AccountDumpData])

  /** A project dump or status check visits every dump config of the project, so all pages are read. */
  private def findAllConfigs(projectId: UUID): IO[List[DataExportConfig]] =
    TgPages.readAll[IO, DataExportConfig](dataExportConfigService.findPaginatedByProjectId(projectId, _))

  def dumpProject(projectId: UUID, accountId: UUID): IO[Either[ProjectError, DataExportResult]] =
    dumpWithConfigs(projectId, accountId, findAllConfigs(projectId))

  def dumpConfig(dumpConfigId: UUID, accountId: UUID): IO[Either[ProjectError, DataExportResult]] =
    dataExportConfigService.findById(dumpConfigId).flatMap { config =>
      dumpWithConfigs(config.projectId, accountId, IO.pure(List(config)))
    }

  def checkStatus(projectId: UUID, accountId: UUID): IO[Either[ProjectError, DataExportStatusResult]] =
    checkStatusWithConfigs(projectId, accountId, findAllConfigs(projectId))

  def checkConfigStatus(dumpConfigId: UUID, accountId: UUID): IO[Either[ProjectError, DataExportStatusResult]] =
    dataExportConfigService.findById(dumpConfigId).flatMap { config =>
      checkStatusWithConfigs(config.projectId, accountId, IO.pure(List(config)))
    }

  private def dumpWithConfigs(
    projectId: UUID,
    accountId: UUID,
    configsIO: IO[List[DataExportConfig]]
  ): IO[Either[ProjectError, DataExportResult]] = {
    logger.info(s"Dumping tasks for ${sp(projectId)} requested by ${sp(accountId)}")
    getDumpData(projectId, accountId)
      .transact(transactor)
      .flatMap {
        case Left(error) => IO.pure(Left(error))
        case Right((project, taskStatuses, account, tasks, referencedAccounts)) =>
          configsIO.map { configs =>
            if (configs.isEmpty)
              Left(NoExportPathConfigured)
            else {
              val manifest = ProjectDumpManifest(
                project = project,
                taskStatuses = taskStatuses,
                dumpedBy = AccountDumpData.from(account),
                referencedAccounts = referencedAccounts,
                dumpedAt = tgClock.currentInstant,
                taskCount = tasks.size
              )
              val pathResults = configs.map { config =>
                Try(dataExportDiskWriter.write(config.exportPath, manifest, tasks)) match {
                  case Success(resolvedPath) => DataExportPathResult.success(resolvedPath)
                  case Failure(e)            => DataExportPathResult.error(config.exportPath, e.getMessage)
                }
              }
              Right(DataExportResult(tasks.size, pathResults))
            }
          }
      }
  }

  private def checkStatusWithConfigs(
    projectId: UUID,
    accountId: UUID,
    configsIO: IO[List[DataExportConfig]]
  ): IO[Either[ProjectError, DataExportStatusResult]] = {
    logger.info(s"Checking dump status for ${sp(projectId)} requested by ${sp(accountId)}")
    getDumpData(projectId, accountId)
      .transact(transactor)
      .flatMap {
        case Left(error) => IO.pure(Left(error))
        case Right((project, _, _, dbTasks, _)) =>
          configsIO.map { configs =>
            if (configs.isEmpty)
              Left(NoExportPathConfigured)
            else {
              val dbTasksById = dbTasks.map(t => t.task.id -> t).toMap
              val pathStatuses = configs.map(config => buildPathStatus(config.exportPath, project, dbTasksById))
              Right(DataExportStatusResult(pathStatuses))
            }
          }
      }
  }

  private def buildPathStatus(
    exportPath: String,
    project: Project,
    dbTasksById: Map[UUID, DataExportData]
  ): DataExportPathStatus = {
    val diskTasksById = dataExportDiskWriter.readDumpedTasks(exportPath, project)
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
          DataExportTaskStatus(taskId, taskName(taskId), DataExportSyncStatus.Malformed, List(parseError))
        case Right(diskData) =>
          val diffs = differences(dbTasksById(taskId), diskData)
          if (diffs.isEmpty) DataExportTaskStatus(taskId, taskName(taskId), DataExportSyncStatus.InSync, Nil)
          else DataExportTaskStatus(taskId, taskName(taskId), DataExportSyncStatus.OutOfSync, diffs)
      }
    }
    val missingFromDumpStatuses =
      missingFromDumpIds.toList.map(taskId =>
        DataExportTaskStatus(taskId, taskName(taskId), DataExportSyncStatus.MissingFromExport, Nil)
      )
    val missingFromDbStatuses =
      missingFromDbIds.toList.map(taskId =>
        DataExportTaskStatus(taskId, taskName(taskId), DataExportSyncStatus.MissingFromDb, Nil)
      )

    DataExportPathStatus(exportPath, commonStatuses ++ missingFromDumpStatuses ++ missingFromDbStatuses)
  }

  private def differences(db: DataExportData, disk: DataExportData): List[String] =
    List(
      Option.when(db.task != disk.task)("task fields differ"),
      Option.when(db.taskVersions != disk.taskVersions)("task versions differ"),
      Option.when(db.links != disk.links)("links differ"),
      Option.when(db.logEntries != disk.logEntries)("log entries differ")
    ).flatten

  private def getDumpData(projectId: UUID, accountId: UUID): ConnectionIO[Either[ProjectError, DumpData]] =
    for {
      assigned <- dataExportRepo.isAssigned(projectId, accountId)
      result <-
        if (!assigned)
          (NotAssignedToProject: ProjectError).asLeft[DumpData].pure[ConnectionIO]
        else
          for {
            projectAndStatuses <- dataExportRepo.getProjectAndTaskStatuses(projectId)
            account <- dataExportRepo.findAccountById(accountId)
            tasks <- dataExportRepo.getAllTasksForProject(projectId)
            referencedAccounts <- dataExportRepo.getReferencedAccounts(tasks)
          } yield (projectAndStatuses._1, projectAndStatuses._2, account, tasks, referencedAccounts)
            .asRight[ProjectError]
    } yield result

}
