package com.togger.contribution

import com.togger.account.Account
import com.togger.common.WorkDayCalendar
import com.togger.dto.{
  ContributionsDto,
  ContributionsFilter,
  DailyStatusCountDto,
  MemberContributionDto,
  MilestoneContributionsDto,
  StatusCountDto
}
import com.togger.logentry.LogEntryStatus

import java.time.DayOfWeek
import java.util.UUID

/** Turns the aggregate rows into the contributions of the members: over the whole project and per milestone. */
object ContributionAssembler {

  val NoMilestoneName: String = "No milestone"

  // Days off are listed among the entries of a member, but they are not work.
  private val daysOff: Set[LogEntryStatus] = Set(LogEntryStatus.Holiday, LogEntryStatus.Vacation)

  case class Rows(
    milestones: Seq[MilestoneHeaderRow],
    taskTotals: Seq[MilestoneTaskTotalsRow],
    activity: Seq[EntryActivityRow],
    assigned: Seq[TaskAssignedRow],
    closed: Seq[TaskClosedRow]
  )

  /** The tasks without a milestone come first, then the milestones in the order of the rows.
    *
    * Everyone who is a member is listed in the general part, even without activity. Rows of accounts that left the
    * project are dropped. A milestone lists the members that did something in it.
    */
  def assemble(
    projectId: UUID,
    members: Seq[Account],
    workDays: Set[DayOfWeek],
    filter: ContributionsFilter,
    rows: Rows
  ): ContributionsDto = {
    val memberIds = members.map(_.id).toSet
    val activity = rows.activity.filter(row => memberIds.contains(row.memberId))
    val assigned = rows.assigned.filter(row => memberIds.contains(row.memberId))
    val closed = rows.closed.filter(row => memberIds.contains(row.memberId))
    val workDaysInRange = countWorkDaysInRange(filter, activity, workDays)
    val sortedMembers = members.sortBy(member => (nameOf(member).toLowerCase, member.id.toString))

    // `inScope` picks the rows of a scope by their milestone; a milestone card lists only the members that did
    // something in it, the general part lists all of them.
    def membersIn(inScope: Option[UUID] => Boolean, onlyActive: Boolean): List[MemberContributionDto] =
      sortedMembers.toList.flatMap { member =>
        val memberActivity = activity.filter(row => row.memberId == member.id && inScope(row.milestoneId))
        val memberAssigned = assigned.filter(row => row.memberId == member.id && inScope(row.milestoneId))
        val memberClosed = closed.filter(row => row.memberId == member.id && inScope(row.milestoneId))
        val hasNothing = memberActivity.isEmpty && memberAssigned.isEmpty && memberClosed.isEmpty
        Option.when(!(onlyActive && hasNothing))(
          memberDto(member, workDaysInRange, memberActivity, memberAssigned, memberClosed)
        )
      }

    def totalsOf(milestoneId: Option[UUID]): (Int, Int) = {
      val totals = rows.taskTotals.filter(_.milestoneId == milestoneId)
      (totals.map(_.total).sum.toInt, totals.map(_.closed).sum.toInt)
    }

    val milestoneCards = rows.milestones.toList.map { header =>
      val (total, closedCount) = totalsOf(Some(header.milestoneId))
      MilestoneContributionsDto(
        milestoneId = Some(header.milestoneId),
        name = header.name,
        status = Some(header.status),
        startDate = Some(header.startDate),
        dueDate = Some(header.dueDate),
        totalTasks = total,
        closedTasks = closedCount,
        members = membersIn(_.contains(header.milestoneId), onlyActive = true)
      )
    }

    val withoutMilestone = {
      val (total, closedCount) = totalsOf(None)
      val noMilestoneMembers = membersIn(_.isEmpty, onlyActive = true)
      Option.when(total > 0 || noMilestoneMembers.nonEmpty)(
        MilestoneContributionsDto(None, NoMilestoneName, None, None, None, total, closedCount, noMilestoneMembers)
      )
    }

    ContributionsDto(projectId, membersIn(_ => true, onlyActive = false), withoutMilestone.toList ++ milestoneCards)
  }

  private def memberDto(
    member: Account,
    workDaysInRange: Int,
    activity: Seq[EntryActivityRow],
    assigned: Seq[TaskAssignedRow],
    closed: Seq[TaskClosedRow]
  ): MemberContributionDto = {
    val dailyEntries = activity
      .filterNot(row => daysOff.contains(row.status))
      .groupMapReduce(row => (row.day, row.status))(_.count)(_ + _)
      .toList
      .sortBy { case ((day, status), _) => (day, status.value) }
      .map { case ((day, status), count) => DailyStatusCountDto(day, status, count.toInt) }
    MemberContributionDto(
      memberId = member.id,
      memberName = nameOf(member),
      entriesByStatus = activity
        .groupMapReduce(_.status)(_.count)(_ + _)
        .toList
        .sortBy(_._1.value)
        .map { case (status, count) => StatusCountDto(status, count.toInt) },
      tasksAssigned = assigned.map(_.count).sum.toInt,
      tasksClosed = closed.map(_.count).sum.toInt,
      activeDays = dailyEntries.map(_.date).distinct.size,
      workDaysInRange = workDaysInRange,
      dailyEntries = dailyEntries
    )
  }

  // An open end of the range is closed by the first or last day with activity, so the numbers don't depend on today.
  private def countWorkDaysInRange(
    filter: ContributionsFilter,
    activity: Seq[EntryActivityRow],
    workDays: Set[DayOfWeek]
  ): Int = {
    val days = activity.map(_.day)
    (filter.from.orElse(days.minOption), filter.to.orElse(days.maxOption)) match {
      case (Some(from), Some(to)) if !to.isBefore(from) => WorkDayCalendar.countWorkDays(from, to, workDays)
      case _                                            => 0
    }
  }

  private def nameOf(member: Account): String = s"${member.firstName} ${member.lastName}"
}
