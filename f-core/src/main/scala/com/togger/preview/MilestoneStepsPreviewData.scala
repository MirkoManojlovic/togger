package com.togger.preview

import com.togger.milestone.MilestoneStepStatus
import io.circe.generic.auto._

import java.time.{Instant, LocalDate}
import java.util.UUID

case class PreviewMilestoneStep(id: UUID, milestoneId: UUID, createdAt: Instant, shelf: Boolean = false)

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

/** What the tasks domain tells about the tasks in the steps; set by [[TasksPreviewData]], which owns the tasks. */
trait StepTaskSource {

  /** The tasks (by their latest version) in each step and how many of them are done; steps without tasks are absent. */
  def countsOf(stepIds: Set[UUID]): Map[UUID, (Int, Int)]
}

/** The steps of the milestones with all their versions. */
class MilestoneStepsPreviewData(db: PreviewDb, milestones: MilestonesPreviewData) {

  val steps: PreviewCollection[PreviewMilestoneStep] =
    db.collection[PreviewMilestoneStep](MilestoneStepsPreviewData.StepsName, _.id)

  /** Versions are stored oldest first, the last one of a step is its current content. */
  val stepVersions: PreviewCollection[PreviewMilestoneStepVersion] =
    db.collection[PreviewMilestoneStepVersion](MilestoneStepsPreviewData.VersionsName, _.version)

  /** Without the tasks domain no step has tasks. */
  var taskSource: StepTaskSource = (_: Set[UUID]) => Map.empty

  milestones.onMilestoneDeleted(deleteStepsOf)

  def taskCounts(stepId: UUID): (Int, Int) = taskSource.countsOf(Set(stepId)).getOrElse(stepId, (0, 0))

  /** The tasks in any step of the milestone. */
  def tasksInMilestone(milestoneId: UUID): Int = {
    val stepIds = steps.filter(_.milestoneId == milestoneId).map(_.id).toSet
    taskSource.countsOf(stepIds).values.map(_._1).sum
  }

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
