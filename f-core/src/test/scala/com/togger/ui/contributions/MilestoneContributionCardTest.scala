package com.togger.ui.contributions

import com.togger.dto.{MilestoneContributionsDto, TaskAttributesDto}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class MilestoneContributionCardTest extends AnyWordSpec with Matchers {

  private def milestone(start: Option[LocalDate], due: Option[LocalDate]) =
    MilestoneContributionsDto(None, "Alpha", None, None, start, due, 0, 0, List.empty, TaskAttributesDto.empty)

  private val start = LocalDate.of(2026, 7, 1)
  private val due = LocalDate.of(2026, 9, 30)

  "MilestoneContributionCard.dateLabels" should {

    "give the start and the due date a badge each, in the format asked" in {
      MilestoneContributionCard.dateLabels(milestone(Some(start), Some(due)), _.toString) shouldBe
        Seq("Start: 2026-07-01", "Due: 2026-09-30")
    }

    "give a badge only to a date that is known" in {
      MilestoneContributionCard.dateLabels(milestone(None, Some(due)), _.toString) shouldBe Seq("Due: 2026-09-30")
      MilestoneContributionCard.dateLabels(milestone(None, None), _.toString) shouldBe empty
    }
  }
}
