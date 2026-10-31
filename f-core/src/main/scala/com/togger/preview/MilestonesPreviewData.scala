package com.togger.preview

import com.togger.dto.{FindMilestoneDateResp, FindMilestoneResp, FindMilestoneSummaryResp}
import com.togger.milestone.MilestoneStatus
import io.circe.generic.auto._

import java.time.{Instant, LocalDate}
import java.util.UUID

/** The status is kept as its int value, so that no codec of the enum is needed in the storage. */
case class PreviewMilestone(
  id: UUID,
  projectId: UUID,
  createdAt: Instant,
  latestVersion: UUID,
  name: String,
  description: String,
  statusValue: Int,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate],
  updatedAt: Instant
) {
  def status: MilestoneStatus = MilestoneStatus.withValue(statusValue)

  def toDto: FindMilestoneResp =
    FindMilestoneResp(
      id,
      projectId,
      createdAt,
      latestVersion,
      name,
      description,
      status,
      startDate,
      dueDate,
      endDate,
      updatedAt
    )
}

/** Milestones with their dates and summaries; only the current content is kept, a save gets a new version id. */
class MilestonesPreviewData(db: PreviewDb, projects: ProjectsPreviewData) {

  val milestones: PreviewCollection[PreviewMilestone] =
    db.collection[PreviewMilestone](MilestonesPreviewData.Name, _.id)

  val dates: PreviewCollection[FindMilestoneDateResp] =
    db.collection[FindMilestoneDateResp](MilestonesPreviewData.DatesName, _.id)

  val summaries: PreviewCollection[FindMilestoneSummaryResp] =
    db.collection[FindMilestoneSummaryResp](MilestonesPreviewData.SummariesName, _.id)

  private var milestoneDeletionHooks: List[UUID => Unit] = List.empty

  /** Lets the tasks domain detach the tasks of a deleted milestone. */
  def onMilestoneDeleted(hook: UUID => Unit): Unit = milestoneDeletionHooks = milestoneDeletionHooks :+ hook

  projects.onProjectDeleted(projectId => milestones.filter(_.projectId == projectId).foreach(m => deleteMilestone(m.id)))

  def deleteMilestone(id: UUID): Boolean = {
    milestoneDeletionHooks.foreach(_(id))
    dates.deleteWhere(_.milestoneId == id)
    summaries.deleteWhere(_.milestoneId == id)
    milestones.delete(id)
  }

  def projectIdOf(milestoneId: UUID): Option[UUID] = milestones.find(milestoneId).map(_.projectId)
}

object MilestonesPreviewData {
  val Name = "milestones"
  val DatesName = "milestone-dates"
  val SummariesName = "milestone-summaries"
}
