package com.togger.milestone

import com.togger.milestone.MilestoneStepStages._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class MilestoneStepStagesTest extends AnyWordSpec with Matchers {

  private def staged(position: Int, isDefault: Boolean = false) = Staged(UUID.randomUUID(), position, isDefault)

  private val shelf = staged(0, isDefault = true)
  private val design = staged(1)
  private val backend = staged(2)
  private val frontend = staged(2)
  private val steps = List(shelf, design, backend, frontend)

  "MilestoneStepStages.placementOf" should {
    "append a new last stage without a position" in {
      placementOf(steps, None, parallel = false) shouldBe NewStage(3)
      placementOf(Nil, None, parallel = false) shouldBe NewStage(0)
    }

    "insert a new stage at the position, or append when it is past the last stage" in {
      placementOf(steps, Some(1), parallel = false) shouldBe NewStage(1)
      placementOf(steps, Some(10), parallel = false) shouldBe NewStage(3)
    }

    "never go into or above the stage of a default step" in {
      placementOf(steps, Some(0), parallel = false) shouldBe NewStage(1)
      placementOf(steps, Some(0), parallel = true) shouldBe JoinStage(1)
    }

    "join the last stage, or the stage at the position, when parallel" in {
      placementOf(steps, None, parallel = true) shouldBe JoinStage(2)
      placementOf(steps, Some(1), parallel = true) shouldBe JoinStage(1)
    }

    "start a new stage when parallel and there is no stage to join" in {
      placementOf(List(shelf), None, parallel = true) shouldBe NewStage(1)
      placementOf(steps, Some(7), parallel = true) shouldBe NewStage(3)
    }

    "leave gaps alone" in {
      val gap = List(shelf, staged(1), staged(5))
      placementOf(gap, None, parallel = false) shouldBe NewStage(6)
      placementOf(gap, Some(3), parallel = false) shouldBe NewStage(3)
      placementOf(gap, Some(3), parallel = true) shouldBe NewStage(3)
    }
  }

  "MilestoneStepStages.movesFor" should {
    "move every step of the stages from the new one down by one, and nothing when joining" in {
      movesFor(steps, NewStage(2)).toMap shouldBe Map(backend.stepId -> 3, frontend.stepId -> 3)
      movesFor(steps, JoinStage(2)) shouldBe Nil
    }
  }

  "MilestoneStepStages.reorderedPositions" should {
    "keep the default steps first, then number one stage per group" in {
      reorderedPositions(steps, List(List(backend.stepId, frontend.stepId), List(design.stepId))) shouldBe
        Map(shelf.stepId -> 0, backend.stepId -> 1, frontend.stepId -> 1, design.stepId -> 2)
    }

    "skip empty groups" in {
      reorderedPositions(steps, List(Nil, List(design.stepId))).get(design.stepId) shouldBe Some(1)
    }
  }

  "MilestoneStepStages.numberedStages" should {
    "number the distinct requested stages one after the other from the first position" in {
      numberedStages(List(5, 10, 10, 20), 1) shouldBe List(1, 2, 2, 3)
      numberedStages(List(2, 0, 2), 1) shouldBe List(2, 1, 2)
      numberedStages(Nil, 1) shouldBe Nil
    }
  }
}
