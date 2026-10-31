package com.togger.preview

import com.togger.demo.DemoMilestone

/** Gives every demo milestone the steps of the demo data; needs the milestones to be seeded first. */
class MilestoneStepsPreviewSeed(data: MilestoneStepsPreviewData, milestones: List[DemoMilestone]) extends PreviewSeeder {

  override def seed(): Unit =
    milestones.foreach { milestone =>
      milestone.steps.foreach { step =>
        val stored = data.steps.upsert(PreviewMilestoneStep(step.id, milestone.id, step.createdAt))
        data.stepVersions.upsert(
          PreviewMilestoneStepVersion(
            version = step.version,
            milestoneStepId = stored.id,
            name = step.name,
            description = step.description,
            statusValue = step.status.value,
            position = step.position,
            startDate = step.startDate,
            dueDate = step.dueDate,
            endDate = step.endDate,
            createdAt = step.createdAt,
            updatedAt = step.updatedAt,
            changeComment = ""
          )
        )
      }
    }
}
