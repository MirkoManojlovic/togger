package com.togger.ui.milestone

import com.togger.milestone.MilestoneStepStatus
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{DayOfWeek, LocalDate}

class MilestoneStepCloseTest extends AnyWordSpec with Matchers {

  private val monFri: Set[DayOfWeek] =
    Set(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

  // 2026-10-05 is a Monday.
  private val monday = LocalDate.of(2026, 10, 5)

  "MilestoneStepClose.closingStatuses" should {
    "offer Done and Abandoned only" in {
      MilestoneStepClose.closingStatuses shouldBe IndexedSeq(MilestoneStepStatus.Done, MilestoneStepStatus.Abandoned)
    }
  }

  "MilestoneStepClose.allClosed" should {
    "be true when every step is Done or Abandoned" in {
      MilestoneStepClose.allClosed(List(MilestoneStepStatus.Done, MilestoneStepStatus.Abandoned)) shouldBe true
    }

    "be false while a step is Planned, In Progress or Paused" in {
      List(MilestoneStepStatus.Planned, MilestoneStepStatus.InProgress, MilestoneStepStatus.Paused).foreach { open =>
        MilestoneStepClose.allClosed(List(MilestoneStepStatus.Done, open)) shouldBe false
      }
    }

    "be false for a milestone without steps" in {
      MilestoneStepClose.allClosed(Nil) shouldBe false
    }
  }

  "MilestoneStepClose.openSteps" should {
    "keep the steps that are not closed in order" in {
      val statuses = List("a" -> MilestoneStepStatus.Done, "b" -> MilestoneStepStatus.Paused, "c" -> MilestoneStepStatus.Planned)

      MilestoneStepClose.openSteps(statuses, (_: (String, MilestoneStepStatus))._2).map(_._1) shouldBe List("b", "c")
    }
  }

  "MilestoneStepClose.endDateErrors" should {
    "accept a work day on or after the start" in {
      MilestoneStepClose.endDateErrors(Some(monday), monday, monFri) shouldBe Nil
      MilestoneStepClose.endDateErrors(Some(monday.plusDays(2)), monday, monFri) shouldBe Nil
    }

    "ask for a date" in {
      MilestoneStepClose.endDateErrors(None, monday, monFri) shouldBe List("Pick an end date")
    }

    "reject a date before the start" in {
      MilestoneStepClose.endDateErrors(Some(monday.minusDays(3)), monday, monFri) shouldBe
        List("End date must be on or after the start date")
    }

    "reject a day off" in {
      MilestoneStepClose.endDateErrors(Some(monday.plusDays(5)), monday, monFri) shouldBe
        List("End date must be one of this project's configured work days")
    }

    "not reject days while the work days are not known yet" in {
      MilestoneStepClose.endDateErrors(Some(monday.plusDays(5)), monday, Set.empty) shouldBe Nil
    }
  }

  "MilestoneStepClose.commentErrors" should {
    "require a comment that is not blank" in {
      MilestoneStepClose.commentErrors("  ") shouldBe List("Change comment is required")
      MilestoneStepClose.commentErrors("Done early") shouldBe Nil
    }
  }
}
