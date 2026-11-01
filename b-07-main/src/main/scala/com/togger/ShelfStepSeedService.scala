package com.togger

import cats.effect.IO
import cats.implicits._
import com.togger.logging.TgLogging
import com.togger.milestone.{
  DefaultMilestoneStep,
  MilestoneStep,
  MilestoneStepRepoImpl,
  MilestoneStepStatus,
  MilestoneStepVersion,
  MilestoneStepVersionRepoImpl,
  MilestoneWithoutShelf
}
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._

import java.time.Instant
import java.util.UUID

/** Gives every milestone without one the Shelf step, at the top, above its other steps, with the dates of the milestone. New
  * milestones get theirs on creation; this covers the milestones that existed before the Shelf step.
  */
class ShelfStepSeedService(transactor: Transactor[IO]) extends TgLogging {
  private val MoveComment = "Moved down to make room for the Shelf step"
  private val stepRepo = new MilestoneStepRepoImpl
  private val stepVersionRepo = new MilestoneStepVersionRepoImpl

  def run: IO[Unit] =
    (for {
      milestones <- stepRepo.findMilestonesWithoutShelf
      _ <- milestones.traverse_(addShelf)
    } yield milestones.size).transact(transactor).flatMap(added => IO(logger.info(s"Added $added Shelf steps")))

  private def addShelf(milestone: MilestoneWithoutShelf): ConnectionIO[Unit] = {
    val now = Instant.now()
    for {
      existing <- stepVersionRepo.findLatestByMilestoneId(milestone.milestoneId)
      _ <- existing.traverse_(version => stepVersionRepo.insert(version.movedTo(version.position + 1, MoveComment, now)))
      step <- stepRepo.insert(MilestoneStep(UUID.randomUUID(), milestone.milestoneId, now))
      _ <- stepVersionRepo.insert(
        MilestoneStepVersion(
          version = UUID.randomUUID(),
          milestoneStepId = step.id,
          name = DefaultMilestoneStep.Shelf.stepName,
          description = DefaultMilestoneStep.Shelf.description,
          status = MilestoneStepStatus.Planned,
          position = 0,
          startDate = milestone.startDate,
          dueDate = milestone.dueDate,
          endDate = None,
          createdAt = now,
          updatedAt = now,
          changeComment = ""
        )
      )
    } yield ()
  }
}
