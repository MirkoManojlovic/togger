package com.togger.timebox

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.logging.TgLogging
import com.togger.task.{TaskRepo, TaskVersion, TaskVersionRepo}
import com.togger.timebox.TimeboxError.{TaskNotFound, TaskNotInProject, TimeboxError, TimeboxNotFound}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.{ConnectionIO, Transactor}

import java.time.{Instant, LocalDate}
import java.util.UUID

class TimeboxService(
  timeboxRepo: TimeboxRepo,
  timeboxVersionRepo: TimeboxVersionRepo,
  taskRepo: TaskRepo,
  taskVersionRepo: TaskVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  private type Result[A] = EitherT[ConnectionIO, TimeboxError, A]

  def createTimebox(
    projectId: UUID,
    startDate: LocalDate,
    endDate: LocalDate,
    description: Option[String],
    taskIds: Seq[UUID],
    now: Instant
  ): IO[Either[TimeboxError, TimeboxWithOneVersion]] = {
    logger.info(s"Creating a timebox for ${sp(projectId)} with ${sp(taskIds)}")
    (for {
      taskVersions <- resolveTaskVersions(projectId, taskIds)
      timebox <- lift(timeboxRepo.insert(Timebox(UUID.randomUUID(), projectId, now)))
      saved <- lift(insertVersion(timebox.id, startDate, endDate, description, taskVersions, now))
    } yield TimeboxWithOneVersion(timebox, saved)).value.transact(transactor)
  }

  def saveNewVersion(
    timeboxId: UUID,
    startDate: LocalDate,
    endDate: LocalDate,
    description: Option[String],
    taskIds: Seq[UUID],
    now: Instant
  ): IO[Either[TimeboxError, TimeboxWithOneVersion]] =
    (for {
      timebox <- findTimebox(timeboxId)
      taskVersions <- resolveTaskVersions(timebox.projectId, taskIds)
      saved <- lift(insertVersion(timeboxId, startDate, endDate, description, taskVersions, now))
    } yield TimeboxWithOneVersion(timebox, saved)).value.transact(transactor)

  def getCurrent(timeboxId: UUID): IO[Either[TimeboxError, TimeboxWithOneVersion]] =
    (for {
      timebox <- findTimebox(timeboxId)
      latest <- lift(timeboxVersionRepo.findLatestByTimeboxId(timeboxId).flatMap(withTasks))
    } yield TimeboxWithOneVersion(timebox, latest)).value.transact(transactor)

  def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[TimeboxWithOneVersion]] =
    (for {
      timeboxesPage <- timeboxRepo.findPaginatedByProjectId(projectId, page)
      combined <- timeboxesPage.items.traverse(timebox =>
        timeboxVersionRepo
          .findLatestByTimeboxId(timebox.id)
          .flatMap(withTasks)
          .map(TimeboxWithOneVersion(timebox, _))
      )
    } yield timeboxesPage.copy(items = combined)).transact(transactor)

  def listVersions(timeboxId: UUID): IO[List[TimeboxVersionWithTasks]] =
    timeboxVersionRepo.findByTimeboxId(timeboxId).flatMap(_.traverse(withTasks)).transact(transactor)

  def deleteTimebox(id: UUID): IO[Int] =
    timeboxRepo.deleteById(id).transact(transactor)

  private def insertVersion(
    timeboxId: UUID,
    startDate: LocalDate,
    endDate: LocalDate,
    description: Option[String],
    taskVersions: List[TaskVersion],
    now: Instant
  ): ConnectionIO[TimeboxVersionWithTasks] =
    for {
      saved <- timeboxVersionRepo.insert(TimeboxVersion(UUID.randomUUID(), timeboxId, startDate, endDate, description, now))
      _ <- timeboxVersionRepo.insertPlannedTasks(saved.version, taskVersions.map(_.version))
    } yield TimeboxVersionWithTasks(saved, taskVersions)

  private def withTasks(version: TimeboxVersion): ConnectionIO[TimeboxVersionWithTasks] =
    timeboxVersionRepo.findPlannedTasks(version.version).map(TimeboxVersionWithTasks(version, _))

  private def lift[A](io: ConnectionIO[A]): Result[A] = EitherT.liftF(io)

  private def findTimebox(timeboxId: UUID): Result[Timebox] =
    EitherT(timeboxRepo.findById(timeboxId).attempt.map {
      case Right(timebox)      => Right(timebox)
      case Left(UnexpectedEnd) => Left(TimeboxNotFound: TimeboxError)
      case Left(other)         => throw other
    })

  /** The latest version of each task is what gets planned, so later task edits don't change the plan. */
  private def resolveTaskVersions(projectId: UUID, taskIds: Seq[UUID]): Result[List[TaskVersion]] =
    taskIds.distinct.toList.traverse { taskId =>
      EitherT(taskRepo.findById(taskId).attempt.flatMap {
        case Left(UnexpectedEnd) => (Left(TaskNotFound(taskId)): Either[TimeboxError, TaskVersion]).pure[ConnectionIO]
        case Left(other)         => throw other
        case Right(task) if task.projectId != projectId =>
          (Left(TaskNotInProject(taskId)): Either[TimeboxError, TaskVersion]).pure[ConnectionIO]
        case Right(_) =>
          taskVersionRepo.findLatestByTaskId(taskId).map(Right(_): Either[TimeboxError, TaskVersion])
      })
    }

}
