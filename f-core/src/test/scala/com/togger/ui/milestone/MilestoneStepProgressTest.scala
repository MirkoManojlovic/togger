package com.togger.ui.milestone

import com.togger.milestone.MilestoneStatus
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MilestoneStepProgressTest extends AnyWordSpec with Matchers {

  "MilestoneStepProgress.label" should {
    "say there are no steps or how many are closed" in {
      MilestoneStepProgress.label(0, 0) shouldBe "No steps"
      MilestoneStepProgress.label(5, 2) shouldBe "2/5 steps closed"
    }
  }

  "MilestoneStepProgress.unfinishedWhenDone" should {
    "count the open steps of a Done milestone only" in {
      MilestoneStepProgress.unfinishedWhenDone(MilestoneStatus.Done, 5, 2) shouldBe 3
      MilestoneStepProgress.unfinishedWhenDone(MilestoneStatus.Done, 5, 5) shouldBe 0
      MilestoneStepProgress.unfinishedWhenDone(MilestoneStatus.InProgress, 5, 2) shouldBe 0
      MilestoneStepProgress.unfinishedWhenDone(MilestoneStatus.Abandoned, 5, 2) shouldBe 0
    }

    "not go below zero" in {
      MilestoneStepProgress.unfinishedWhenDone(MilestoneStatus.Done, 1, 3) shouldBe 0
    }
  }

  "MilestoneStepProgress.closedEarlyLabel" should {
    "use the singular for one step" in {
      MilestoneStepProgress.closedEarlyLabel(1) shouldBe "Closed with 1 unfinished step"
      MilestoneStepProgress.closedEarlyLabel(3) shouldBe "Closed with 3 unfinished steps"
    }
  }

  "MilestoneStepProgress.closeWarning" should {
    "warn about the open steps and say that closing is still possible" in {
      MilestoneStepProgress.closeWarning(3, 1).exists(_.startsWith("2 of 3 steps are not closed yet.")) shouldBe true
      MilestoneStepProgress.closeWarning(1, 0).exists(_.startsWith("1 of 1 step is not closed yet.")) shouldBe true
    }

    "stay quiet when every step is closed or there are none" in {
      MilestoneStepProgress.closeWarning(3, 3) shouldBe None
      MilestoneStepProgress.closeWarning(0, 0) shouldBe None
    }
  }
}
