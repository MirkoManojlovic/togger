package com.togger.ui.task.bulk

import com.togger.common.WorkDayCalendar
import com.togger.task.TaskPriority

import java.time.temporal.ChronoUnit
import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** @param workDays
  *   the work days of all tasks added up, so two tasks over the same week count twice: it is the effort, not the time
  * @param calendarSpanDays
  *   from the earliest start to the last due date, both included
  */
case class BulkSummary(
  taskCount: Int,
  workDays: Int,
  earliestStart: Option[LocalDate],
  latestStart: Option[LocalDate],
  lastDueDate: Option[LocalDate],
  calendarSpanDays: Option[Int],
  byPriority: Seq[(TaskPriority, Int)],
  byStatus: Seq[(Option[UUID], Int)],
  byLabel: Seq[(UUID, Int)],
  byMilestone: Seq[(UUID, Int)],
  unassigned: Int,
  labelsUsed: Int
)

object BulkSummary {

  private def counts[A](values: Seq[A]): Seq[(A, Int)] =
    values.groupBy(identity).toSeq.map { case (value, group) => value -> group.size }

  def of(tasks: Seq[BulkTask], workDays: Set[DayOfWeek]): BulkSummary = {
    val earliestStart = tasks.map(_.startDate).minOption
    val lastDueDate = tasks.map(_.dueDate).maxOption
    BulkSummary(
      taskCount = tasks.size,
      workDays = tasks.map(task => WorkDayCalendar.countWorkDays(task.startDate, task.dueDate, workDays)).sum,
      earliestStart = earliestStart,
      latestStart = tasks.map(_.startDate).maxOption,
      lastDueDate = lastDueDate,
      calendarSpanDays =
        earliestStart.zip(lastDueDate).map { case (from, to) => ChronoUnit.DAYS.between(from, to).toInt + 1 },
      byPriority = counts(tasks.map(_.priority)).sortBy(_._1.value),
      byStatus = counts(tasks.map(_.statusId)).sortBy(-_._2),
      byLabel = counts(tasks.flatMap(_.labelIds)),
      byMilestone = counts(tasks.flatMap(_.milestoneId)),
      unassigned = tasks.count(_.assigneeId.isEmpty),
      labelsUsed = tasks.flatMap(_.labelIds).distinct.size
    )
  }
}
