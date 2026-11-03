package com.togger.demo

import com.togger.task.DefaultTaskLabel
import com.togger.task.DefaultTaskStatus
import com.togger.task.DefaultTaskTemplates

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The default statuses and labels of every demo project, and their default task templates. */
object DemoTaskConfigData {
  private val projectIds = DemoOrganizationsData.projectIds

  def statusId(projectIndex: Int, statusIndex: Int): UUID =
    DemoSeedIds.id(DemoSeedIds.TaskStatus, projectIndex * 20 + statusIndex)

  def labelId(projectIndex: Int, labelIndex: Int): UUID =
    DemoSeedIds.id(DemoSeedIds.TaskLabel, projectIndex * 20 + labelIndex)

  val statuses: List[DemoTaskStatus] =
    for {
      (projectId, index) <- projectIds.zipWithIndex
      (status, i) <- DefaultTaskStatus.values.toList.zipWithIndex
    } yield DemoTaskStatus(statusId(index, i), projectId, status)

  val labels: List[DemoTaskLabel] =
    for {
      (projectId, index) <- projectIds.zipWithIndex
      (label, i) <- DefaultTaskLabel.values.toList.zipWithIndex
    } yield DemoTaskLabel(labelId(index, i), projectId, label)

  /** Every project has the default template of each default label, like a project created in the backend. */
  def templates(now: Instant): List[DemoTaskTemplate] =
    for {
      (projectId, projectIndex) <- projectIds.zipWithIndex
      label <- DefaultTaskLabel.values.toList
      (name, body) <- DefaultTaskTemplates.contentOf(label.labelName).toList
    } yield {
      val number = projectIndex * 20 + label.i
      DemoTaskTemplate(
        DemoSeedIds.id(DemoSeedIds.TaskTemplate, number),
        projectId,
        Some(labelId(projectIndex, label.i)),
        DemoIdentity.AccountId,
        now.minus(10L, ChronoUnit.DAYS),
        List(
          DemoTaskTemplateVersion(
            DemoSeedIds.id(DemoSeedIds.TaskTemplate, number + 1000),
            name,
            body,
            now.minus(10L, ChronoUnit.DAYS)
          )
        )
      )
    }
}
