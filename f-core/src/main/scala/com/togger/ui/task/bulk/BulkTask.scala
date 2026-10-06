package com.togger.ui.task.bulk

import com.togger.common.WorkDayCalendar
import com.togger.dto.FindTaskStatusResp
import com.togger.task.{DefaultTaskStatus, TaskPriority}

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** @param key
  *   stable identity, so a task keeps its place and attributes while the text it came from is edited
  * @param dueDateManual
  *   the due date was picked, so moving the start date no longer drags it along
  */
case class BulkTask(
  key: UUID,
  name: String,
  priority: TaskPriority,
  statusId: Option[UUID],
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate],
  assigneeId: Option[UUID],
  labelIds: Set[UUID],
  milestoneId: Option[UUID],
  dueDateManual: Boolean = false
)

object BulkTask {
  def withDefaults(key: UUID, name: String, defaults: BulkTaskDefaults): BulkTask =
    BulkTask(
      key = key,
      name = name,
      priority = defaults.priority,
      statusId = defaults.statusId,
      startDate = defaults.startDate,
      dueDate = defaults.dueDate,
      endDate = None,
      assigneeId = None,
      labelIds = Set.empty,
      milestoneId = None
    )
}

case class BulkTaskDefaults(priority: TaskPriority, statusId: Option[UUID], startDate: LocalDate, dueDate: LocalDate)

object BulkTaskDefaults {

  val DefaultWorkDaysCount = 5

  /** p3 medium, backlog, next Monday (the first work day after it when the project doesn't work Mondays), and a due
    * date that many work days later.
    */
  def of(today: LocalDate, workDays: Set[DayOfWeek], statuses: Seq[FindTaskStatusResp]): BulkTaskDefaults = {
    val start = WorkDayCalendar.firstWorkDayFrom(WorkDayCalendar.nextWeekday(today, DayOfWeek.MONDAY), workDays)
    BulkTaskDefaults(
      priority = TaskPriority.Medium,
      statusId = backlogStatus(statuses).map(_.id),
      startDate = start,
      dueDate = WorkDayCalendar.lastDayOfWorkDays(start, DefaultWorkDaysCount, workDays)
    )
  }

  private def backlogStatus(statuses: Seq[FindTaskStatusResp]): Option[FindTaskStatusResp] =
    statuses.find(_.name == DefaultTaskStatus.Backlog.statusName).orElse(statuses.headOption)
}
