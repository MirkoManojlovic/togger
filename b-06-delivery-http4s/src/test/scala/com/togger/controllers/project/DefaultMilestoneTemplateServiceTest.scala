package com.togger.controllers.project

import com.togger.common.TgClockImpl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class DefaultMilestoneTemplateServiceTest extends AnyWordSpec with Matchers {

  private val service = new DefaultMilestoneTemplateService(new TgClockImpl)
  private val projectId = UUID.randomUUID()
  private val accountId = UUID.randomUUID()

  private val stepTemplateIds: Map[String, UUID] =
    DefaultMilestoneStepTemplateService.defaults.map { case (name, _) => name -> UUID.randomUUID() }.toMap

  private def create = service.createDefaultMilestoneTemplates(projectId, accountId, stepTemplateIds)

  "DefaultMilestoneTemplateService" should {

    "create the new feature and the migration templates with all five steps in order" in {
      val allSteps = List("Design", "Prototype", "Development", "Staging", "Production").map(stepTemplateIds)
      val byName = create.map { case (_, version, steps) => version.name -> steps }.toMap

      byName("New feature") shouldBe allSteps
      byName("Migration") shouldBe allSteps
    }

    "create the extra templates from a subset of the steps" in {
      val byName = create.map { case (_, version, steps) => version.name -> steps }.toMap

      byName("Bug fix") shouldBe List("Development", "Staging", "Production").map(stepTemplateIds)
      byName("Research spike") shouldBe List("Design", "Prototype").map(stepTemplateIds)
      byName("Release") shouldBe List("Staging", "Production").map(stepTemplateIds)
      byName("Refactoring") shouldBe List("Design", "Development", "Staging").map(stepTemplateIds)
    }

    "skip steps whose default step template does not exist" in {
      val onlyDesign = Map("Design" -> stepTemplateIds("Design"))
      val steps = service.createDefaultMilestoneTemplates(projectId, accountId, onlyDesign).flatMap(_._3)

      steps.toSet shouldBe Set(stepTemplateIds("Design"))
    }

    "tie each first version to its template, project and creator" in {
      create.foreach { case (template, version, _) =>
        template.projectId shouldBe projectId
        template.createdBy shouldBe accountId
        version.milestoneTemplateId shouldBe template.id
      }
    }
  }
}
