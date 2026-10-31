package com.togger.preview

import com.togger.demo.{DemoTaskLabel, DemoTaskStatus, DemoTaskTemplate}
import com.togger.dto.{FindTaskLabelResp, FindTaskStatusResp, TaskTemplateVersionDto}

/** Seeds the default statuses, task labels and task templates of the demo projects. */
class TaskConfigPreviewSeed(
  data: TaskConfigPreviewData,
  statuses: List[DemoTaskStatus],
  labels: List[DemoTaskLabel],
  templates: List[DemoTaskTemplate]
) extends PreviewSeeder {

  override def seed(): Unit = {
    statuses.foreach { s =>
      data.statuses.upsert(FindTaskStatusResp(s.id, s.projectId, s.status.statusName, s.status.description, s.status.emoji))
    }
    labels.foreach { l =>
      data.labels.upsert(FindTaskLabelResp(l.id, l.projectId, l.label.labelName, l.label.description, l.label.emoji))
    }
    templates.foreach { template =>
      data.templates.upsert(
        PreviewTaskTemplate(template.id, template.projectId, template.taskLabelId, template.createdBy, template.createdAt)
      )
      template.versions.foreach { v =>
        data.templateVersions.upsert(TaskTemplateVersionDto(v.id, template.id, v.name, v.body, v.createdAt))
      }
    }
  }
}
