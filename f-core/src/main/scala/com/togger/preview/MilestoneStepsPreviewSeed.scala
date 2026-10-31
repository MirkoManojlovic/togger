package com.togger.preview

import com.togger.common.WorkDayCalendar
import com.togger.demo.{DemoMilestone, DemoMilestonesData, DemoProject, DemoSeedIds}
import com.togger.milestone.{DefaultMilestoneStepTemplates, MilestoneStatus, MilestoneStepStatus}

import java.time.{DayOfWeek, LocalDate, ZoneOffset}

/** Gives every demo milestone three steps spread over its dates; needs the milestones to be seeded first.
  *
  * The state of a step follows today: a step that ended is Done, the current one is In Progress (Paused when the
  * milestone is) and the later ones are Planned. A milestone that is Abandoned abandons the steps that did not end. The
  * foundations milestone is Done although its last step never ended, as an example of a milestone closed early.
  */
class MilestoneStepsPreviewSeed(
  data: MilestoneStepsPreviewData,
  milestones: List[DemoMilestone],
  projects: List[DemoProject],
  today: LocalDate
) extends PreviewSeeder {

  private val stepNames =
    List(DefaultMilestoneStepTemplates.Design, DefaultMilestoneStepTemplates.Development, DefaultMilestoneStepTemplates.Production)

  override def seed(): Unit =
    milestones.zipWithIndex.foreach { case (milestone, milestoneIndex) =>
      val workDays = projects
        .find(_.id == milestone.projectId)
        .fold(WorkDayCalendar.DefaultWorkDays)(_.workDays.map(DayOfWeek.of))
      val ranges = WorkDayCalendar.splitEvenly(milestone.startDate, milestone.dueDate, stepNames.size, workDays)
      val closedEarly = milestone.id == DemoMilestonesData.FoundationsId
      stepNames.zip(ranges).zipWithIndex.foreach { case ((name, (start, due)), position) =>
        val (status, endDate) = stateOf(milestone, start, due, isLast = position == stepNames.size - 1, closedEarly)
        val number = milestoneIndex * 10 + position
        val at = start.atStartOfDay().toInstant(ZoneOffset.UTC)
        val step = data.steps.upsert(
          PreviewMilestoneStep(DemoSeedIds.id(DemoSeedIds.MilestoneStep, number), milestone.id, milestone.createdAt)
        )
        data.stepVersions.upsert(
          PreviewMilestoneStepVersion(
            version = DemoSeedIds.id(DemoSeedIds.MilestoneStepVersion, number),
            milestoneStepId = step.id,
            name = name,
            description = DefaultMilestoneStepTemplates.defaults.collectFirst { case (`name`, description) => description }.getOrElse(""),
            statusValue = status.value,
            position = position,
            startDate = start,
            dueDate = due,
            endDate = endDate,
            createdAt = at,
            updatedAt = endDate.fold(at)(_.atStartOfDay().toInstant(ZoneOffset.UTC)),
            changeComment = ""
          )
        )
      }
    }

  private def stateOf(
    milestone: DemoMilestone,
    start: LocalDate,
    due: LocalDate,
    isLast: Boolean,
    closedEarly: Boolean
  ): (MilestoneStepStatus, Option[LocalDate]) =
    if (closedEarly && isLast) MilestoneStepStatus.InProgress -> None
    else if (milestone.status == MilestoneStatus.Done || due.isBefore(today)) MilestoneStepStatus.Done -> Some(due)
    else if (milestone.status == MilestoneStatus.Abandoned) MilestoneStepStatus.Abandoned -> Some(start)
    else if (!start.isAfter(today))
      (if (milestone.status == MilestoneStatus.Paused) MilestoneStepStatus.Paused else MilestoneStepStatus.InProgress) -> None
    else MilestoneStepStatus.Planned -> None
}
