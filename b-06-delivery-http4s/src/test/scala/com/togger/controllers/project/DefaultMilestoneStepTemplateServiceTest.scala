package com.togger.controllers.project

import com.togger.common.TgClockImpl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class DefaultMilestoneStepTemplateServiceTest extends AnyWordSpec with Matchers {

  private val service = new DefaultMilestoneStepTemplateService(new TgClockImpl)
  private val projectId = UUID.randomUUID()
  private val accountId = UUID.randomUUID()

  "DefaultMilestoneStepTemplateService" should {

    "create Design, Prototype, Development, Staging and Production in that order" in {
      val created = service.createDefaultMilestoneStepTemplates(projectId, accountId)

      created.map { case (_, version) => version.name } shouldBe
        List("Design", "Prototype", "Development", "Staging", "Production")
    }

    "describe every step with exit criteria" in {
      service.createDefaultMilestoneStepTemplates(projectId, accountId).foreach { case (_, version) =>
        version.description should include("Exit criteria")
      }
    }

    "tie each first version to its template, project and creator" in {
      service.createDefaultMilestoneStepTemplates(projectId, accountId).foreach { case (template, version) =>
        template.projectId shouldBe projectId
        template.createdBy shouldBe accountId
        version.milestoneStepTemplateId shouldBe template.id
      }
    }
  }
}
