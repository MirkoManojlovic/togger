package com.togger.ui.daily

import com.togger.dto.{FindTaskRespFull, SearchAccountDto}
import com.togger.logentry.LogEntryStatus
import com.togger.ui.timebox.gantt.GanttLogStatus

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

case class DailyTaskPlan(
  taskId: UUID,
  name: String,
  statusName: String,
  missingDays: Seq[LocalDate],
  previousStatus: LogEntryStatus
)

case class DailyMemberPlan(member: SearchAccountDto, tasks: Seq[DailyTaskPlan])

sealed trait DailyPosition
object DailyPosition {
  case object ChooseMembers extends DailyPosition
  case class Fill(memberId: UUID, taskId: UUID, date: LocalDate) extends DailyPosition
  case object Review extends DailyPosition
}

/** What the daily asks for each chosen member: their open tasks, in status order, with the days still to log. */
object DailyPlan {

  def build(
    tasks: Seq[FindTaskRespFull],
    members: Seq[SearchAccountDto],
    selectedIds: Set[UUID],
    today: LocalDate,
    workDays: Set[DayOfWeek]
  ): Seq[DailyMemberPlan] =
    members.filter(member => selectedIds.contains(member.id)).flatMap { member =>
      val memberTasks = tasks
        .filter(_.assignedTo.exists(_.id == member.id))
        .flatMap(task => DailyStatusOrder.rank(task.taskStatus.name).map(rank => rank -> task))
        .sortBy { case (rank, task) => (rank, task.name) }
        .map { case (_, task) => taskPlan(task, today, workDays) }
        .filter(_.missingDays.nonEmpty)
      Option.when(memberTasks.nonEmpty)(DailyMemberPlan(member, memberTasks))
    }

  private def taskPlan(task: FindTaskRespFull, today: LocalDate, workDays: Set[DayOfWeek]): DailyTaskPlan = {
    val latest = task.logEntries.maxByOption(_.createdAt)
    DailyTaskPlan(
      taskId = task.id,
      name = task.name,
      statusName = task.taskStatus.name,
      missingDays = MissingLogDays.of(latest.map(e => GanttLogStatus.toLocalDate(e.createdAt)), task.startDate, today, workDays),
      previousStatus = latest.fold[LogEntryStatus](LogEntryStatus.InProgress)(_.status)
    )
  }

  /** Every place Next walks through: choosing members, each day of each task of each member, then the review. */
  def positions(plan: Seq[DailyMemberPlan], todayFirst: Boolean): Seq[DailyPosition] =
    DailyPosition.ChooseMembers +: plan.flatMap { member =>
      member.tasks.flatMap { task =>
        MissingLogDays.inFillOrder(task.missingDays, todayFirst).map(DailyPosition.Fill(member.member.id, task.taskId, _))
      }
    } :+ DailyPosition.Review
}
