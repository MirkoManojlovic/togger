package com.togger.ui.milestonetemplate

import com.togger.dto.FindMilestoneStepTemplateResp
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class MilestoneTemplateStepsEditingTest extends AnyWordSpec with Matchers {

  private val s1 = UUID.randomUUID()
  private val s2 = UUID.randomUUID()
  private val s3 = UUID.randomUUID()

  "MilestoneTemplateStepsEditing.add" should {
    "append the step, also when it is already in the list" in {
      MilestoneTemplateStepsEditing.add(List(s1), s2) shouldBe List(s1, s2)
      MilestoneTemplateStepsEditing.add(List(s1), s1) shouldBe List(s1, s1)
    }
  }

  "MilestoneTemplateStepsEditing.remove" should {
    "remove the step at the index only" in {
      MilestoneTemplateStepsEditing.remove(List(s1, s2, s1), 2) shouldBe List(s1, s2)
      MilestoneTemplateStepsEditing.remove(List(s1, s2, s3), 0) shouldBe List(s2, s3)
    }

    "change nothing for an index out of range" in {
      MilestoneTemplateStepsEditing.remove(List(s1, s2), 2) shouldBe List(s1, s2)
      MilestoneTemplateStepsEditing.remove(List(s1, s2), -1) shouldBe List(s1, s2)
    }
  }

  "MilestoneTemplateStepsEditing.move" should {
    "move s1 step down and up by swapping it with its neighbour" in {
      MilestoneTemplateStepsEditing.move(List(s1, s2, s3), 0, 1) shouldBe List(s2, s1, s3)
      MilestoneTemplateStepsEditing.move(List(s1, s2, s3), 2, -1) shouldBe List(s1, s3, s2)
    }

    "stop at the ends of the list" in {
      MilestoneTemplateStepsEditing.move(List(s1, s2, s3), 0, -1) shouldBe List(s1, s2, s3)
      MilestoneTemplateStepsEditing.move(List(s1, s2, s3), 2, 1) shouldBe List(s1, s2, s3)
    }

    "change nothing for an index out of range" in {
      MilestoneTemplateStepsEditing.move(List(s1, s2), 5, -1) shouldBe List(s1, s2)
    }
  }

  "MilestoneTemplateFormSupport.stepNames" should {
    "name the steps in order and mark s1 step template that no longer exists" in {
      def stepTemplate(id: UUID, name: String) =
        FindMilestoneStepTemplateResp(id, UUID.randomUUID(), name, "", UUID.randomUUID(), Instant.EPOCH)

      MilestoneTemplateFormSupport.stepNames(List(s2, s1, s3, s2), Seq(stepTemplate(s1, "Design"), stepTemplate(s2, "Staging"))) shouldBe
        List("Staging", "Design", "(deleted step template)", "Staging")
    }
  }
}
