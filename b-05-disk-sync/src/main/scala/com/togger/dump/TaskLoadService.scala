package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.logging.TgLogging
import com.togger.project.ProjectError.{DumpManifestMissing, NotAssignedToProject, ProjectError}
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

  def loadProject(projectId: UUID, accountId: UUID, projectRootPath: String): IO[Either[ProjectError, TaskLoadResult]] = {
    logger.info(s"Loading tasks for ${sp(projectId)} from ${sp(projectRootPath)} requested by ${sp(accountId)}")
    taskDumpRepo.isAssigned(projectId, accountId).transact(transactor).flatMap { assigned =>
      if (!assigned) IO.pure(Left(NotAssignedToProject))
      else
        taskDumpDiskWriter.resolveLatestDumpFolder(projectRootPath) match {
          case None => IO.pure(Left(DumpManifestMissing))
          case Some(dumpPath) =>
            taskDumpDiskWriter.readManifest(dumpPath) match {
              case Left(_) => IO.pure(Left(DumpManifestMissing))
              case Right(manifest) =>
                taskLoadRepo
                  .resolveAccountMappings(manifest.dumpedBy :: manifest.referencedAccounts)
                  .transact(transactor)
                  .flatMap {
                    case Left(error) => IO.pure(Left(error))
                    case Right(accountIdMap) =>
                      taskLoadRepo
                        .prepareLoad(projectId, manifest)
                        .transact(transactor)
                        .flatMap { statusIdMap =>
                          val diskTasksById = taskDumpDiskWriter.readDumpedTasksAtPath(dumpPath)
                          diskTasksById.toList
                            .traverse {
                              case (taskId, Left(parseError)) =>
                                IO.pure(
                                  TaskLoadTaskResult(
                                    taskId,
                                    None,
                                    TaskLoadTaskStatus.Malformed,
                                    0,
                                    0,
                                    0,
                                    Some(parseError)
                                  )
                                )
                              case (taskId, Right(diskData)) =>
                                val remapped = remapForTarget(diskData, projectId, statusIdMap, accountIdMap)
                                taskLoadRepo
                                  .mergeTask(remapped)
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
            }
        }
    }
  }

  private def remapForTarget(
    diskData: TaskDumpData,
    projectIdDb: UUID,
    taskStatusIdMap: Map[UUID, UUID],
    accountIdMap: Map[UUID, UUID]
  ): TaskDumpData = {
    def remapAccount(id: UUID): UUID = accountIdMap.getOrElse(id, id)
    def remapAccountOpt(id: Option[UUID]): Option[UUID] = id.map(remapAccount)

    diskData.copy(
      task = diskData.task.copy(projectId = projectIdDb, createdBy = remapAccount(diskData.task.createdBy)),
      taskVersions = diskData.taskVersions.map(v =>
        v.copy(
          taskStatusId = taskStatusIdMap.getOrElse(v.taskStatusId, v.taskStatusId),
          assignedTo = remapAccountOpt(v.assignedTo),
          closedBy = remapAccountOpt(v.closedBy)
        )
      ),
      logEntries = diskData.logEntries.map(le =>
        le.copy(
          logEntry = le.logEntry.copy(createdBy = remapAccount(le.logEntry.createdBy)),
          versions = le.versions.map(v => v.copy(createdBy = remapAccount(v.createdBy)))
        )
      )
    )
  }

}
