package com.togger.task

import cats.effect.IO
import cats.implicits._
import com.togger.logging.TgLogging
import com.togger.task.TaskError.{CannotDeleteLastVersion, CannotEditLatestVersion, TaskError, VersionNotFound}
import doobie.Transactor
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd

import java.util.UUID

class TaskVersionService(
  taskVersionRepo: TaskVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  def listVersions(taskId: UUID): IO[List[TaskVersion]] =
    taskVersionRepo.findByTaskId(taskId).transact(transactor)

  def updateVersionContent(
    version: UUID,
    name: String,
    description: String,
    assignedTo: Option[UUID],
    closedBy: Option[UUID],
    taskStatusId: UUID,
    milestone: Option[UUID] = None
  ): IO[Either[TaskError, TaskVersion]] =
    (for {
      existingVersion <- taskVersionRepo.findByVersion(version)
      latestVersion <- taskVersionRepo.findLatestByTaskId(existingVersion.taskId)
      result <-
        if (existingVersion.version == latestVersion.version)
          (CannotEditLatestVersion: TaskError).asLeft[TaskVersion].pure[doobie.ConnectionIO]
        else
          taskVersionRepo
            .updateContent(
              existingVersion.withNewContent(name, description, assignedTo, closedBy, taskStatusId, milestone)
            )
            .map(_.asRight[TaskError])
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

  def deleteVersion(version: UUID): IO[Either[TaskError, Int]] =
    (for {
      existingVersion <- taskVersionRepo.findByVersion(version)
      latestVersion <- taskVersionRepo.findLatestByTaskId(existingVersion.taskId)
      result <-
        if (existingVersion.version != latestVersion.version)
          taskVersionRepo.deleteByVersion(version).map(_.asRight[TaskError])
        else
          taskVersionRepo
            .countByTaskId(existingVersion.taskId)
            .flatMap(count =>
              if (count <= 1)
                (CannotDeleteLastVersion: TaskError).asLeft[Int].pure[doobie.ConnectionIO]
              else
                taskVersionRepo.deleteByVersion(version).map(_.asRight[TaskError])
            )
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

}
