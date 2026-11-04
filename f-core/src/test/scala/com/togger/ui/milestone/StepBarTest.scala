package com.togger.ui.milestone

import com.togger.milestone.MilestoneStepStatus
import com.togger.ui.components.TgProgressColor
import com.togger.ui.components.TgProgressStyles
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class StepBarTest extends AnyWordSpec with Matchers {

  private val milestone = UUID.randomUUID()

  private def bar(status: MilestoneStepStatus, total: Int, done: Int) =
    StepBar.of(StepTestData.step(milestone, "Build", status = status, tasksTotal = total, tasksDone = done))

  "StepBar.of" should {

    "fill blue with the done tasks over the tasks of a step that is not done" in {
      List(MilestoneStepStatus.Planned, MilestoneStepStatus.InProgress, MilestoneStepStatus.Paused).foreach { status =>
        val result = bar(status, 4, 1)
        (result.value, result.max, result.color) shouldBe ((1, 4, TgProgressColor.Brand))
      }
    }

    "stay blue and empty for a step without tasks that is not done" in {
      val result = bar(MilestoneStepStatus.InProgress, 0, 0)
      (result.color, TgProgressStyles.percent(result.value, result.max)) shouldBe ((TgProgressColor.Brand, 0))
    }

    "stay blue when all tasks are done but the step is not" in {
      val result = bar(MilestoneStepStatus.InProgress, 3, 3)
      (result.color, TgProgressStyles.percent(result.value, result.max)) shouldBe ((TgProgressColor.Brand, 100))
    }

    "turn green when the step is done and all its tasks are done" in {
      val result = bar(MilestoneStepStatus.Done, 3, 3)
      (result.color, TgProgressStyles.percent(result.value, result.max)) shouldBe ((TgProgressColor.Success, 100))
    }

    "be full and green for a done step without tasks" in {
      val result = bar(MilestoneStepStatus.Done, 0, 0)
      (result.color, TgProgressStyles.percent(result.value, result.max)) shouldBe ((TgProgressColor.Success, 100))
    }

    "stay blue and partial for a done step with open tasks" in {
      val result = bar(MilestoneStepStatus.Done, 4, 3)
      (result.color, TgProgressStyles.percent(result.value, result.max)) shouldBe ((TgProgressColor.Brand, 75))
    }

    "be dark for an abandoned step, whatever its tasks" in {
      bar(MilestoneStepStatus.Abandoned, 4, 4).color shouldBe TgProgressColor.Dark
      bar(MilestoneStepStatus.Abandoned, 0, 0).color shouldBe TgProgressColor.Dark
    }

    "name the step with its status, name and task counts" in {
      bar(MilestoneStepStatus.InProgress, 5, 2).title shouldBe "▶️ Build · 2/5 tasks"
    }
  }
}
