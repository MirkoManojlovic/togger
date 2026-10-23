package com.togger.ui.slacker

import com.togger.dto.FindTaskRespFull
import com.togger.logentry.LogEntryStatus
import com.togger.ui.teamdaily.{TeamDailyEntryDraft, TeamDailyStatusGroup, TeamDailyStatusOrder, TeamDailyTaskPlan}
import com.togger.ui.timebox.gantt.GanttLogStatus

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

sealed trait SlackerPosition
object SlackerPosition {
  case object ChooseTasks extends SlackerPosition
  case class Fill(taskId: UUID, date: LocalDate) extends SlackerPosition
  case object Review extends SlackerPosition
}

case class SlackerEntryKey(taskId: UUID, date: LocalDate)

/** What the slacker asks for: the open tasks of the user, in status order, with every day still to log. */
object SlackerPlan {

  def build(
    tasks: Seq[FindTaskRespFull],
    me: UUID,
    workDays: Set[DayOfWeek],
    today: LocalDate
  ): Seq[TeamDailyTaskPlan] =
    tasks
      .filter(_.assignedTo.exists(_.id == me))
      .flatMap(task => TeamDailyStatusOrder.rank(task.taskStatus.name).map(rank => rank -> task))
      .sortBy { case (rank, task) => (rank, task.name) }
      .map { case (_, task) => taskPlan(task, workDays, today) }
      .filter(_.missingDays.nonEmpty)

  private def taskPlan(task: FindTaskRespFull, workDays: Set[DayOfWeek], today: LocalDate): TeamDailyTaskPlan =
    TeamDailyTaskPlan(
      taskId = task.id,
      name = task.name,
      statusName = task.taskStatus.name,
      statusEmoji = task.taskStatus.emoji,
      missingDays = SlackerMissingDays.of(
        task.logEntries.map(e => GanttLogStatus.toLocalDate(e.createdAt)),
        task.startDate,
        today,
        workDays
      ),
      previousStatus = task.logEntries.maxByOption(_.createdAt).fold[LogEntryStatus](LogEntryStatus.InProgress)(_.status),
      priority = task.priority,
      milestoneId = task.milestone,
      labels = task.taskLabels,
      startDate = task.startDate,
      dueDate = task.dueDate,
      logEntries = task.logEntries,
      description = task.description
    )

  def taskIds(candidates: Seq[TeamDailyTaskPlan]): Set[UUID] = candidates.map(_.taskId).toSet

  /** The tasks that were chosen; `None` means all of them. */
  def chosen(candidates: Seq[TeamDailyTaskPlan], selected: Option[Set[UUID]]): Seq[TeamDailyTaskPlan] =
    candidates.filter(task => selected.forall(_.contains(task.taskId)))

  /** The tasks under the status they have, in the order of the daily statuses and then by name. */
  def byStatus(tasks: Seq[TeamDailyTaskPlan]): Seq[TeamDailyStatusGroup] =
    tasks
      .groupBy(task => TeamDailyStatusOrder.rank(task.statusName))
      .toSeq
      .sortBy { case (rank, _) => rank.getOrElse(Int.MaxValue) }
      .map { case (_, sameStatus) =>
        val sorted = sameStatus.sortBy(_.name)
        TeamDailyStatusGroup(sorted.head.statusName, sorted, sorted.head.statusEmoji)
      }

  def daysOf(task: TeamDailyTaskPlan, pastFirst: Set[UUID]): Seq[LocalDate] =
    SlackerDirection.of(pastFirst.contains(task.taskId)).order(task.missingDays)

  /** Every place Next walks through: choosing tasks, each day of each task in its direction, then the review. */
  def positions(plan: Seq[TeamDailyTaskPlan], pastFirst: Set[UUID]): Seq[SlackerPosition] =
    SlackerPosition.ChooseTasks +: plan.flatMap(task =>
      daysOf(task, pastFirst).map(SlackerPosition.Fill(task.taskId, _))
    ) :+ SlackerPosition.Review

  /** A day starts as the day walked just before it, or as the last status of the task for the first one. */
  def defaultEntry(
    task: TeamDailyTaskPlan,
    pastFirst: Set[UUID],
    date: LocalDate,
    entries: Map[SlackerEntryKey, TeamDailyEntryDraft]
  ): TeamDailyEntryDraft = {
    val days = daysOf(task, pastFirst)
    val index = days.indexOf(date)
    val before = Option.when(index > 0)(days(index - 1))
    val status = before
      .flatMap(day => entries.get(SlackerEntryKey(task.taskId, day)))
      .fold(task.previousStatus)(_.status)
    TeamDailyEntryDraft(status, "")
  }
}
