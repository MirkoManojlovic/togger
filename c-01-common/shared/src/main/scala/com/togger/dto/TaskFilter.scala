package com.togger.dto

import com.togger.task.TaskPriority

import java.util.UUID

/** Filters of the task list. An empty list (or no search) leaves that dimension unfiltered. Dimensions are combined
  * with AND, values within one dimension with OR. `labelIds` matches tasks having at least one of the labels.
  * `assigneeIds` may hold `TgIds.UNASSIGNED_UUID` for tasks without assignee and `milestoneIds` may hold
  * `TgIds.NO_MILESTONE_UUID` for tasks without milestone. `search` is a case-insensitive substring of name or
  * description.
  */
case class TaskFilter(
  statusIds: List[UUID] = List.empty,
  priorities: List[TaskPriority] = List.empty,
  labelIds: List[UUID] = List.empty,
  assigneeIds: List[UUID] = List.empty,
  milestoneIds: List[UUID] = List.empty,
  search: Option[String] = None
)

object TaskFilter {
  val empty: TaskFilter = TaskFilter()
}
