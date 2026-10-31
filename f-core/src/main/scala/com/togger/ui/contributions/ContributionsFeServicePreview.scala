package com.togger.ui.contributions

import cats.effect.IO
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.WorkDayCalendar
import com.togger.dto._
import com.togger.logentry.LogEntryStatus
import com.togger.milestone.MilestoneStatus
import com.togger.preview._
import com.togger.task.TaskPriority

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** Mirrors the backend aggregation: the tasks are counted by their latest version, the log entries by their latest
  * status, and the numbers are cut to the optional date range.
  */
class ContributionsFeServicePreview(
  ctx: PreviewContext,
  projects: ProjectsPreviewData,
  milestones: MilestonesPreviewData,
  taskConfig: TaskConfigPreviewData,
  accounts: AccountsPreviewData,
  data: TasksPreviewData
) extends ContributionsFeService {
  import ContributionsFeServicePreview._

  override def findByProjectId(projectId: UUID, filter: ContributionsFilter): TgRespIO[ContributionsDto] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        projects.projects.find(projectId) match {
          case None          => PreviewResponses.notFound[ContributionsDto]
          case Some(project) => PreviewResponses.ok(assemble(project, filter))
        }
      )
    )

  private case class TaskRow(task: PreviewTask, version: TaskVersionDto) {
    def milestoneId: Option[UUID] = version.milestone
  }

  private case class Activity(memberId: UUID, milestoneId: Option[UUID], day: LocalDate, status: LogEntryStatus)

  private def assemble(project: PreviewProject, filter: ContributionsFilter): ContributionsDto = {
    val taskRows = data.tasks
      .filter(_.projectId == project.id)
      .flatMap(task => data.latestTaskVersion(task.id).map(TaskRow(task, _)))
    val members = accounts.accounts.filter(account => projects.memberIds(project.id).contains(account.id))
    val memberIds = members.map(_.id).toSet

    def inRange(date: LocalDate): Boolean =
      filter.from.forall(!date.isBefore(_)) && filter.to.forall(!date.isAfter(_))

    val taskById = taskRows.map(row => row.task.id -> row).toMap
    val activity = data.logEntries.all
      .flatMap(entry => taskById.get(entry.taskId).zip(data.latestLogEntryVersion(entry.id)).map { case (row, version) =>
        Activity(entry.createdBy, row.milestoneId, data.dayOf(entry.createdAt), version.status)
      })
      .filter(row => memberIds.contains(row.memberId) && inRange(row.day))
    val started = taskRows.filter(row => inRange(row.version.startDate))
    val assigned = taskRows.filter(row => row.version.assignedTo.exists(memberIds.contains) && inRange(row.version.startDate))
    val closed = taskRows.filter(row => row.version.closedBy.exists(memberIds.contains) && row.version.endDate.exists(inRange))

    val workDays = project.workDays.map(DayOfWeek.of)
    val days = activity.map(_.day)
    val workDaysInRange = (filter.from.orElse(days.minOption), filter.to.orElse(days.maxOption)) match {
      case (Some(from), Some(to)) if !to.isBefore(from) => WorkDayCalendar.countWorkDays(from, to, workDays)
      case _                                            => 0
    }
    val sortedMembers = members.sortBy(member => (nameOf(member).toLowerCase, member.id.toString))

    def tasksIn(rows: List[TaskRow], inScope: Option[UUID] => Boolean, ofAssignee: Option[UUID] => Boolean = _ => true) = {
      val scoped = rows.filter(row => inScope(row.milestoneId) && ofAssignee(row.version.assignedTo))
      TaskAttributesDto(
        byStatus = scoped
          .flatMap(row => taskConfig.statuses.find(row.version.taskStatusId).map(_.name))
          .groupBy(identity)
          .map { case (name, names) => name -> names.size }
          .toList
          .sortBy { case (name, count) => (-count, name) }
          .map { case (name, count) => TaskStatusCountDto(name, count) },
        byPriority = scoped
          .groupBy(_.version.priority)
          .map { case (priority, group) => priority -> group.size }
          .toList
          .sortBy { case (priority, _) => priority.value }
          .map { case (priority, count) => TaskPriorityCountDto(priority, count) },
        byLabel = scoped
          .flatMap { row =>
            val names = row.task.labelIds.flatMap(taskConfig.labels.find).map(label => Option(label.name))
            if (names.isEmpty) List(Option.empty[String]) else names
          }
          .groupBy(identity)
          .map { case (name, names) => name -> names.size }
          .toList
          .sortBy { case (name, count) => (-count, name.isEmpty, name) }
          .map { case (name, count) => TaskLabelCountDto(name, count) }
      )
    }

    def membersIn(inScope: Option[UUID] => Boolean, onlyActive: Boolean): List[MemberContributionDto] =
      sortedMembers.flatMap { member =>
        val memberActivity = activity.filter(row => row.memberId == member.id && inScope(row.milestoneId))
        val memberAssigned = assigned.filter(row => row.version.assignedTo.contains(member.id) && inScope(row.milestoneId))
        val memberClosed = closed.filter(row => row.version.closedBy.contains(member.id) && inScope(row.milestoneId))
        val hasNothing = memberActivity.isEmpty && memberAssigned.isEmpty && memberClosed.isEmpty
        Option.when(!(onlyActive && hasNothing))(
          memberDto(
            member,
            workDaysInRange,
            memberActivity,
            memberAssigned.size,
            memberClosed.size,
            tasksIn(started, inScope, _.contains(member.id))
          )
        )
      }

    def totalsOf(milestoneId: Option[UUID]): (Int, Int) = {
      val rows = taskRows.filter(_.milestoneId == milestoneId)
      (rows.size, rows.count(_.version.endDate.isDefined))
    }

    val milestoneCards = milestones.milestones
      .filter(_.projectId == project.id)
      .sortBy(m => (-m.startDate.toEpochDay, -m.createdAt.toEpochMilli, m.id.toString))
      .map { milestone =>
        val (total, closedCount) = totalsOf(Some(milestone.id))
        MilestoneContributionsDto(
          milestoneId = Some(milestone.id),
          name = milestone.name,
          description = Some(milestone.description),
          status = Some(milestone.status),
          startDate = Some(milestone.startDate),
          dueDate = Some(milestone.dueDate),
          totalTasks = total,
          closedTasks = closedCount,
          members = membersIn(_.contains(milestone.id), onlyActive = true),
          tasks = tasksIn(started, _.contains(milestone.id))
        )
      }

    val withoutMilestone = {
      val (total, closedCount) = totalsOf(None)
      val noMilestoneMembers = membersIn(_.isEmpty, onlyActive = true)
      Option.when(total > 0 || noMilestoneMembers.nonEmpty)(
        MilestoneContributionsDto(
          milestoneId = None,
          name = NoMilestoneName,
          description = None,
          status = Option.empty[MilestoneStatus],
          startDate = None,
          dueDate = None,
          totalTasks = total,
          closedTasks = closedCount,
          members = noMilestoneMembers,
          tasks = tasksIn(started, _.isEmpty)
        )
      )
    }

    ContributionsDto(
      project.id,
      membersIn(_ => true, onlyActive = false),
      tasksIn(started, _ => true),
      withoutMilestone.toList ++ milestoneCards
    )
  }

  private def memberDto(
    member: PreviewAccount,
    workDaysInRange: Int,
    activity: List[Activity],
    tasksAssigned: Int,
    tasksClosed: Int,
    tasks: TaskAttributesDto
  ): MemberContributionDto = {
    val dailyEntries = activity
      .filterNot(row => daysOff.contains(row.status))
      .groupBy(row => (row.day, row.status))
      .map { case (key, rows) => key -> rows.size }
      .toList
      .sortBy { case ((day, status), _) => (day.toEpochDay, status.value) }
      .map { case ((day, status), count) => DailyStatusCountDto(day, status, count) }
    MemberContributionDto(
      memberId = member.id,
      memberName = nameOf(member),
      entriesByStatus = activity
        .groupBy(_.status)
        .map { case (status, rows) => status -> rows.size }
        .toList
        .sortBy(_._1.value)
        .map { case (status, count) => StatusCountDto(status, count) },
      tasksAssigned = tasksAssigned,
      tasksClosed = tasksClosed,
      activeDays = dailyEntries.map(_.date).distinct.size,
      workDaysInRange = workDaysInRange,
      dailyEntries = dailyEntries,
      tasks = tasks
    )
  }

  private def nameOf(member: PreviewAccount): String = s"${member.firstName} ${member.lastName}"
}

object ContributionsFeServicePreview {
  val NoMilestoneName: String = "No milestone"

  private val daysOff: Set[LogEntryStatus] = Set(LogEntryStatus.Holiday, LogEntryStatus.Vacation)
}
