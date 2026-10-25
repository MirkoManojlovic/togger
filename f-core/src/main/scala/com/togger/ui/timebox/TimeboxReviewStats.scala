package com.togger.ui.timebox

import com.togger.dto.{FindMilestoneResp, FindTaskRespFull, SearchAccountDto}
import com.togger.ui.apexchart.ApexChartOptions

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The days of a timebox, the start and the end both included. */
case class DaysProgress(elapsed: Int, total: Int) {
  def left: Int = total - elapsed
}

/** The tasks of a timebox and how many of them are done. */
case class TasksProgress(done: Int, total: Int) {
  def left: Int = total - done
}

/** A pie of the review, under its heading. */
case class ReviewPie(title: String, options: ApexChartOptions)

/** What the review of a timebox adds up to, kept apart from the screen so it can be tested without a DOM. */
object TimeboxReviewStats {

  /** Tasks count as done while their status has this name, whatever its case. */
  val DoneStatusName = "Done"

  val OverallGroupings: Seq[StagedGrouping] = Seq(
    StagedGrouping.ByStatus,
    StagedGrouping.ByMilestone,
    StagedGrouping.ByPriority,
    StagedGrouping.ByLabel,
    StagedGrouping.ByAssignee
  )

  val MemberGroupings: Seq[StagedGrouping] = OverallGroupings.filterNot(_ == StagedGrouping.ByAssignee)

  /** Before the timebox starts nothing has elapsed, after it ends everything has. */
  def daysProgress(start: LocalDate, end: LocalDate, today: LocalDate): DaysProgress = {
    val total = ChronoUnit.DAYS.between(start, end).toInt + 1
    val left = ChronoUnit.DAYS.between(today, end).toInt.max(0).min(total)
    DaysProgress(elapsed = total - left, total = total)
  }

  def tasksProgress(tasks: Seq[FindTaskRespFull]): TasksProgress =
    TasksProgress(done = tasks.count(isDone), total = tasks.size)

  def isDone(task: FindTaskRespFull): Boolean = task.taskStatus.name.trim.equalsIgnoreCase(DoneStatusName)

  def tasksOf(member: SearchAccountDto, tasks: Seq[FindTaskRespFull]): Seq[FindTaskRespFull] =
    tasks.filter(_.assignedTo.exists(_.id == member.id))

  /** The tasks of each assignee, by name, the unassigned tasks last. */
  def tasksPerMember(tasks: Seq[FindTaskRespFull]): Seq[(Option[SearchAccountDto], Seq[FindTaskRespFull])] =
    tasks
      .groupBy(_.assignedTo)
      .toSeq
      .sortBy { case (assignee, _) => (assignee.isEmpty, assignee.map(_.fullName)) }

  def pies(
    groupings: Seq[StagedGrouping],
    tasks: Seq[FindTaskRespFull],
    milestones: Seq[FindMilestoneResp]
  ): Seq[ReviewPie] =
    groupings.map(grouping =>
      ReviewPie(grouping.label, StagedCharts.pieOfGroups(grouping.group(tasks, milestones)))
    )
}
