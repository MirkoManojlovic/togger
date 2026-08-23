package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.logging.TgLogging
import com.togger.project.ProjectError.{DumpManifestMissing, NotAssignedToProject, ProjectError, ProjectMappingConflict}
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

  def loadProject(projectId: UUID, accountId: UUID, dumpPath: String): IO[Either[ProjectError, TaskLoadOutcome]] = {
    logger.info(s"Loading tasks for ${sp(projectId)} from ${sp(dumpPath)} requested by ${sp(accountId)}")
    taskDumpRepo.isAssigned(projectId, accountId).transact(transactor).flatMap { assigned =>
      if (!assigned) IO.pure(Left(NotAssignedToProject))
      else
        taskDumpDiskWriter.readManifest(dumpPath) match {
          case Left(_) => IO.pure(Left(DumpManifestMissing))
          case Right(manifest) =>
            taskLoadRepo
              .prepareLoad(projectId, manifest)
              .transact(transactor)
              .flatMap {
                case MappingResolution.Required =>
                  IO.pure(Right(TaskLoadOutcome.MappingRequired(manifest.project.id, manifest.project.name)))
                case MappingResolution.Conflict =>
                  IO.pure(Left(ProjectMappingConflict))
                case MappingResolution.Resolved(statusIdMap) =>
                  val diskTasksById = taskDumpDiskWriter.readDumpedTasksAtPath(dumpPath)
                  diskTasksById.toList
                    .traverse {
                      case (taskId, Left(parseError)) =>
                        IO.pure(TaskLoadTaskResult(taskId, None, TaskLoadTaskStatus.Malformed, 0, 0, 0, Some(parseError)))
                      case (taskId, Right(diskData)) =>
                        val remapped = remapForTarget(diskData, projectId, statusIdMap)
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
                    .map(results => Right(TaskLoadOutcome.Loaded(TaskLoadResult(dumpPath, results.size, results))))
              }
        }
    }
  }

  private def remapForTarget(
    diskData: TaskDumpData,
    projectIdDb: UUID,
    taskStatusIdMap: Map[UUID, UUID]
  ): TaskDumpData =
    diskData.copy(
      task = diskData.task.copy(projectId = projectIdDb),
      taskVersions = diskData.taskVersions.map(v =>
        v.copy(taskStatusId = taskStatusIdMap.getOrElse(v.taskStatusId, v.taskStatusId))
      )
    )

}
