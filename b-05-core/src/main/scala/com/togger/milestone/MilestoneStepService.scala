package com.togger.milestone

import cats.effect.IO
import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.logging.TgLogging
import com.togger.milestone.MilestoneStepError.{
  LastStepOfMilestone,
  MilestoneStepError,
  MilestoneStepNotFound,
  StepHasTasks,
  StepsDoNotMatchMilestone
}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.{ConnectionIO, Transactor}

import java.time.{Instant, LocalDate}
import java.util.UUID

class MilestoneStepService(
  milestoneStepRepo: MilestoneStepRepo,
  milestoneStepVersionRepo: MilestoneStepVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  private val MoveForNewStepComment = "Moved to make room for a new step"

  /** Appends the step, or inserts it at `position` and moves the later steps down by one. */
  def createMilestoneStep(
    milestoneStep: MilestoneStep,
    firstVersion: MilestoneStepVersion,
    position: Option[Int],
    now: Instant
  ): IO[MilestoneStepWithOneVersion] = {
    logger.info(s"Creating a ${sp(milestoneStep)}, with ${sp(firstVersion)} at ${sp(position)}")
    (for {
      current <- orderedLatestVersions(milestoneStep.milestoneId)
      insertAt = position.map(_.max(0)).filter(_ < current.size)
      savedStep <- milestoneStepRepo.insert(milestoneStep)
      savedVersion <- milestoneStepVersionRepo.insert(
        firstVersion.copy(
          milestoneStepId = savedStep.id,
          position = insertAt.getOrElse(nextPosition(current))
        )
      )
      _ <- insertAt.traverse_ { index =>
        renumber(current.take(index), 0, MoveForNewStepComment, now) *>
          renumber(current.drop(index), index + 1, MoveForNewStepComment, now)
      }
    } yield {
      logger.info(s"Milestone step created successfully ${sp(savedStep)} with ${sp(savedVersion)}")
      MilestoneStepWithOneVersion(savedStep, savedVersion)
    }).transact(transactor)
  }

  def saveNewVersion(
    milestoneStepId: UUID,
    newVersion: MilestoneStepVersion
  ): IO[Either[MilestoneStepError, MilestoneStepWithOneVersion]] =
    (for {
      milestoneStep <- milestoneStepRepo.findById(milestoneStepId)
      current <- milestoneStepVersionRepo.findLatestByMilestoneStepId(milestoneStepId)
      // The position is changed by reordering only; an update keeps it.
      savedVersion <- milestoneStepVersionRepo.insert(
        newVersion.copy(milestoneStepId = milestoneStepId, position = current.position)
      )
    } yield MilestoneStepWithOneVersion(milestoneStep, savedVersion))
      .transact(transactor)
      .map(_.asRight[MilestoneStepError])
      .recoverWith { case UnexpectedEnd => IO.pure(Left(MilestoneStepNotFound)) }

  /** One new version for every step whose position changes, all with the same change comment. */
  def reorder(
    milestoneId: UUID,
    orderedStepIds: List[UUID],
    changeComment: String,
    now: Instant
  ): IO[Either[MilestoneStepError, List[MilestoneStepWithOneVersion]]] =
    (for {
      current <- orderedLatestVersions(milestoneId)
      result <- reorderMatching(milestoneId, current, orderedStepIds, changeComment, now)
    } yield result).transact(transactor)

  private def reorderMatching(
    milestoneId: UUID,
    current: List[MilestoneStepVersion],
    orderedStepIds: List[UUID],
    changeComment: String,
    now: Instant
  ): ConnectionIO[Either[MilestoneStepError, List[MilestoneStepWithOneVersion]]] =
    if (current.map(_.milestoneStepId).toSet != orderedStepIds.toSet || orderedStepIds.distinct.size != current.size)
      (StepsDoNotMatchMilestone: MilestoneStepError).asLeft[List[MilestoneStepWithOneVersion]].pure[ConnectionIO]
    else {
      val byStepId = current.map(version => version.milestoneStepId -> version).toMap
      renumber(orderedStepIds.map(byStepId), 0, changeComment, now) *>
        findAllWithLatest(milestoneId).map(_.asRight[MilestoneStepError])
    }

  def getCurrent(milestoneStepId: UUID): IO[Either[MilestoneStepError, MilestoneStepWithOneVersion]] =
    (for {
      milestoneStep <- milestoneStepRepo.findById(milestoneStepId)
      latestVersion <- milestoneStepVersionRepo.findLatestByMilestoneStepId(milestoneStepId)
    } yield MilestoneStepWithOneVersion(milestoneStep, latestVersion))
      .transact(transactor)
      .map(_.asRight[MilestoneStepError])
      .recoverWith { case UnexpectedEnd => IO.pure(Left(MilestoneStepNotFound)) }

  /** The steps of every milestone of the project, with their current version, by milestone then position. */
  def findAllByProjectId(projectId: UUID): IO[List[MilestoneStepWithOneVersion]] =
    (for {
      steps <- milestoneStepRepo.findAllByProjectId(projectId)
      versions <- milestoneStepVersionRepo.findLatestByProjectId(projectId)
      stepsById = steps.map(step => step.id -> step).toMap
    } yield versions
      .flatMap(version => stepsById.get(version.milestoneStepId).map(MilestoneStepWithOneVersion(_, version)))
      .sortBy(step => (step.milestoneStep.milestoneId.toString, step.version.position)))
      .transact(transactor)

  /** The steps of the milestone ordered by their current position. */
  def findAllByMilestoneId(milestoneId: UUID): IO[List[MilestoneStepWithOneVersion]] =
    findAllWithLatest(milestoneId).transact(transactor)

  /** Step counts for the given milestones; a milestone without steps maps to [[MilestoneStepCounts.empty]]. */
  def countsByMilestoneIds(milestoneIds: List[UUID]): IO[Map[UUID, MilestoneStepCounts]] =
    if (milestoneIds.isEmpty) IO.pure(Map.empty)
    else
      milestoneStepVersionRepo
        .countByMilestoneIds(milestoneIds)
        .map(counts => milestoneIds.map(id => id -> counts.getOrElse(id, MilestoneStepCounts.empty)).toMap)
        .transact(transactor)

  /** The steps of the milestone (latest versions) that would lie outside the given range. */
  def findStepsOutsideRange(
    milestoneId: UUID,
    rangeStart: LocalDate,
    rangeEnd: LocalDate
  ): IO[List[MilestoneStepVersion]] =
    orderedLatestVersions(milestoneId)
      .map(MilestoneStepRange.stepsOutside(_, rangeStart, rangeEnd).toList)
      .transact(transactor)

  def listVersions(milestoneStepId: UUID, page: DbPageRequest): IO[PaginatedResponse[MilestoneStepVersion]] =
    milestoneStepVersionRepo.findPaginatedByMilestoneStepId(milestoneStepId, page).transact(transactor)

  /** Refuses to delete the last step of a milestone and a step that tasks are in (by their latest version). */
  def deleteMilestoneStep(id: UUID): IO[Either[MilestoneStepError, Int]] =
    (for {
      step <- milestoneStepRepo.findById(id)
      siblings <- milestoneStepRepo.findAllByMilestoneId(step.milestoneId)
      tasks <- milestoneStepRepo.countTasksByStepIds(List(id))
      result <-
        if (siblings.size <= 1) (LastStepOfMilestone: MilestoneStepError).asLeft[Int].pure[ConnectionIO]
        else if (tasks.get(id).exists(_.total > 0))
          (StepHasTasks(tasks(id).total): MilestoneStepError).asLeft[Int].pure[ConnectionIO]
        else milestoneStepRepo.deleteById(id).map(_.asRight[MilestoneStepError])
    } yield result).transact(transactor).recoverWith { case UnexpectedEnd => IO.pure(Left(MilestoneStepNotFound)) }

  /** The milestone of each step; a step that does not exist is absent from the result. */
  def findMilestoneIdsByStepIds(stepIds: Iterable[UUID]): IO[Map[UUID, UUID]] =
    milestoneStepRepo.findMilestoneIdsByStepIds(stepIds.toList).transact(transactor)

  /** The project of each step; a step that does not exist is absent from the result. */
  def findProjectIdsByStepIds(stepIds: Iterable[UUID]): IO[Map[UUID, UUID]] =
    milestoneStepRepo.findProjectIdsByStepIds(stepIds.toList).transact(transactor)

  /** The tasks in each step; a step without tasks maps to [[MilestoneStepTaskCounts.empty]]. */
  def taskCountsByStepIds(stepIds: Iterable[UUID]): IO[Map[UUID, MilestoneStepTaskCounts]] = {
    val ids = stepIds.toList.distinct
    milestoneStepRepo
      .countTasksByStepIds(ids)
      .map(counts => ids.map(id => id -> counts.getOrElse(id, MilestoneStepTaskCounts.empty)).toMap)
      .transact(transactor)
  }

  /** The tasks in any step of the milestone. */
  def countTasksOfMilestone(milestoneId: UUID): IO[Int] =
    milestoneStepRepo.countTasksOfMilestone(milestoneId).transact(transactor)

  private def nextPosition(ordered: List[MilestoneStepVersion]): Int =
    ordered.lastOption.map(_.position + 1).getOrElse(0)

  private def orderedLatestVersions(milestoneId: UUID): ConnectionIO[List[MilestoneStepVersion]] =
    milestoneStepVersionRepo.findLatestByMilestoneId(milestoneId).map(_.sortBy(_.position))

  private def findAllWithLatest(milestoneId: UUID): ConnectionIO[List[MilestoneStepWithOneVersion]] =
    for {
      steps <- milestoneStepRepo.findAllByMilestoneId(milestoneId)
      latest <- orderedLatestVersions(milestoneId)
      stepsById = steps.map(step => step.id -> step).toMap
    } yield latest.flatMap(version => stepsById.get(version.milestoneStepId).map(MilestoneStepWithOneVersion(_, version)))

  /** Gives `ordered` the positions `firstPosition`, `firstPosition + 1`, ... and versions only the ones that move. */
  private def renumber(
    ordered: List[MilestoneStepVersion],
    firstPosition: Int,
    comment: String,
    now: Instant
  ): ConnectionIO[Unit] =
    ordered.zipWithIndex
      .map { case (version, index) => version -> (firstPosition + index) }
      .filter { case (version, position) => version.position != position }
      .traverse_ { case (version, position) => milestoneStepVersionRepo.insert(version.movedTo(position, comment, now)) }

}
