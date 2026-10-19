package com.togger.ui.teamdaily

import com.togger.dto.{FindTaskLabelResp, FindTaskRespFull, LogEntryDto, SearchAccountDto}
import com.togger.logentry.LogEntryStatus
import com.togger.task.TaskPriority
import com.togger.ui.timebox.gantt.GanttLogStatus

import java.time.LocalDate
import java.util.UUID

case class TeamDailyTaskPlan(
  taskId: UUID,
  name: String,
  statusName: String,
  missingDays: Seq[LocalDate],
  previousStatus: LogEntryStatus,
  priority: TaskPriority,
  milestoneId: Option[UUID],
  labels: Seq[FindTaskLabelResp],
  startDate: LocalDate,
  dueDate: LocalDate,
  /** The newest log entry, shown while filling in today's so the user sees what was said last. */
  latestEntry: Option[LogEntryDto] = None,
  description: String = ""
)

case class TeamDailyMemberPlan(member: SearchAccountDto, tasks: Seq[TeamDailyTaskPlan])

/** The tasks of one member that have one status. */
case class TeamDailyStatusGroup(statusName: String, tasks: Seq[TeamDailyTaskPlan])

sealed trait TeamDailyPosition
object TeamDailyPosition {
  case object ChooseMembers extends TeamDailyPosition
  case object ChooseTasks extends TeamDailyPosition
  case class Fill(memberId: UUID, taskId: UUID, date: LocalDate) extends TeamDailyPosition
  case object Review extends TeamDailyPosition
}

/** What the daily asks for each chosen member: their open tasks, in status order, with the days still to log. */
object TeamDailyPlan {

  def build(
    tasks: Seq[FindTaskRespFull],
    members: Seq[SearchAccountDto],
    selectedIds: Set[UUID],
    today: LocalDate
  ): Seq[TeamDailyMemberPlan] =
    members.filter(member => selectedIds.contains(member.id)).flatMap { member =>
      val memberTasks = tasks
        .filter(_.assignedTo.exists(_.id == member.id))
        .flatMap(task => TeamDailyStatusOrder.rank(task.taskStatus.name).map(rank => rank -> task))
        .sortBy { case (rank, task) => (rank, task.name) }
        .map { case (_, task) => taskPlan(task, today) }
        .filter(_.missingDays.nonEmpty)
      Option.when(memberTasks.nonEmpty)(TeamDailyMemberPlan(member, memberTasks))
    }

  private def taskPlan(task: FindTaskRespFull, today: LocalDate): TeamDailyTaskPlan = {
    val latest = task.logEntries.maxByOption(_.createdAt)
    TeamDailyTaskPlan(
      taskId = task.id,
      name = task.name,
      statusName = task.taskStatus.name,
      missingDays = MissingLogDays.of(task.logEntries.map(e => GanttLogStatus.toLocalDate(e.createdAt)), today),
      previousStatus = latest.fold[LogEntryStatus](LogEntryStatus.InProgress)(_.status),
      priority = task.priority,
      milestoneId = task.milestone,
      labels = task.taskLabels,
      startDate = task.startDate,
      dueDate = task.dueDate,
      latestEntry = latest,
      description = task.description
    )
  }

  def taskIds(candidates: Seq[TeamDailyMemberPlan]): Set[UUID] =
    candidates.flatMap(_.tasks.map(_.taskId)).toSet

  /** The tasks that were chosen; `None` means all of them. Members left without a task are dropped. */
  def chosen(candidates: Seq[TeamDailyMemberPlan], selected: Option[Set[UUID]]): Seq[TeamDailyMemberPlan] =
    candidates.flatMap { member =>
      val tasks = member.tasks.filter(task => selected.forall(_.contains(task.taskId)))
      Option.when(tasks.nonEmpty)(member.copy(tasks = tasks))
    }

  /** The tasks of a member, under the status they have, in the order of the daily statuses and then by name. */
  def byStatus(tasks: Seq[TeamDailyTaskPlan]): Seq[TeamDailyStatusGroup] =
    tasks
      .groupBy(task => TeamDailyStatusOrder.rank(task.statusName))
      .toSeq
      .sortBy { case (rank, _) => rank.getOrElse(Int.MaxValue) }
      .map { case (_, sameStatus) =>
        val sorted = sameStatus.sortBy(_.name)
        TeamDailyStatusGroup(sorted.head.statusName, sorted)
      }

  /** Every place Next walks through: choosing members, choosing tasks, each task of each member, then the review. */
  def positions(plan: Seq[TeamDailyMemberPlan]): Seq[TeamDailyPosition] =
    TeamDailyPosition.ChooseMembers +: TeamDailyPosition.ChooseTasks +: plan.flatMap { member =>
      member.tasks.flatMap { task =>
        task.missingDays.map(TeamDailyPosition.Fill(member.member.id, task.taskId, _))
      }
    } :+ TeamDailyPosition.Review
}
