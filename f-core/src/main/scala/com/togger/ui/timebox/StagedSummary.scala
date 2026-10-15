package com.togger.ui.timebox

import com.togger.common.WorkDayCalendar
import com.togger.dto.{FindTaskLabelResp, FindTaskRespFull, FindTaskStatusResp}
import com.togger.task.TaskPriority

import java.time.temporal.ChronoUnit
import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** What the tasks staged for a timebox add up to.
  *
  * @param workDays
  *   the work days of all tasks added up, so two tasks over the same week count twice: it is the effort, not the time
  * @param calendarSpanDays
  *   from the earliest start to the last due date, both included
  * @param outsideTimebox
  *   tasks that start before the timebox or are due after it; 0 while the timebox has no valid dates yet
  */
case class StagedSummary(
  taskCount: Int,
  workDays: Int,
  earliestStart: Option[LocalDate],
  lastDueDate: Option[LocalDate],
  calendarSpanDays: Option[Int],
  byPriority: Seq[(TaskPriority, Int)],
  byStatus: Seq[(FindTaskStatusResp, Int)],
  unassigned: Int,
  byLabel: Seq[(FindTaskLabelResp, Int)],
  outsideTimebox: Int
)

object StagedSummary {

  private def counts[A](values: Seq[A]): Seq[(A, Int)] =
    values.groupBy(identity).toSeq.map { case (value, group) => value -> group.size }

  def of(
    tasks: Seq[FindTaskRespFull],
    workDays: Set[DayOfWeek],
    timebox: Option[(LocalDate, LocalDate)]
  ): StagedSummary = {
    val earliestStart = tasks.map(_.startDate).minOption
    val lastDueDate = tasks.map(_.dueDate).maxOption
    StagedSummary(
      taskCount = tasks.size,
      workDays = tasks.map(task => WorkDayCalendar.countWorkDays(task.startDate, task.dueDate, workDays)).sum,
      earliestStart = earliestStart,
      lastDueDate = lastDueDate,
      calendarSpanDays =
        earliestStart.zip(lastDueDate).map { case (from, to) => ChronoUnit.DAYS.between(from, to).toInt + 1 },
      byPriority = counts(tasks.map(_.priority)).sortBy(_._1.value),
      byStatus = counts(tasks.map(_.taskStatus)).sortBy(-_._2),
      unassigned = tasks.count(_.assignedTo.isEmpty),
      byLabel = counts(tasks.flatMap(_.taskLabels)).sortBy { case (label, count) => (-count, label.name) },
      outsideTimebox = timebox.fold(0) { case (from, to) =>
        tasks.count(task => task.startDate.isBefore(from) || task.dueDate.isAfter(to))
      }
    )
  }
}
