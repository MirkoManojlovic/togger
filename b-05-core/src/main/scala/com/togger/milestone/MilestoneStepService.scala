package com.togger.milestone

import cats.effect.IO
import cats.implicits._
import com.togger.common.DbPageRequest
import com.togger.common.PaginatedResponse
import com.togger.logging.TgLogging
import com.togger.milestone.MilestoneStepError.DefaultStepCannotBeDeleted
import com.togger.milestone.MilestoneStepError.DefaultStepCannotBeEdited
import com.togger.milestone.MilestoneStepError.DefaultStepNameReserved
import com.togger.milestone.MilestoneStepError.LastStepOfMilestone
import com.togger.milestone.MilestoneStepError.MilestoneStepError
import com.togger.milestone.MilestoneStepError.MilestoneStepNotFound
import com.togger.milestone.MilestoneStepError.StepHasTasks
import com.togger.milestone.MilestoneStepError.StepsDoNotMatchMilestone
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.Transactor
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class MilestoneStepService(
  milestoneStepRepo: MilestoneStepRepo,
  milestoneStepVersionRepo: MilestoneStepVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  private val MoveForNewStepComment = "Moved to make room for a new step"

  /** Appends the step as a new last stage, or inserts it as a new stage at `position` and moves the stages from there
    * down by one. When `parallel`, the step joins the last stage (or the stage at `position`) instead, and nothing
    * moves. A step never joins the stage of a default step.
    */
  def createMilestoneStep(
    milestoneStep: MilestoneStep,
    firstVersion: MilestoneStepVersion,
    position: Option[Int],
    parallel: Boolean,
    now: Instant
  ): IO[MilestoneStepWithOneVersion] = {
    logger.info(s"Creating a ${sp(milestoneStep)}, with ${sp(firstVersion)} at ${sp(position)}, parallel $parallel")
    (for {
      current <- orderedLatestVersions(milestoneStep.milestoneId)
      staged = current.map(asStaged)
      placement = MilestoneStepStages.placementOf(staged, position, parallel)
      stage = MilestoneStepStages.positionOf(placement)
      savedStep <- milestoneStepRepo.insert(milestoneStep)
      savedVersion <- milestoneStepVersionRepo.insert(
        firstVersion.copy(milestoneStepId = savedStep.id, position = stage)
      )
      moves = MilestoneStepStages.movesFor(staged, placement).toMap
      _ <- moveTo(
        current.flatMap(step => moves.get(step.milestoneStepId).map(step -> _)),
        MoveForNewStepComment,
        now
      )
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
      result <-
        if (DefaultMilestoneStep.isDefaultName(current.name))
          (DefaultStepCannotBeEdited(current.name): MilestoneStepError)
            .asLeft[MilestoneStepWithOneVersion]
            .pure[ConnectionIO]
        else if (DefaultMilestoneStep.isDefaultName(newVersion.name))
          (DefaultStepNameReserved(newVersion.name.trim): MilestoneStepError)
            .asLeft[MilestoneStepWithOneVersion]
            .pure[ConnectionIO]
        else
          // The position is changed by reordering only; an update keeps it.
          milestoneStepVersionRepo
            .insert(newVersion.copy(milestoneStepId = milestoneStepId, position = current.position))
            .map(saved => MilestoneStepWithOneVersion(milestoneStep, saved).asRight[MilestoneStepError])
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd => IO.pure(Left(MilestoneStepNotFound)) }

  /** `stages` is the complete new order: the outer list is the sequence of stages, the steps of an inner list run in
    * parallel. One new version for every step whose position changes, all with the same change comment.
    */
  def reorder(
    milestoneId: UUID,
    stages: List[List[UUID]],
    changeComment: String,
    now: Instant
  ): IO[Either[MilestoneStepError, List[MilestoneStepWithOneVersion]]] =
    (for {
      current <- orderedLatestVersions(milestoneId)
      result <- reorderMatching(milestoneId, current, stages, changeComment, now)
    } yield result).transact(transactor)

  private def reorderMatching(
    milestoneId: UUID,
    current: List[MilestoneStepVersion],
    stages: List[List[UUID]],
    changeComment: String,
    now: Instant
  ): ConnectionIO[Either[MilestoneStepError, List[MilestoneStepWithOneVersion]]] = {
    val (defaults, movable) = current.partition(isDefault)
    // the default steps are not ordered: they stay at the top, alone, whatever the request says
    val defaultIds = defaults.map(_.milestoneStepId).toSet
    val requestedStages = stages.map(_.filterNot(defaultIds.contains)).filter(_.nonEmpty)
    val requested = requestedStages.flatten
    if (movable.map(_.milestoneStepId).toSet != requested.toSet || requested.distinct.size != movable.size)
      (StepsDoNotMatchMilestone: MilestoneStepError).asLeft[List[MilestoneStepWithOneVersion]].pure[ConnectionIO]
    else {
      val positions = MilestoneStepStages.reorderedPositions(current.map(asStaged), requestedStages)
      moveTo(current.map(step => step -> positions(step.milestoneStepId)), changeComment, now) *>
        findAllWithLatest(milestoneId).map(_.asRight[MilestoneStepError])
    }
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
      // a default step cannot be edited, so it is not held to the range
      .map(_.filterNot(step => DefaultMilestoneStep.isDefaultName(step.name)))
      .map(MilestoneStepRange.stepsOutside(_, rangeStart, rangeEnd).toList)
      .transact(transactor)

  def listVersions(milestoneStepId: UUID, page: DbPageRequest): IO[PaginatedResponse[MilestoneStepVersion]] =
    milestoneStepVersionRepo.findPaginatedByMilestoneStepId(milestoneStepId, page).transact(transactor)

  /** Refuses to delete a default step (Shelf), the last step of a milestone and a step that tasks are in (by their
    * latest version).
    */
  def deleteMilestoneStep(id: UUID): IO[Either[MilestoneStepError, Int]] =
    (for {
      step <- milestoneStepRepo.findById(id)
      latest <- milestoneStepVersionRepo.findLatestByMilestoneStepId(id)
      siblings <- milestoneStepRepo.findAllByMilestoneId(step.milestoneId)
      tasks <- milestoneStepRepo.countTasksByStepIds(List(id))
      result <-
        if (DefaultMilestoneStep.isDefaultName(latest.name))
          (DefaultStepCannotBeDeleted(latest.name): MilestoneStepError).asLeft[Int].pure[ConnectionIO]
        else if (siblings.size <= 1) (LastStepOfMilestone: MilestoneStepError).asLeft[Int].pure[ConnectionIO]
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

  private def asStaged(version: MilestoneStepVersion): MilestoneStepStages.Staged =
    MilestoneStepStages.Staged(version.milestoneStepId, version.position, isDefault(version))

  private def isDefault(version: MilestoneStepVersion): Boolean = DefaultMilestoneStep.isDefaultName(version.name)

  private def orderedLatestVersions(milestoneId: UUID): ConnectionIO[List[MilestoneStepVersion]] =
    milestoneStepVersionRepo.findLatestByMilestoneId(milestoneId).map(_.sortBy(_.position))

  private def findAllWithLatest(milestoneId: UUID): ConnectionIO[List[MilestoneStepWithOneVersion]] =
    for {
      steps <- milestoneStepRepo.findAllByMilestoneId(milestoneId)
      latest <- orderedLatestVersions(milestoneId)
      stepsById = steps.map(step => step.id -> step).toMap
    } yield latest.flatMap(version =>
      stepsById.get(version.milestoneStepId).map(MilestoneStepWithOneVersion(_, version))
    )

  /** Gives every step its target position and versions only the ones that move. */
  private def moveTo(targets: List[(MilestoneStepVersion, Int)], comment: String, now: Instant): ConnectionIO[Unit] =
    targets
      .filter { case (version, position) => version.position != position }
      .traverse_ { case (version, position) =>
        milestoneStepVersionRepo.insert(version.movedTo(position, comment, now))
      }

}
