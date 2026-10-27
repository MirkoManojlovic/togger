package com.togger.milestone

import java.time.{Instant, LocalDate}
import java.util.UUID

case class MilestoneSummary(
  id: UUID,
  milestoneId: UUID,
  createdAt: Instant
)

case class MilestoneSummaryVersion(
  version: UUID,
  milestoneSummaryId: UUID,
  summary: String,
  startDate: LocalDate,
  endDate: LocalDate,
  createdAt: Instant,
  updatedAt: Instant
) {

  def withNewContent(summary: String, startDate: LocalDate, endDate: LocalDate): MilestoneSummaryVersion =
    this.copy(summary = summary, startDate = startDate, endDate = endDate)

}

case class MilestoneSummaryWithOneVersion(milestoneSummary: MilestoneSummary, version: MilestoneSummaryVersion)
case class MilestoneSummaryWithVersions(milestoneSummary: MilestoneSummary, versions: Seq[MilestoneSummaryVersion]) {
  lazy val latestVersion: MilestoneSummaryVersion = versions.head
}
