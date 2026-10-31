package com.togger.preview

import com.togger.common.TgClock
import com.togger.dto.{FindTaskLabelResp, FindTaskStatusResp, TaskTemplateVersionDto}
import com.togger.task.{DefaultTaskLabel, DefaultTaskStatus}

import java.time.temporal.ChronoUnit
import java.util.UUID

/** Seeds the default statuses, task labels and task templates of the demo projects. */
class TaskConfigPreviewSeed(data: TaskConfigPreviewData, clock: TgClock) extends PreviewSeeder {
  import TaskConfigPreviewSeed._

  override def seed(): Unit = {
    val now = clock.currentInstant
    projectIds.zipWithIndex.foreach { case (projectId, index) =>
      DefaultTaskStatus.values.zipWithIndex.foreach { case (status, i) =>
        data.statuses.upsert(
          FindTaskStatusResp(statusId(index, i), projectId, status.statusName, status.description, status.emoji)
        )
      }
      DefaultTaskLabel.values.zipWithIndex.foreach { case (label, i) =>
        data.labels.upsert(FindTaskLabelResp(labelId(index, i), projectId, label.labelName, label.description, label.emoji))
      }
    }

    templates.foreach { case (templateId, projectIndex, labelOpt, versions) =>
      data.templates.upsert(
        PreviewTaskTemplate(
          templateId,
          projectIds(projectIndex),
          labelOpt.map(label => labelId(projectIndex, label.i)),
          PreviewDemo.AccountId,
          now.minus(versions.size * 10L, ChronoUnit.DAYS)
        )
      )
      versions.zipWithIndex.foreach { case ((name, body), i) =>
        data.templateVersions.upsert(
          TaskTemplateVersionDto(
            PreviewSeedIds.id(PreviewSeedIds.TaskTemplate, templateNumber(templateId) * 10 + i + 100),
            templateId,
            name,
            body,
            now.minus((versions.size - i) * 10L, ChronoUnit.DAYS)
          )
        )
      }
    }
  }
}

object TaskConfigPreviewSeed {
  val projectIds: List[UUID] = List(PreviewDemo.ProjectId, PreviewDemo.SecondProjectId, OrganizationsPreviewSeed.SideProjectId)

  def statusId(projectIndex: Int, statusIndex: Int): UUID =
    PreviewSeedIds.id(PreviewSeedIds.TaskStatus, projectIndex * 20 + statusIndex)

  def labelId(projectIndex: Int, labelIndex: Int): UUID =
    PreviewSeedIds.id(PreviewSeedIds.TaskLabel, projectIndex * 20 + labelIndex)

  private def templateId(n: Int): UUID = PreviewSeedIds.id(PreviewSeedIds.TaskTemplate, n)

  private def templateNumber(id: UUID): Int = (id.getLeastSignificantBits & 0xffffL).toInt

  private val bugBody =
    "## What happened\n\n\n## Steps to reproduce\n\n1. \n2. \n\n## Expected behaviour\n\n\n## Environment\n\n- Version:\n- Browser:\n"
  private val bugBodyV1 = "## What happened\n\n\n## Steps to reproduce\n\n1. \n\n## Expected behaviour\n"
  private val featureBody =
    "## Goal\n\n\n## Why now\n\n\n## Acceptance criteria\n\n- [ ] \n- [ ] \n\n## Out of scope\n"
  private val doneBody =
    "## Definition of done\n\n- [ ] Code reviewed\n- [ ] Tests added or updated\n- [ ] Documentation updated\n- [ ] Deployed to staging\n"

  private val templates: List[(UUID, Int, Option[DefaultTaskLabel], List[(String, String)])] = List(
    (templateId(1), 0, Some(DefaultTaskLabel.Bug), List("Bug report" -> bugBodyV1, "Bug report" -> bugBody)),
    (templateId(2), 0, Some(DefaultTaskLabel.Feature), List("Feature request" -> featureBody)),
    (templateId(3), 0, None, List("Definition of done" -> doneBody)),
    (templateId(4), 1, Some(DefaultTaskLabel.Bug), List("Mobile bug report" -> bugBody.replace("Browser", "Device"))),
    (templateId(5), 2, Some(DefaultTaskLabel.Docs), List("Documentation page" -> "## Audience\n\n\n## Outline\n\n\n## Examples\n"))
  )
}
