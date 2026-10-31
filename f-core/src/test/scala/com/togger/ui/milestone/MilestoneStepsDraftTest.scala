package com.togger.ui.milestone

import com.togger.dto.MilestoneTemplateStepDto
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.DayOfWeek
import java.util.UUID

class MilestoneStepsDraftTest extends AnyWordSpec with Matchers {

  private val monFri: Set[DayOfWeek] =
    Set(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

  // 2026-10-05 is a Monday; two work weeks end on Friday 2026-10-16.
  private val start = "2026-10-05"
  private val due = "2026-10-16"

  private def templateStep(name: String) = MilestoneTemplateStepDto(UUID.randomUUID(), name, s"$name description")

  "MilestoneStepsDraft.fromTemplate" should {
    "make one draft per step with the milestone range spread over them in order" in {
      val drafts = MilestoneStepsDraft.fromTemplate(List(templateStep("Design"), templateStep("Build")), start, due, monFri)

      drafts shouldBe List(
        MilestoneStepDraft("Design", "Design description", "2026-10-05", "2026-10-09"),
        MilestoneStepDraft("Build", "Build description", "2026-10-12", "2026-10-16")
      )
    }

    "leave the dates empty while the milestone range cannot be read" in {
      val drafts = MilestoneStepsDraft.fromTemplate(List(templateStep("Design")), "", due, monFri)

      drafts.map(d => d.startDate -> d.dueDate) shouldBe List("" -> "")
    }

    "leave the dates empty when the milestone ends before it starts" in {
      MilestoneStepsDraft.spread(2, due, start, monFri) shouldBe List("" -> "", "" -> "")
    }
  }

  "MilestoneStepsDraft.errors" should {
    "find nothing wrong with a step inside the milestone on work days" in {
      MilestoneStepsDraft.errors(MilestoneStepDraft("Design", "", "2026-10-06", "2026-10-08"), start, due, monFri) shouldBe Nil
    }

    "ask for a name and for the dates" in {
      MilestoneStepsDraft.errors(MilestoneStepDraft(" ", "", "", "x"), start, due, monFri) shouldBe
        List("Step name is required", "Start date and due date are required")
    }

    "report a date outside the milestone" in {
      val errors = MilestoneStepsDraft.errors(MilestoneStepDraft("Design", "", "2026-10-02", "2026-10-08"), start, due, monFri)

      errors should have size 1
      errors.head should include("Start date must be inside the range")
    }

    "report a start on a day that is not a work day" in {
      val errors = MilestoneStepsDraft.errors(MilestoneStepDraft("Design", "", "2026-10-10", "2026-10-12"), start, due, monFri)

      errors.exists(_.contains("work days")) shouldBe true
    }

    "report a due date before the start date" in {
      val errors = MilestoneStepsDraft.errors(MilestoneStepDraft("Design", "", "2026-10-08", "2026-10-06"), start, due, monFri)

      errors.exists(_.contains("on or after the start date")) shouldBe true
    }

    "not flag every day while the work days are not loaded yet" in {
      MilestoneStepsDraft.errors(MilestoneStepDraft("Design", "", "2026-10-06", "2026-10-08"), start, due, Set.empty) shouldBe Nil
    }

    "skip the milestone range check while that range cannot be read" in {
      MilestoneStepsDraft.errors(MilestoneStepDraft("Design", "", "2026-10-06", "2026-10-08"), "", "", monFri) shouldBe Nil
    }
  }

  "MilestoneStepsDraft.toRequests" should {
    "turn the drafts into requests in order, trimming the name" in {
      val requests = MilestoneStepsDraft.toRequests(
        List(
          MilestoneStepDraft(" Design ", "d", "2026-10-05", "2026-10-09"),
          MilestoneStepDraft("Build", "", "2026-10-12", "2026-10-16")
        )
      )

      requests.map(r => (r.name, r.startDate.toString, r.dueDate.toString)) shouldBe
        List(("Design", "2026-10-05", "2026-10-09"), ("Build", "2026-10-12", "2026-10-16"))
    }

    "leave out a draft whose dates cannot be read" in {
      MilestoneStepsDraft.toRequests(List(MilestoneStepDraft("A", "", "", ""))) shouldBe Nil
    }
  }
}
