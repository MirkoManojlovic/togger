package com.togger.milestone

import cats.effect.IO
import cats.implicits._
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

/** Creates a milestone together with its first steps in one transaction, so a failing step leaves no milestone. */
class MilestoneWithStepsService(
  milestoneRepo: MilestoneRepo,
  milestoneVersionRepo: MilestoneVersionRepo,
  milestoneStepRepo: MilestoneStepRepo,
  milestoneStepVersionRepo: MilestoneStepVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  /** The steps are stored in the given order; their ids and positions are set here. Without steps the milestone gets
    * the [[DefaultMilestoneStep]] (Shelf), which is always the first step.
    */
  def create(
    milestone: Milestone,
    firstVersion: MilestoneVersion,
    steps: List[(MilestoneStep, MilestoneStepVersion)]
  ): IO[(MilestoneWithOneVersion, List[MilestoneStepWithOneVersion])] = {
    logger.info(s"Creating a ${sp(milestone)}, with ${sp(firstVersion)} and ${sp(steps.size)} steps")
    (for {
      savedMilestone <- milestoneRepo.insert(milestone)
      savedVersion <- milestoneVersionRepo.insert(firstVersion.copy(milestoneId = savedMilestone.id))
      savedSteps <- (shelfStep(milestone, firstVersion) +: steps).zipWithIndex.traverse { case ((step, stepVersion), position) =>
        for {
          savedStep <- milestoneStepRepo.insert(step.copy(milestoneId = savedMilestone.id))
          savedStepVersion <- milestoneStepVersionRepo.insert(
            stepVersion.copy(milestoneStepId = savedStep.id, position = position)
          )
        } yield MilestoneStepWithOneVersion(savedStep, savedStepVersion)
      }
    } yield MilestoneWithOneVersion(savedMilestone, savedVersion) -> savedSteps).transact(transactor)
  }

  private def shelfStep(milestone: Milestone, firstVersion: MilestoneVersion): (MilestoneStep, MilestoneStepVersion) = {
    val step = MilestoneStep(UUID.randomUUID(), milestone.id, milestone.createdAt)
    step -> MilestoneStepVersion(
      version = UUID.randomUUID(),
      milestoneStepId = step.id,
      name = DefaultMilestoneStep.Shelf.stepName,
      description = DefaultMilestoneStep.Shelf.description,
      status = MilestoneStepStatus.Planned,
      position = 0,
      startDate = firstVersion.startDate,
      dueDate = firstVersion.dueDate,
      endDate = None,
      createdAt = firstVersion.createdAt,
      updatedAt = firstVersion.createdAt,
      changeComment = ""
    )
  }
}
