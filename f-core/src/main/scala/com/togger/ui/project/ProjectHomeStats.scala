package com.togger.ui.project

import com.togger.dto.{FindMilestoneResp, FindTaskRespFull, FindTaskStatusResp, FindTimeboxResp, SearchAccountDto}
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.task.bulk.BadgeSpec
import com.togger.ui.teamdaily.{TeamDailyStatusOrder, TeamDailyTaskBadges}
import com.togger.ui.timebox.TimeboxReviewStats

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** What the project home picks out of the project, kept apart from the screen so it can be tested without a DOM. */
object ProjectHomeStats {

  /** A timebox has no status of its own, it is active while today lies within the dates of its latest version. */
  def activeTimeboxes(timeboxes: Seq[FindTimeboxResp], today: LocalDate): Seq[FindTimeboxResp] =
    timeboxes
      .filter(timebox =>
        !today.isBefore(timebox.latestVersion.startDate) && !today.isAfter(timebox.latestVersion.endDate)
      )
      .sortBy(_.latestVersion.endDate.toEpochDay)

  /** The statuses of tasks that are being worked on, in the order of the daily. */
  def inFlightStatuses(statuses: Seq[FindTaskStatusResp]): Seq[FindTaskStatusResp] =
    statuses
      .flatMap(status => TeamDailyStatusOrder.rank(status.name).map(_ -> status))
      .sortBy(_._1)
      .map(_._2)

  /** The tasks in the given statuses, grouped by status in the order of the statuses; statuses without tasks are left
    * out.
    */
  def tasksByStatus(
    statuses: Seq[FindTaskStatusResp],
    tasks: Seq[FindTaskRespFull]
  ): Seq[(FindTaskStatusResp, Seq[FindTaskRespFull])] = {
    val byStatusId: Map[UUID, Seq[FindTaskRespFull]] = tasks.groupBy(_.taskStatus.id)
    statuses.flatMap(status => byStatusId.get(status.id).map(status -> _.sortBy(_.name)))
  }

  /** The tasks being worked on, grouped by their status in the order of the daily; no group is empty. */
  def activeTasksByStatus(tasks: Seq[FindTaskRespFull]): Seq[(FindTaskStatusResp, Seq[FindTaskRespFull])] =
    tasks
      .flatMap(task => TeamDailyStatusOrder.rank(task.taskStatus.name).map(rank => (rank, task)))
      .groupBy(_._2.taskStatus.id)
      .values
      .map(group => (group.head._1, group.head._2.taskStatus, group.map(_._2).sortBy(_.name)))
      .toSeq
      .sortBy(_._1)
      .map { case (_, status, tasks) => status -> tasks }

  /** The milestones the member has tasks in, each with only the tasks of the member. */
  def milestonesOf(
    member: SearchAccountDto,
    milestones: Seq[(FindMilestoneResp, Seq[FindTaskRespFull])]
  ): Seq[(FindMilestoneResp, Seq[FindTaskRespFull])] =
    milestones.flatMap { case (milestone, tasks) =>
      Some(TimeboxReviewStats.tasksOf(member, tasks)).filter(_.nonEmpty).map(milestone -> _)
    }

  /** The groups of tasks in flight, with the tasks of the member only; no group is empty. */
  def inFlightOf(
    member: SearchAccountDto,
    inFlight: Seq[(FindTaskStatusResp, Seq[FindTaskRespFull])]
  ): Seq[(FindTaskStatusResp, Seq[FindTaskRespFull])] =
    inFlight.flatMap { case (status, tasks) =>
      Some(TimeboxReviewStats.tasksOf(member, tasks)).filter(_.nonEmpty).map(status -> _)
    }

  /** The labels of a task, sorted by name, to go beside its name. */
  def labelBadges(task: FindTaskRespFull): Seq[BadgeSpec] =
    task.taskLabels.sortBy(_.name).map(label => BadgeSpec(label.badgeText, label.badgeColor))

  /** The milestone, the priority, the assignee and the due date with the work days left of a task; a task without a
    * milestone or an assignee has no badge for them.
    */
  def taskBadges(
    task: FindTaskRespFull,
    milestones: Seq[FindMilestoneResp],
    showAssignee: Boolean,
    today: LocalDate,
    workDays: Set[DayOfWeek],
    formatDate: LocalDate => String
  ): Seq[BadgeSpec] =
    task.milestone
      .flatMap(id => milestones.find(_.id == id))
      .map(milestone => BadgeSpec(milestone.name, TgBadgeColor.Alternative))
      .toSeq ++
      Seq(BadgeSpec(task.priority.shortCode, task.priority.badgeColor)) ++
      Option.when(showAssignee)(task.assignedTo).flatten.map(member => BadgeSpec(member.fullName, TgBadgeColor.Gray)) ++
      Seq(
        BadgeSpec(formatDate(task.dueDate), TgBadgeColor.Gray),
        TeamDailyTaskBadges.daysLeft(task.dueDate, today, workDays)
      )

  /** A row for each assignee of the tasks, by name, the unassigned tasks last; a row has the tasks of the assignee for
    * each of the statuses of the daily, in its order.
    */
  def memberRows(
    inFlight: Seq[(FindTaskStatusResp, Seq[FindTaskRespFull])]
  ): Seq[(Option[SearchAccountDto], Seq[Seq[FindTaskRespFull]])] = {
    val ranked = inFlight.flatMap { case (_, tasks) =>
      tasks.flatMap(task => TeamDailyStatusOrder.rank(task.taskStatus.name).map(_ -> task))
    }
    val columns = 0 until TeamDailyStatusOrder.StatusCount
    ranked
      .groupBy(_._2.assignedTo)
      .toSeq
      .sortBy { case (assignee, _) => (assignee.isEmpty, assignee.map(_.fullName)) }
      .map { case (assignee, group) =>
        assignee -> columns.map(column => group.collect { case (rank, task) if rank == column => task }.sortBy(_.name))
      }
  }
}
