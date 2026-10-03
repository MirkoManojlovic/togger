package com.togger.milestone

import java.time.{Instant, LocalDate}
import java.util.UUID

case class Milestone(
  id: UUID,
  projectId: UUID,
  createdAt: Instant
)

case class MilestoneVersion(
  version: UUID,
  milestoneId: UUID,
  name: String,
  description: String,
  status: MilestoneStatus = MilestoneStatus.InProgress,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate] = None,
  createdAt: Instant,
  updatedAt: Instant
) {

  def withNewContent(
    name: String,
    description: String,
    status: MilestoneStatus = MilestoneStatus.InProgress,
    startDate: LocalDate,
    dueDate: LocalDate,
    endDate: Option[LocalDate] = None
  ): MilestoneVersion =
    this.copy(
      name = name,
      description = description,
      status = status,
      startDate = startDate,
      dueDate = dueDate,
      endDate = endDate
    )

}

case class MilestoneWithOneVersion(milestone: Milestone, version: MilestoneVersion)
case class MilestoneWithVersions(milestone: Milestone, versions: Seq[MilestoneVersion]) {
  lazy val latestVersion: MilestoneVersion = versions.head
}
