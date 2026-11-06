package com.togger.dump

import com.togger.milestonesteptemplate.MilestoneStepTemplate
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersion
import com.togger.milestonetemplate.MilestoneTemplate
import com.togger.milestonetemplate.MilestoneTemplateVersion
import com.togger.projectlabel.ProjectLabel
import com.togger.projectlabel.ProjectLabelCategory
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class ExportContentKeyTest extends AnyWordSpec with Matchers {

  private val earlier = Instant.parse("2026-10-01T10:00:00Z")
  private val later = Instant.parse("2026-10-02T10:00:00Z")
  private val projectId = UUID.randomUUID()
  private val accountId = UUID.randomUUID()

  private def stepTemplateJson(id: UUID, versions: (String, String, Instant)*): Json =
    MilestoneStepTemplateDumpData(
      MilestoneStepTemplate(id, projectId, accountId, earlier),
      versions.toList.map { case (name, description, at) =>
        MilestoneStepTemplateVersion(UUID.randomUUID(), id, name, description, at)
      }
    ).asJson

  private def milestoneTemplateJson(id: UUID, versions: (String, String, Instant, List[List[UUID]])*): Json =
    MilestoneTemplateDumpData(
      MilestoneTemplate(id, projectId, accountId, earlier),
      versions.toList.map { case (name, description, at, steps) =>
        MilestoneTemplateVersionDumpData(MilestoneTemplateVersion(UUID.randomUUID(), id, name, description, at), steps)
      }
    ).asJson

  private def projectLabelJson(id: UUID, name: String, categoryName: String = "Area"): Json =
    ProjectLabelDumpData(
      ProjectLabel(id, name, "description", UUID.randomUUID(), earlier, earlier),
      ProjectLabelCategory(UUID.randomUUID(), categoryName, earlier, earlier)
    ).asJson

  "ExportContentKey" should {

    "identify a project label by its name, whatever its id" in {
      val here =
        ExportContentKey.of(DataExportEntityType.ProjectLabel, projectLabelJson(UUID.randomUUID(), "Scala"), Map.empty)
      here.isRight shouldBe true
      here shouldBe ExportContentKey.of(
        DataExportEntityType.ProjectLabel,
        projectLabelJson(UUID.randomUUID(), "Scala", "Other area"),
        Map.empty
      )
      here should not be ExportContentKey.of(
        DataExportEntityType.ProjectLabel,
        projectLabelJson(UUID.randomUUID(), "Web"),
        Map.empty
      )
      ExportContentKey.applies(DataExportEntityType.ProjectLabel) shouldBe true
    }

    "apply to the milestone step templates and the milestone templates" in {
      ExportContentKey.applies(DataExportEntityType.MilestoneStepTemplate) shouldBe true
      ExportContentKey.applies(DataExportEntityType.MilestoneTemplate) shouldBe true
      ExportContentKey.applies(DataExportEntityType.Milestone) shouldBe false
    }

    "identify a step template by the name and description of its latest version, whatever its id" in {
      val a = stepTemplateJson(UUID.randomUUID(), ("Design", "old", earlier), ("Design", "new", later))
      val b = stepTemplateJson(UUID.randomUUID(), ("Design", "new", later))

      val keyA = ExportContentKey.of(DataExportEntityType.MilestoneStepTemplate, a, Map.empty)
      keyA shouldBe Right(List("Design", "new"))
      keyA shouldBe ExportContentKey.of(DataExportEntityType.MilestoneStepTemplate, b, Map.empty)
    }

    "tell step templates apart that differ in the description" in {
      val a = stepTemplateJson(UUID.randomUUID(), ("Design", "one", later))
      val b = stepTemplateJson(UUID.randomUUID(), ("Design", "two", later))

      ExportContentKey.of(DataExportEntityType.MilestoneStepTemplate, a, Map.empty) should not be
        ExportContentKey.of(DataExportEntityType.MilestoneStepTemplate, b, Map.empty)
    }

    "identify a milestone template by its latest version and the content of its steps, whatever their ids" in {
      val designHere = UUID.randomUUID()
      val designThere = UUID.randomUUID()
      val stepKeysHere =
        ExportContentKey.stepTemplateKeys(Map(designHere -> stepTemplateJson(designHere, ("Design", "d", later))))
      val stepKeysThere =
        ExportContentKey.stepTemplateKeys(Map(designThere -> stepTemplateJson(designThere, ("Design", "d", later))))
      val here = milestoneTemplateJson(
        UUID.randomUUID(),
        ("Feature", "x", earlier, Nil),
        ("Feature", "y", later, List(List(designHere)))
      )
      val there = milestoneTemplateJson(UUID.randomUUID(), ("Feature", "y", later, List(List(designThere))))

      val keyHere = ExportContentKey.of(DataExportEntityType.MilestoneTemplate, here, stepKeysHere)
      keyHere.isRight shouldBe true
      keyHere shouldBe ExportContentKey.of(DataExportEntityType.MilestoneTemplate, there, stepKeysThere)
    }

    "tell milestone templates apart that list different steps or the same steps in another order" in {
      val design = UUID.randomUUID()
      val staging = UUID.randomUUID()
      val keys = ExportContentKey.stepTemplateKeys(
        Map(
          design -> stepTemplateJson(design, ("Design", "d", later)),
          staging -> stepTemplateJson(staging, ("Staging", "s", later))
        )
      )
      def keyOf(steps: List[List[UUID]]) =
        ExportContentKey.of(
          DataExportEntityType.MilestoneTemplate,
          milestoneTemplateJson(UUID.randomUUID(), ("T", "d", later, steps)),
          keys
        )

      keyOf(List(List(design), List(staging))) should not be keyOf(List(List(staging), List(design)))
      keyOf(List(List(design))) should not be keyOf(List(List(design), List(staging)))
      // steps that run in parallel are not steps in sequence, and their order inside a stage does not matter
      keyOf(List(List(design, staging))) should not be keyOf(List(List(design), List(staging)))
      keyOf(List(List(design, staging))) shouldBe keyOf(List(List(staging, design)))
    }

    "report a step template the milestone template names but the tree does not hold" in {
      val unknown = UUID.randomUUID()
      val result = ExportContentKey.of(
        DataExportEntityType.MilestoneTemplate,
        milestoneTemplateJson(UUID.randomUUID(), ("T", "d", later, List(List(unknown)))),
        Map.empty
      )

      result.left.exists(_.contains(unknown.toString)) shouldBe true
    }
  }
}
