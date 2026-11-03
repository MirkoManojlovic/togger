package com.togger.ui.milestonetemplate

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MilestoneStageEditingTest extends AnyWordSpec with Matchers {

  import MilestoneStageEditing._

  "MilestoneStageEditing.sequential and add" should {
    "give every step a stage of its own, also a new one" in {
      sequential(List("a", "b")) shouldBe List(List("a"), List("b"))
      add(sequential(List("a")), "b") shouldBe List(List("a"), List("b"))
    }
  }

  "MilestoneStageEditing.remove" should {
    "remove the step and drop a stage left empty" in {
      remove(List(List("a"), List("b", "c")), 1, 0) shouldBe List(List("a"), List("c"))
      remove(List(List("a"), List("b")), 0, 0) shouldBe List(List("b"))
    }

    "change nothing for an address out of range" in {
      val stages = List(List("a"), List("b"))
      remove(stages, 2, 0) shouldBe stages
      remove(stages, 0, 1) shouldBe stages
    }
  }

  "MilestoneStageEditing.move" should {
    "swap a step alone in its stage with the neighbouring stage" in {
      val stages = List(List("a"), List("b", "c"), List("d"))
      move(stages, 0, 0, 1) shouldBe List(List("b", "c"), List("a"), List("d"))
      move(stages, 2, 0, -1) shouldBe List(List("a"), List("d"), List("b", "c"))
    }

    "stop at the ends" in {
      val stages = List(List("a"), List("b"))
      move(stages, 0, 0, -1) shouldBe stages
      move(stages, 1, 0, 1) shouldBe stages
    }

    "take a step of a parallel stage out into a stage of its own before or after it" in {
      val stages = List(List("a"), List("b", "c"))
      move(stages, 1, 1, -1) shouldBe List(List("a"), List("c"), List("b"))
      move(stages, 1, 0, 1) shouldBe List(List("a"), List("c"), List("b"))
      move(stages, 1, 0, -1) shouldBe List(List("a"), List("b"), List("c"))
    }
  }

  "MilestoneStageEditing.joinPreviousStage" should {
    "run the step in parallel with the previous stage and drop the stage it leaves empty" in {
      joinPreviousStage(List(List("a"), List("b"), List("c")), 1, 0) shouldBe List(List("a", "b"), List("c"))
      joinPreviousStage(List(List("a"), List("b", "c")), 1, 1) shouldBe List(List("a", "c"), List("b"))
    }

    "change nothing for the first stage" in {
      val stages = List(List("a"), List("b"))
      joinPreviousStage(stages, 0, 0) shouldBe stages
    }
  }

  "MilestoneStageEditing.splitFromStage" should {
    "give a step of a parallel stage a stage of its own after it" in {
      splitFromStage(List(List("a", "b"), List("c")), 0, 0) shouldBe List(List("b"), List("a"), List("c"))
    }

    "keep a step alone in its stage" in {
      val stages = List(List("a"), List("b"))
      splitFromStage(stages, 0, 0) shouldBe stages
    }
  }

  "MilestoneStageEditing.rows" should {
    "label a step alone in its stage with the stage number and the steps of a parallel stage with letters" in {
      rows(List(List("a"), List("b", "c"), List("d"))).map(row => row.step -> row.label) shouldBe
        List("a" -> "1", "b" -> "2a", "c" -> "2b", "d" -> "3")
    }

    "give the address of each step" in {
      rows(List(List("a"), List("b", "c"))).map(row => (row.stage, row.indexInStage)) shouldBe
        List((0, 0), (1, 0), (1, 1))
    }
  }

  "MilestoneStageEditing.groupByStage and stageIndexes" should {
    "group the items by stage number in order and give the stage index of each" in {
      val stages = groupByStage(List(5 -> "b", 1 -> "a", 5 -> "c"))

      stages shouldBe List(List("a"), List("b", "c"))
      stageIndexes(stages) shouldBe List(0, 1, 1)
    }
  }
}
