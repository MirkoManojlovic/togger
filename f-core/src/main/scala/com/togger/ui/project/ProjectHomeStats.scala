package com.togger.ui.project

import com.togger.dto.{FindMilestoneResp, FindTaskRespFull, FindTaskStatusResp, FindTimeboxResp, SearchAccountDto}
import com.togger.ui.teamdaily.TeamDailyStatusOrder
import com.togger.ui.timebox.TimeboxReviewStats

import java.time.LocalDate
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
}
