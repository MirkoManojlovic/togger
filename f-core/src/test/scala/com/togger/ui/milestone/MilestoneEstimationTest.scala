package com.togger.ui.milestone

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MilestoneEstimationTest extends AnyWordSpec with Matchers {

  "MilestoneEstimation.statusOf" should {
    "not make it with more work than days" in {
      MilestoneEstimation.statusOf(-1) shouldBe MilestoneEstimationStatus.NotGoingToMakeIt
    }
    "probably not make it with as much work as days" in {
      MilestoneEstimation.statusOf(0) shouldBe MilestoneEstimationStatus.ProbablyNotGoingToMakeIt
    }
    "be a tight finish with a few days of buffer" in {
      MilestoneEstimation.statusOf(5) shouldBe MilestoneEstimationStatus.TightFinish
    }
    "be a comfortable finish with 10 days of buffer" in {
      MilestoneEstimation.statusOf(10) shouldBe MilestoneEstimationStatus.ComfortableFinish
    }
    "be on track with 20 days of buffer or more" in {
      MilestoneEstimation.statusOf(20) shouldBe MilestoneEstimationStatus.OnTrack
      MilestoneEstimation.statusOf(35) shouldBe MilestoneEstimationStatus.OnTrack
    }
  }
}
