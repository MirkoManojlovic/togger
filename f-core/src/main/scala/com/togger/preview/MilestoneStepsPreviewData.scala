package com.togger.preview

import com.togger.milestone.MilestoneStepStatus
import io.circe.generic.auto._

import java.time.{Instant, LocalDate}
import java.util.UUID

case class PreviewMilestoneStep(id: UUID, milestoneId: UUID, createdAt: Instant)

/** The status is kept as its int value, so that no codec of the enum is needed in the storage. */
case class PreviewMilestoneStepVersion(
  version: UUID,
  milestoneStepId: UUID,
  name: String,
  description: String,
  statusValue: Int,
  position: Int,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate],
  createdAt: Instant,
  updatedAt: Instant,
  changeComment: String
) {
  def status: MilestoneStepStatus = MilestoneStepStatus.withValue(statusValue)
}

/** The steps of the milestones with all their versions. */
class MilestoneStepsPreviewData(db: PreviewDb, milestones: MilestonesPreviewData) {

  val steps: PreviewCollection[PreviewMilestoneStep] =
    db.collection[PreviewMilestoneStep](MilestoneStepsPreviewData.StepsName, _.id)

  /** Versions are stored oldest first, the last one of a step is its current content. */
  val stepVersions: PreviewCollection[PreviewMilestoneStepVersion] =
    db.collection[PreviewMilestoneStepVersion](MilestoneStepsPreviewData.VersionsName, _.version)

  milestones.onMilestoneDeleted(deleteStepsOf)

  def latestVersion(stepId: UUID): Option[PreviewMilestoneStepVersion] =
    stepVersions.filter(_.milestoneStepId == stepId).lastOption

  /** The current content of the steps of the milestone, ordered by position. */
  def latestVersionsOf(milestoneId: UUID): List[PreviewMilestoneStepVersion] =
    steps.filter(_.milestoneId == milestoneId).flatMap(step => latestVersion(step.id)).sortBy(_.position)

  /** How many steps the milestone has and how many of them are closed (Done or Abandoned). */
  def counts(milestoneId: UUID): (Int, Int) = {
    val latest = latestVersionsOf(milestoneId)
    latest.size -> latest.count(_.status.isClosing)
  }

  def deleteStepsOf(milestoneId: UUID): Unit =
    steps.filter(_.milestoneId == milestoneId).foreach { step =>
      stepVersions.deleteWhere(_.milestoneStepId == step.id)
      steps.delete(step.id)
    }
}

object MilestoneStepsPreviewData {
  val StepsName = "milestone-steps"
  val VersionsName = "milestone-step-versions"
}
