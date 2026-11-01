package com.togger.ui.milestone

import com.togger.dto.FindMilestoneStepResp
import com.togger.milestone.MilestoneStepStatus
import com.togger.ui.components.TgProgressColor

/** What the progress bar of a step on the project home page shows: its tasks done over its tasks, in blue; green once
  * the step is Done and all its tasks are done; dark for an abandoned step.
  */
case class StepBar(value: Int, max: Int, color: TgProgressColor, title: String)

object StepBar {

  def of(step: FindMilestoneStepResp): StepBar = {
    val title = s"${step.status.emoji} ${step.name} · ${step.tasksDone}/${step.tasksTotal} tasks"
    val allTasksDone = step.tasksDone >= step.tasksTotal
    step.status match {
      case MilestoneStepStatus.Abandoned =>
        StepBar(step.tasksDone, step.tasksTotal, TgProgressColor.Dark, title)
      // A Done step without tasks has nothing left, so it is full.
      case MilestoneStepStatus.Done if allTasksDone && step.tasksTotal == 0 =>
        StepBar(1, 1, TgProgressColor.Success, title)
      case MilestoneStepStatus.Done if allTasksDone =>
        StepBar(step.tasksDone, step.tasksTotal, TgProgressColor.Success, title)
      case _ => StepBar(step.tasksDone, step.tasksTotal, TgProgressColor.Brand, title)
    }
  }
}
