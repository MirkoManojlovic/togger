package com.togger.milestone

import doobie.ConnectionIO

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A step of a milestone for the tests of tasks, which are in a step. */
object MilestoneStepFixtures {

  private val stepRepo = new MilestoneStepRepoImpl
  private val stepVersionRepo = new MilestoneStepVersionRepoImpl

  def insertStep(
    milestoneId: UUID,
    at: Instant,
    name: String = "Step",
    position: Int = 0,
    startDate: LocalDate = LocalDate.of(2026, 10, 1),
    dueDate: LocalDate = LocalDate.of(2026, 10, 31)
  ): ConnectionIO[MilestoneStep] =
    for {
      step <- stepRepo.insert(MilestoneStep(UUID.randomUUID(), milestoneId, at))
      _ <- stepVersionRepo.insert(
        MilestoneStepVersion(
          version = UUID.randomUUID(),
          milestoneStepId = step.id,
          name = name,
          description = "",
          status = MilestoneStepStatus.Planned,
          position = position,
          startDate = startDate,
          dueDate = dueDate,
          endDate = None,
          createdAt = at,
          updatedAt = at,
          changeComment = ""
        )
      )
    } yield step
}
