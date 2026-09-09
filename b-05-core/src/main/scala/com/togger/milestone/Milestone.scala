package com.togger.milestone

import java.time.Instant
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
  createdAt: Instant,
  updatedAt: Instant
) {

  def withNewContent(name: String, description: String): MilestoneVersion =
    this.copy(name = name, description = description)

}

case class MilestoneWithOneVersion(milestone: Milestone, version: MilestoneVersion)
case class MilestoneWithVersions(milestone: Milestone, versions: Seq[MilestoneVersion]) {
  lazy val latestVersion: MilestoneVersion = versions.head
}
