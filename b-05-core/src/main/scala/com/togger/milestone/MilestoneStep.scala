package com.togger.milestone

import java.time.{Instant, LocalDate}
import java.util.UUID

case class MilestoneStep(
  id: UUID,
  milestoneId: UUID,
  createdAt: Instant
)

/** Append-only: every change, including a move to another position, is a new version with a change comment. */
case class MilestoneStepVersion(
  version: UUID,
  milestoneStepId: UUID,
  name: String,
  description: String,
  status: MilestoneStepStatus,
  position: Int,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate],
  createdAt: Instant,
  updatedAt: Instant,
  changeComment: String
) {

  def movedTo(newPosition: Int, comment: String, now: Instant): MilestoneStepVersion =
    copy(version = UUID.randomUUID(), position = newPosition, changeComment = comment, createdAt = now, updatedAt = now)

}

/** How many steps a milestone has and how many of them are closed (Done or Abandoned). */
case class MilestoneStepCounts(total: Int, closed: Int)

object MilestoneStepCounts {
  val empty: MilestoneStepCounts = MilestoneStepCounts(0, 0)

  def fromStatuses(statuses: Seq[MilestoneStepStatus]): MilestoneStepCounts =
    MilestoneStepCounts(statuses.size, statuses.count(_.isClosing))
}

object MilestoneStepRange {

  /** The steps whose start or due date would fall outside the given milestone range. */
  def stepsOutside(
    steps: Seq[MilestoneStepVersion],
    rangeStart: LocalDate,
    rangeEnd: LocalDate
  ): Seq[MilestoneStepVersion] =
    steps.filter(step => step.startDate.isBefore(rangeStart) || step.dueDate.isAfter(rangeEnd))

}

case class MilestoneStepWithOneVersion(milestoneStep: MilestoneStep, version: MilestoneStepVersion)
case class MilestoneStepWithVersions(milestoneStep: MilestoneStep, versions: Seq[MilestoneStepVersion]) {
  lazy val latestVersion: MilestoneStepVersion = versions.head
}
