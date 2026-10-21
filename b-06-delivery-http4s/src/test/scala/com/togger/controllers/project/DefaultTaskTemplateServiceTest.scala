package com.togger.controllers.project

import com.togger.common.TgClockImpl
import com.togger.task.{DefaultTaskLabel, TaskLabel}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class DefaultTaskTemplateServiceTest extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-10-12T10:00:00Z")
  private val service = new DefaultTaskTemplateService(new TgClockImpl)

  private val projectId = UUID.randomUUID()
  private val accountId = UUID.randomUUID()

  private val labels = DefaultTaskLabel.values.map(label =>
    TaskLabel(UUID.randomUUID(), projectId, label.labelName, label.description, now, now)
  )

  "DefaultTaskTemplateService" should {

    "create a template for the feature and the bug labels only, linked to them" in {
      val created = service.createDefaultTaskTemplates(projectId, accountId, labels)

      val byLabel = created.map { case (template, version) => template.taskLabelId.get -> (template, version) }.toMap
      val feature = labels.find(_.name == DefaultTaskLabel.Feature.labelName).get
      val bug = labels.find(_.name == DefaultTaskLabel.Bug.labelName).get

      created should have size 2
      byLabel.keySet shouldBe Set(feature.id, bug.id)
      byLabel(feature.id)._2.body should include("Definition of done")
      byLabel(bug.id)._2.body should include("Steps to reproduce")
    }

    "tie each first version to its template and project" in {
      service.createDefaultTaskTemplates(projectId, accountId, labels).foreach { case (template, version) =>
        template.projectId shouldBe projectId
        template.createdBy shouldBe accountId
        version.taskTemplateId shouldBe template.id
      }
    }

    "create nothing for labels without a default template" in {
      service.createDefaultTaskTemplates(projectId, accountId, labels.filter(_.name.contains("docs"))) shouldBe empty
    }
  }
}
