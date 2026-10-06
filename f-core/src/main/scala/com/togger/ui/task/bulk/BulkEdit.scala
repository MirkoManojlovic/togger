package com.togger.ui.task.bulk

import com.togger.common.WorkDayCalendar
import com.togger.task.TaskPriority

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

sealed trait BulkEdit

object BulkEdit {
  case class SetPriority(priority: TaskPriority) extends BulkEdit
  case class SetStatus(statusId: UUID) extends BulkEdit
  case class SetMilestone(milestoneId: Option[UUID]) extends BulkEdit
  case class SetAssignee(assigneeId: Option[UUID]) extends BulkEdit
  case class AddLabels(labelIds: Set[UUID]) extends BulkEdit
  case class RemoveLabels(labelIds: Set[UUID]) extends BulkEdit
  case class SetLabels(labelIds: Set[UUID]) extends BulkEdit
  case class SetStartDate(date: LocalDate) extends BulkEdit
  case class SetDueDate(date: LocalDate) extends BulkEdit

  /** The due date that ends this many work days after the start, the start day counting as the first. */
  case class SetWorkDays(count: Int) extends BulkEdit

  val MaxWorkDays = 250
}

object BulkTaskEditor {

  /** The tasks with the edit applied to the selected ones. A date that is not a work day, or a due date before a task's
    * start, leaves that task as it was, so a bulk edit can never produce a task the backend would reject.
    */
  def apply(tasks: Seq[BulkTask], selected: Set[UUID], edit: BulkEdit, workDays: Set[DayOfWeek]): Seq[BulkTask] =
    tasks.map(task => if (selected.contains(task.key)) applyTo(task, edit, workDays) else task)

  private def applyTo(task: BulkTask, edit: BulkEdit, workDays: Set[DayOfWeek]): BulkTask =
    edit match {
      case BulkEdit.SetPriority(priority)   => task.copy(priority = priority)
      case BulkEdit.SetStatus(statusId)     => task.copy(statusId = Some(statusId))
      case BulkEdit.SetMilestone(milestone) => task.copy(milestoneId = milestone)
      case BulkEdit.SetAssignee(assignee)   => task.copy(assigneeId = assignee)
      case BulkEdit.AddLabels(labelIds)     => task.copy(labelIds = task.labelIds ++ labelIds)
      case BulkEdit.RemoveLabels(labelIds)  => task.copy(labelIds = task.labelIds -- labelIds)
      case BulkEdit.SetLabels(labelIds)     => task.copy(labelIds = labelIds)
      case BulkEdit.SetStartDate(date)      => withStartDate(task, date, workDays)
      case BulkEdit.SetDueDate(date)        => withDueDate(task, date, workDays)
      case BulkEdit.SetWorkDays(count)      => withWorkDays(task, count, workDays)
    }

  /** The due date follows the start date, keeping the task's length in work days, unless it was picked by hand and is
    * still on or after the new start.
    */
  private def withStartDate(task: BulkTask, date: LocalDate, workDays: Set[DayOfWeek]): BulkTask = {
    if (!workDays.contains(date.getDayOfWeek)) return task
    val length = WorkDayCalendar.countWorkDays(task.startDate, task.dueDate, workDays).max(1)
    val keepDueDate = task.dueDateManual && !task.dueDate.isBefore(date)
    task.copy(
      startDate = date,
      dueDate = if (keepDueDate) task.dueDate else WorkDayCalendar.lastDayOfWorkDays(date, length, workDays)
    )
  }

  private def withWorkDays(task: BulkTask, count: Int, workDays: Set[DayOfWeek]): BulkTask =
    if (workDays.isEmpty) task
    else
      task.copy(
        dueDate = WorkDayCalendar.lastDayOfWorkDays(task.startDate, count.max(1).min(BulkEdit.MaxWorkDays), workDays),
        dueDateManual = true
      )

  private def withDueDate(task: BulkTask, date: LocalDate, workDays: Set[DayOfWeek]): BulkTask =
    if (!workDays.contains(date.getDayOfWeek) || date.isBefore(task.startDate)) task
    else task.copy(dueDate = date, dueDateManual = true)
}

object BulkSelection {

  /** Every key from `anchor` to `target` in list order, both included; just the target if the anchor is gone. */
  def rangeBetween(orderedKeys: Seq[UUID], anchor: UUID, target: UUID): Set[UUID] = {
    val (from, to) = (orderedKeys.indexOf(anchor), orderedKeys.indexOf(target))
    if (from == -1 || to == -1) Set(target)
    else orderedKeys.slice(from.min(to), from.max(to) + 1).toSet
  }

  /** A click on a task's checkbox: shift extends from the last click, a plain click toggles that task. */
  def click(
    selected: Set[UUID],
    orderedKeys: Seq[UUID],
    anchor: Option[UUID],
    target: UUID,
    shift: Boolean
  ): (Set[UUID], Option[UUID]) =
    anchor.filter(_ => shift) match {
      case Some(from) => (selected ++ rangeBetween(orderedKeys, from, target), Some(target))
      case None =>
        (if (selected.contains(target)) selected - target else selected + target, Some(target))
    }

  /** Selects everything, or clears the selection when everything already is selected. */
  def toggleAll(selected: Set[UUID], orderedKeys: Seq[UUID]): Set[UUID] =
    if (orderedKeys.nonEmpty && orderedKeys.forall(selected.contains)) Set.empty else orderedKeys.toSet

  /** Drops keys of tasks that no longer exist, e.g. after their line was deleted. */
  def prune(selected: Set[UUID], orderedKeys: Seq[UUID]): Set[UUID] = selected.intersect(orderedKeys.toSet)
}
