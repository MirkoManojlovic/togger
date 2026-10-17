package com.togger.chart

import com.togger.dto.FindTaskStatusResp
import com.togger.task.DefaultTaskStatus

sealed abstract class TaskStatusChartable(val defaultStatus: DefaultTaskStatus, val chartColor: ChartColor)
    extends Chartable {
  val key: String = s"task-status.${defaultStatus.entryName}"
  val label: String = defaultStatus.statusName
}

object TaskStatusBlockedChartable extends TaskStatusChartable(DefaultTaskStatus.Blocked, ChartColor.Red)
object TaskStatusInProgressChartable extends TaskStatusChartable(DefaultTaskStatus.InProgress, ChartColor.Blue)
object TaskStatusTestingChartable extends TaskStatusChartable(DefaultTaskStatus.Testing, ChartColor.Blue)
object TaskStatusWaitingChartable extends TaskStatusChartable(DefaultTaskStatus.Waiting, ChartColor.Yellow)
object TaskStatusTodoChartable extends TaskStatusChartable(DefaultTaskStatus.Todo, ChartColor.Gray)
object TaskStatusBacklogChartable extends TaskStatusChartable(DefaultTaskStatus.Backlog, ChartColor.Gray)
object TaskStatusDoneChartable extends TaskStatusChartable(DefaultTaskStatus.Done, ChartColor.Green)
object TaskStatusWontDoChartable extends TaskStatusChartable(DefaultTaskStatus.WontDo, ChartColor.Dark)
object TaskStatusArchivedChartable extends TaskStatusChartable(DefaultTaskStatus.Archived, ChartColor.Dark)

/** A project's task statuses are free-form rows rather than [[DefaultTaskStatus]], so a status is matched to its
  * [[Chartable]] by name; a name that matches none is gray.
  */
object TaskStatusChartable {

  val all: Seq[TaskStatusChartable] = Seq(
    TaskStatusInProgressChartable,
    TaskStatusTestingChartable,
    TaskStatusWaitingChartable,
    TaskStatusBlockedChartable,
    TaskStatusTodoChartable,
    TaskStatusBacklogChartable,
    TaskStatusDoneChartable,
    TaskStatusWontDoChartable,
    TaskStatusArchivedChartable
  )

  def of(statusName: String): Chartable =
    all.find(_.label == statusName).getOrElse(Chartable.Unknown(s"task-status.$statusName", statusName))

  def of(taskStatus: FindTaskStatusResp): Chartable = of(taskStatus.name)

}
