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

  /** The ids of the steps are set here. The `position` of each given step is the stage it asked for: steps with the
    * same stage run in parallel and the stages are numbered, in the order of the requested ones, after the stage of the
    * [[DefaultMilestoneStep]] (Shelf), which is always alone in the first one. Without steps the milestone only gets
    * the Shelf step.
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
      savedSteps <- (shelfStep(milestone, firstVersion) +: steps).zip(0 +: stagePositions(steps)).traverse {
        case ((step, stepVersion), position) =>
          for {
            savedStep <- milestoneStepRepo.insert(step.copy(milestoneId = savedMilestone.id))
            savedStepVersion <- milestoneStepVersionRepo.insert(
              stepVersion.copy(milestoneStepId = savedStep.id, position = position)
            )
          } yield MilestoneStepWithOneVersion(savedStep, savedStepVersion)
      }
    } yield MilestoneWithOneVersion(savedMilestone, savedVersion) -> savedSteps).transact(transactor)
  }

  /** The positions after the Shelf step: the requested stages numbered one after the other, from 1. */
  private def stagePositions(steps: List[(MilestoneStep, MilestoneStepVersion)]): List[Int] = {
    val stageNumbers = steps.map { case (_, version) => version.position }.distinct.sorted.zipWithIndex.toMap
    steps.map { case (_, version) => stageNumbers(version.position) + 1 }
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
