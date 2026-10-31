package com.togger.demo

import com.togger.task.{DefaultTaskLabel, DefaultTaskStatus}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The default statuses and labels of every demo project, and a few task templates. */
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

  def templates(now: Instant): List[DemoTaskTemplate] =
    templateSpecs.map { case (number, projectIndex, labelOpt, versions) =>
      DemoTaskTemplate(
        templateId(number),
        projectIds(projectIndex),
        labelOpt.map(label => labelId(projectIndex, label.i)),
        DemoIdentity.AccountId,
        now.minus(versions.size * 10L, ChronoUnit.DAYS),
        versions.zipWithIndex.map { case ((name, body), i) =>
          DemoTaskTemplateVersion(
            DemoSeedIds.id(DemoSeedIds.TaskTemplate, number * 10 + i + 100),
            name,
            body,
            now.minus((versions.size - i) * 10L, ChronoUnit.DAYS)
          )
        }
      )
    }

  private def templateId(n: Int): UUID = DemoSeedIds.id(DemoSeedIds.TaskTemplate, n)

  private val bugBody =
    "## What happened\n\n\n## Steps to reproduce\n\n1. \n2. \n\n## Expected behaviour\n\n\n## Environment\n\n- Version:\n- Browser:\n"
  private val bugBodyV1 = "## What happened\n\n\n## Steps to reproduce\n\n1. \n\n## Expected behaviour\n"
  private val featureBody =
    "## Goal\n\n\n## Why now\n\n\n## Acceptance criteria\n\n- [ ] \n- [ ] \n\n## Out of scope\n"
  private val doneBody =
    "## Definition of done\n\n- [ ] Code reviewed\n- [ ] Tests added or updated\n- [ ] Documentation updated\n- [ ] Deployed to staging\n"

  private val templateSpecs: List[(Int, Int, Option[DefaultTaskLabel], List[(String, String)])] = List(
    (1, 0, Some(DefaultTaskLabel.Bug), List("Bug report" -> bugBodyV1, "Bug report" -> bugBody)),
    (2, 0, Some(DefaultTaskLabel.Feature), List("Feature request" -> featureBody)),
    (3, 0, None, List("Definition of done" -> doneBody)),
    (4, 1, Some(DefaultTaskLabel.Bug), List("Mobile bug report" -> bugBody.replace("Browser", "Device"))),
    (5, 2, Some(DefaultTaskLabel.Docs), List("Documentation page" -> "## Audience\n\n\n## Outline\n\n\n## Examples\n"))
  )
}
