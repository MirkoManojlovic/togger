package com.togger.milestone

import java.time.Instant
import java.util.UUID

case class MilestoneDate(
  id: UUID,
  milestoneId: UUID,
  createdAt: Instant
)

case class MilestoneDateVersion(
  version: UUID,
  milestoneDateId: UUID,
  name: String,
  description: String,
  date: Instant,
  createdAt: Instant,
  updatedAt: Instant
) {

  def withNewContent(name: String, description: String, date: Instant): MilestoneDateVersion =
    this.copy(name = name, description = description, date = date)

}

case class MilestoneDateWithOneVersion(milestoneDate: MilestoneDate, version: MilestoneDateVersion)
case class MilestoneDateWithVersions(milestoneDate: MilestoneDate, versions: Seq[MilestoneDateVersion]) {
  lazy val latestVersion: MilestoneDateVersion = versions.head
}
