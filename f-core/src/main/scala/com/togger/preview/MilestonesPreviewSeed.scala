package com.togger.preview

import com.togger.demo.DemoMilestone
import com.togger.dto.{FindMilestoneDateResp, FindMilestoneSummaryResp}

/** Seeds milestones around today, with their dates and summaries. */
class MilestonesPreviewSeed(data: MilestonesPreviewData, milestones: List[DemoMilestone]) extends PreviewSeeder {

  override def seed(): Unit =
    milestones.foreach { m =>
      data.milestones.upsert(
        PreviewMilestone(
          id = m.id,
          projectId = m.projectId,
          createdAt = m.createdAt,
          latestVersion = m.version,
          name = m.name,
          description = m.description,
          statusValue = m.status.value,
          startDate = m.startDate,
          dueDate = m.dueDate,
          endDate = m.endDate,
          updatedAt = m.updatedAt
        )
      )
      m.dates.foreach { d =>
        data.dates.upsert(
          FindMilestoneDateResp(d.id, m.id, d.createdAt, d.version, d.name, d.description, d.date, d.updatedAt)
        )
      }
      m.summaries.foreach { s =>
        data.summaries.upsert(
          FindMilestoneSummaryResp(s.id, m.id, s.createdAt, s.version, s.text, s.fromDate, s.toDate, s.updatedAt)
        )
      }
    }
}
