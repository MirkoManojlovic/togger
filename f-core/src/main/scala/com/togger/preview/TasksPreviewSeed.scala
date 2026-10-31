package com.togger.preview

import com.togger.common.TgClock
import com.togger.dto.{LinkVersionDto, LogEntryVersionDto, TaskVersionDto}
import com.togger.logentry.LogEntryStatus
import com.togger.task.TaskPriority

import java.time.temporal.ChronoUnit
import java.time.{DayOfWeek, Instant, LocalDate, ZoneOffset}
import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/** Seeds tasks with several versions, links, log entries over the last two months and a few timeboxes. Needs the
  * accounts, projects, task config and milestones to be seeded first.
  */
class TasksPreviewSeed(
  data: TasksPreviewData,
  projects: ProjectsPreviewData,
  clock: TgClock
) extends PreviewSeeder {
  import TasksPreviewSeed._

  private val today = clock.currentDate(ZoneOffset.UTC)
  private val now = clock.currentInstant
  private val counters = mutable.Map.empty[Int, Int]

  private val tasksBuf = ListBuffer.empty[PreviewTask]
  private val taskVersionsBuf = ListBuffer.empty[TaskVersionDto]
  private val linksBuf = ListBuffer.empty[PreviewLink]
  private val linkVersionsBuf = ListBuffer.empty[LinkVersionDto]
  private val logEntriesBuf = ListBuffer.empty[PreviewLogEntry]
  private val logEntryVersionsBuf = ListBuffer.empty[LogEntryVersionDto]

  private def add[A](buffer: ListBuffer[A])(entity: A): Unit = buffer += entity

  private def nextId(group: Int): UUID = {
    val number = counters.getOrElse(group, 0) + 1
    counters.update(group, number)
    PreviewSeedIds.id(group, number)
  }

  private def at(day: LocalDate, hour: Int, minute: Int): Instant =
    day.atTime(hour, minute).toInstant(ZoneOffset.UTC)

  override def seed(): Unit = {
    counters.clear()
    List(tasksBuf, taskVersionsBuf, linksBuf, linkVersionsBuf, logEntriesBuf, logEntryVersionsBuf).foreach(_.clear())
    val seeded = taskSpecs.zipWithIndex.map { case (spec, index) => spec -> seedTask(spec, index + 1) }
    data.tasks.replaceAll(tasksBuf.toList)
    data.taskVersions.replaceAll(taskVersionsBuf.toList)
    data.links.replaceAll(linksBuf.toList)
    data.linkVersions.replaceAll(linkVersionsBuf.toList)
    data.logEntries.replaceAll(logEntriesBuf.toList)
    data.logEntryVersions.replaceAll(logEntryVersionsBuf.toList)
    seedTimeboxes(seeded.map { case (spec, taskId) => spec.number -> taskId }.toMap)
  }

  private def workDaysOf(projectIndex: Int): Set[DayOfWeek] =
    projects.projects.find(TaskConfigPreviewSeed.projectIds(projectIndex)).fold(Set(1, 2, 3, 4, 5))(_.workDays).map(DayOfWeek.of)

  private def workDayFrom(day: LocalDate, workDays: Set[DayOfWeek]): LocalDate =
    Iterator.iterate(day)(_.plusDays(1)).find(d => workDays.contains(d.getDayOfWeek)).get

  private def seedTask(spec: TaskSpec, index: Int): UUID = {
    val workDays = workDaysOf(spec.project)
    val projectId = TaskConfigPreviewSeed.projectIds(spec.project)
    val start = workDayFrom(today.plusDays(spec.start.toLong), workDays)
    val due = workDayFrom(today.plusDays(spec.due.toLong), workDays)
    val end = spec.end.map(offset => workDayFrom(today.plusDays(offset.toLong), workDays)).map(d => if (d.isBefore(start)) start else d)
    val createdAt = at(start.minusDays(2), 9, index)
    val lastChange = end.map(at(_, 17, 0)).getOrElse(now.minus((index % 3 + 1).toLong, ChronoUnit.DAYS)) match {
      case t if t.isAfter(now) => now
      case t                   => t
    }
    val taskId = PreviewSeedIds.id(PreviewSeedIds.Task, index)
    val labelIds = spec.labels.map(TaskConfigPreviewSeed.labelId(spec.project, _)).toList
    add(tasksBuf)(PreviewTask(taskId, projectId, spec.creator, createdAt, spec.archived, labelIds))

    val steps = spec.path.size
    val spanMillis = math.max(ChronoUnit.MILLIS.between(createdAt, lastChange), 0L)
    spec.path.zipWithIndex.foreach { case (statusIndex, i) =>
      val isLast = i == steps - 1
      val versionAt = createdAt.plusMillis(if (steps == 1) 0L else spanMillis * i / (steps - 1))
      val finished = isLast && end.isDefined
      add(taskVersionsBuf)(
        TaskVersionDto(
          PreviewSeedIds.id(PreviewSeedIds.TaskVersion, index * 10 + i),
          taskId,
          spec.name,
          spec.description,
          spec.assignee,
          Option.when(finished && statusIndex == Done)(spec.assignee).flatten,
          TaskConfigPreviewSeed.statusId(spec.project, statusIndex),
          versionAt,
          versionAt,
          if (isLast || steps < 3) spec.priority else lowered(spec.priority),
          spec.milestone,
          start,
          if (isLast || steps < 3 || !due.minusDays(5).isAfter(start)) due else workDayFrom(due.minusDays(5), workDays),
          if (finished) end else None
        )
      )
    }

    spec.links.zipWithIndex.foreach { case ((name, url), j) =>
      val link = PreviewLink(nextId(PreviewSeedIds.Link), taskId, createdAt.plus(1, ChronoUnit.HOURS))
      add(linksBuf)(link)
      if ((index + j) % 4 == 0)
        add(linkVersionsBuf)(
          LinkVersionDto(nextId(PreviewSeedIds.LinkVersion), link.id, name, url.replace("https://", "http://"), link.createdAt, link.createdAt)
        )
      val latestAt = link.createdAt.plus(2, ChronoUnit.DAYS)
      add(linkVersionsBuf)(LinkVersionDto(nextId(PreviewSeedIds.LinkVersion), link.id, name, url, latestAt, latestAt))
    }

    seedLogEntries(spec, index, taskId, start, end, workDays)
    taskId
  }

  private def lowered(priority: TaskPriority): TaskPriority =
    TaskPriority.values.find(_.value == math.min(priority.value + 1, TaskPriority.Planned.value)).getOrElse(priority)

  private def seedLogEntries(
    spec: TaskSpec,
    index: Int,
    taskId: UUID,
    start: LocalDate,
    end: Option[LocalDate],
    workDays: Set[DayOfWeek]
  ): Unit = {
    val lastStatus = spec.path.last
    if (lastStatus == Todo || lastStatus == Backlog) return
    val from = if (start.isBefore(today.minusDays(WindowDays))) today.minusDays(WindowDays) else start
    val to = end.getOrElse(today)
    val members = projects.memberIds(TaskConfigPreviewSeed.projectIds(spec.project))
    val days = Iterator
      .iterate(from)(_.plusDays(1))
      .takeWhile(!_.isAfter(if (to.isAfter(today)) today else to))
      .filter(day => workDays.contains(day.getDayOfWeek))
      .filter(day => (index * 5 + day.toEpochDay) % 4 != 0)
      .toList
    days.zipWithIndex.foreach { case (day, i) =>
      val fromEnd = days.size - 1 - i
      val creator =
        if (day.toEpochDay % 5 == 0 && members.nonEmpty) members(((index + i) % members.size)) else spec.assignee.getOrElse(PreviewDemo.AccountId)
      val status = entryStatus(lastStatus, fromEnd, i)
      val createdAt = at(day, 9 + (i + index) % 8, (index * 7 + i * 13) % 60)
      val entry = PreviewLogEntry(nextId(PreviewSeedIds.LogEntry), taskId, creator, createdAt)
      add(logEntriesBuf)(entry)
      val texts = pool((index + i) % pool.size)
      if ((index + i) % 6 == 0)
        add(logEntryVersionsBuf)(
          LogEntryVersionDto(
            nextId(PreviewSeedIds.LogEntryVersion),
            entry.id,
            texts._1.dropRight(1),
            "",
            LogEntryStatus.InProgress,
            creator,
            createdAt,
            createdAt
          )
        )
      add(logEntryVersionsBuf)(
        LogEntryVersionDto(
          nextId(PreviewSeedIds.LogEntryVersion),
          entry.id,
          texts._1,
          texts._2,
          status,
          creator,
          createdAt,
          createdAt.plus(20, ChronoUnit.MINUTES)
        )
      )
    }
  }

  private def entryStatus(taskStatus: Int, fromEnd: Int, i: Int): LogEntryStatus =
    (taskStatus, fromEnd) match {
      case (Done, 0)         => LogEntryStatus.Completed
      case (WontDo, 0)       => LogEntryStatus.Abandoned
      case (Blocked, 0 | 1)  => LogEntryStatus.Blocked
      case (Waiting, 0 | 1)  => LogEntryStatus.Paused
      case _ if i % 7 == 6   => LogEntryStatus.Paused
      case _                 => LogEntryStatus.InProgress
    }

  private def seedTimeboxes(taskIds: Map[Int, UUID]): Unit = {
    val monday = today.minusDays((today.getDayOfWeek.getValue - 1).toLong)
    def versionIds(numbers: List[Int]): List[UUID] =
      numbers.flatMap(taskIds.get).flatMap(data.latestTaskVersion).map(_.version)

    def timebox(
      number: Int,
      projectIndex: Int,
      start: LocalDate,
      versions: List[(String, String, List[Int], Int)]
    ): Unit = {
      val id = PreviewSeedIds.id(PreviewSeedIds.Timebox, number)
      val createdAt = at(start.minusDays(3), 10, 0)
      data.timeboxes.upsert(PreviewTimebox(id, TaskConfigPreviewSeed.projectIds(projectIndex), createdAt)): Unit
      versions.zipWithIndex.foreach { case ((description, comment, numbers, daysAfter), i) =>
        data.timeboxVersions.upsert(
          PreviewTimeboxVersion(
            PreviewSeedIds.id(PreviewSeedIds.TimeboxVersion, number * 10 + i),
            id,
            start,
            start.plusDays(13),
            Some(description),
            createdAt.plus(daysAfter.toLong, ChronoUnit.DAYS),
            comment,
            versionIds(numbers)
          )
        )
      }
    }

    timebox(
      1,
      0,
      monday.minusWeeks(4),
      List(("Wrap up the board and the 2FA groundwork", "", List(4, 6, 11, 12), 0))
    )
    timebox(
      2,
      0,
      monday,
      List(
        ("Dashboards and search performance", "", List(5, 7, 8, 9, 10, 20), 0),
        ("Dashboards and search performance", "Dropped the CSV export, pulled in the beta invite template", List(5, 8, 9, 10, 20, 13), 2)
      )
    )
    timebox(3, 1, monday, List(("Beta build 0.9", "", List(25, 26, 27, 29), 0)))
  }
}

object TasksPreviewSeed {
  private val WindowDays = 60L

  private val InProgress = 0
  private val Testing = 1
  private val Waiting = 2
  private val Blocked = 3
  private val Todo = 4
  private val Backlog = 5
  private val Done = 6
  private val WontDo = 7

  private val Feature = 0
  private val Bug = 1
  private val Refactor = 2
  private val Test = 3
  private val Docs = 4
  private val Chore = 5

  private val demo = Some(PreviewDemo.AccountId)
  private val second = Some(PreviewDemo.SecondAccountId)
  private val third = Some(AccountsPreviewSeed.ThirdAccountId)
  private val fourth = Some(AccountsPreviewSeed.FourthAccountId)
  private val fifth = Some(AccountsPreviewSeed.FifthAccountId)

  private val foundations = Some(MilestonesPreviewSeed.FoundationsId)
  private val beta = Some(MilestonesPreviewSeed.BetaId)
  private val ga = Some(MilestonesPreviewSeed.GeneralAvailabilityId)
  private val legacy = Some(MilestonesPreviewSeed.LegacySunsetId)
  private val mobileMvp = Some(MilestonesPreviewSeed.MobileMvpId)
  private val mobileBeta = Some(MilestonesPreviewSeed.MobileBetaId)
  private val docsRevamp = Some(MilestonesPreviewSeed.DocsRevampId)
  private val docsTutorials = Some(MilestonesPreviewSeed.DocsTutorialsId)

  private val pool: Vector[(String, String)] = Vector(
    "Investigated the root cause and wrote a failing test." -> "Root cause found, fix in progress.",
    "Implemented the first version, it still needs a review." -> "First version ready for review.",
    "Pairing session to unblock the approach." -> "Agreed on the approach after a pairing session.",
    "Addressed the review comments and pushed a fix." -> "Review comments addressed.",
    "Waiting for the staging deploy to verify the change." -> "Change is on staging for verification.",
    "Cleaned up the edge cases and updated the docs." -> "Edge cases handled, docs updated.",
    "Reproduced it locally and narrowed it down to one module." -> "Reproduced, narrowing down.",
    "Merged after a green build, monitoring the rollout." -> "Merged and rolled out, monitoring."
  )

  /** Day values are offsets from today, moved to the next work day of the project. */
  private case class TaskSpec(
    number: Int,
    project: Int,
    name: String,
    description: String,
    assignee: Option[UUID],
    path: List[Int],
    priority: TaskPriority,
    milestone: Option[UUID],
    start: Int,
    due: Int,
    end: Option[Int],
    labels: List[Int],
    links: List[(String, String)] = List.empty,
    archived: Boolean = false,
    creator: UUID = PreviewDemo.AccountId
  )

  private val taskSpecs: List[TaskSpec] = List(
    TaskSpec(1, 0, "Set up the CI pipeline", "Build, test and deploy to staging on every merge.", third, List(Todo, InProgress, Done), TaskPriority.Medium, foundations, -95, -80, Some(-82), List(Chore)),
    TaskSpec(2, 0, "Session based authentication", "Login, logout and token refresh for the web app.", demo, List(Todo, InProgress, Testing, Done), TaskPriority.High, foundations, -90, -60, Some(-62), List(Feature)),
    TaskSpec(3, 0, "Database migration tooling", "Repeatable migrations with a dry run mode.", second, List(Todo, InProgress, Done), TaskPriority.Medium, foundations, -70, -48, Some(-47), List(Chore, Refactor)),
    TaskSpec(4, 0, "Task board with status columns", "Drag tasks between the status columns of a project.", second, List(Todo, InProgress, Testing, Done), TaskPriority.High, beta, -40, -20, Some(-21), List(Feature), List("Design" -> "https://example.com/design/task-board")),
    TaskSpec(5, 0, "Contribution dashboard charts", "Daily activity and status breakdown per member.", demo, List(Todo, InProgress), TaskPriority.High, beta, -35, 5, None, List(Feature), List("Design doc" -> "https://example.com/docs/contributions", "Pull request" -> "https://example.com/pull/412")),
    TaskSpec(6, 0, "Fix the timezone bug in the daily log", "Entries created late in the evening land on the wrong day.", third, List(Todo, InProgress, Testing, Done), TaskPriority.Critical, beta, -25, -18, Some(-18), List(Bug), List("Incident report" -> "https://example.com/incidents/37")),
    TaskSpec(7, 0, "Export tasks to CSV", "Export the filtered task list including the log entry count.", fifth, List(Backlog, Todo, InProgress, Blocked), TaskPriority.Medium, beta, -14, 3, None, List(Feature)),
    TaskSpec(8, 0, "Milestone date reminders", "Notify the assignees a week before a milestone date.", fourth, List(Todo, InProgress, Waiting), TaskPriority.Medium, beta, -10, 8, None, List(Feature)),
    TaskSpec(9, 0, "Search is slow on large projects", "Searching more than 5000 tasks takes several seconds.", second, List(Todo, InProgress, Testing), TaskPriority.High, beta, -12, 2, None, List(Bug)),
    TaskSpec(10, 0, "Refactor the repository layer", "Split the big repository into smaller read and write parts.", third, List(Backlog, Todo, InProgress), TaskPriority.Low, beta, -8, 6, None, List(Refactor)),
    TaskSpec(11, 0, "Integration tests for authentication", "Cover login, logout and expired sessions.", fourth, List(Todo, InProgress, Done), TaskPriority.Medium, beta, -20, -9, Some(-9), List(Test)),
    TaskSpec(12, 0, "Write the onboarding guide", "A first-day guide for new team members.", fifth, List(Todo, Done), TaskPriority.Low, beta, -18, -12, Some(-13), List(Docs), List("Guide draft" -> "https://example.com/wiki/onboarding")),
    TaskSpec(13, 0, "Beta invitation email template", "Plain text and HTML versions of the invitation.", demo, List(Backlog, Todo), TaskPriority.Medium, beta, 2, 10, None, List(Chore)),
    TaskSpec(14, 0, "Billing integration", "Subscriptions, invoices and the customer portal.", second, List(Backlog, Todo), TaskPriority.High, ga, 14, 40, None, List(Feature), List("Provider docs" -> "https://example.com/billing")),
    TaskSpec(15, 0, "Public status page", "Show incidents and uptime of the main services.", fourth, List(Backlog), TaskPriority.Low, ga, 20, 35, None, List(Feature)),
    TaskSpec(16, 0, "Pricing page copy", "Texts for the three plans and the FAQ.", None, List(Backlog), TaskPriority.Planned, ga, 25, 30, None, List(Docs)),
    TaskSpec(17, 0, "Notify customers still on API v1", "Email every customer that has traffic on the v1 endpoints.", third, List(Todo, InProgress, Waiting), TaskPriority.Medium, legacy, -30, -10, None, List(Chore)),
    TaskSpec(18, 0, "Remove the v1 endpoints", "Delete the v1 routes once the traffic is gone.", None, List(Backlog, Todo), TaskPriority.Low, legacy, -5, 10, None, List(Refactor)),
    TaskSpec(19, 0, "Upgrade the Postgres driver", "Move to the latest minor release.", fifth, List(Todo, Done), TaskPriority.Low, None, -7, -4, Some(-4), List(Chore)),
    TaskSpec(20, 0, "Rate limit login attempts", "Block an address after ten failed logins within a minute.", demo, List(Todo, InProgress, Testing), TaskPriority.High, None, -6, 1, None, List(Feature), List("Security review" -> "https://example.com/security/rate-limits")),
    TaskSpec(21, 0, "Flaky test in the nightly build", "The export test fails about one run in twenty.", fourth, List(Todo, InProgress, Done), TaskPriority.Medium, None, -16, -13, Some(-13), List(Bug, Test)),
    TaskSpec(22, 0, "Spike: GraphQL gateway", "Evaluate a GraphQL layer in front of the REST API.", third, List(Todo, WontDo), TaskPriority.Low, None, -50, -40, Some(-42), List(Refactor), archived = true),
    TaskSpec(23, 1, "Offline log entry queue", "Store entries on the device and sync them later.", second, List(Todo, InProgress, Done), TaskPriority.High, mobileMvp, -65, -40, Some(-42), List(Feature)),
    TaskSpec(24, 1, "Login screen on iOS", "Native login with biometric unlock.", fourth, List(Todo, InProgress, Done), TaskPriority.High, mobileMvp, -60, -45, Some(-46), List(Feature)),
    TaskSpec(25, 1, "Push notifications", "Remind members to write their daily log entry.", demo, List(Todo, InProgress, Testing), TaskPriority.High, mobileBeta, -25, 2, None, List(Feature), List("Notification spec" -> "https://example.com/mobile/push")),
    TaskSpec(26, 1, "Home screen widget", "Show today's tasks and a quick add button.", fourth, List(Backlog, Todo, InProgress), TaskPriority.Medium, mobileBeta, -12, 10, None, List(Feature)),
    TaskSpec(27, 1, "Crash on Android 12 when rotating", "The task detail screen crashes when the device is rotated.", second, List(Todo, InProgress, Done), TaskPriority.Critical, mobileBeta, -9, -6, Some(-6), List(Bug)),
    TaskSpec(28, 1, "Dark mode colors", "Review contrast of all screens in dark mode.", fourth, List(Backlog, Todo), TaskPriority.Low, mobileBeta, 1, 12, None, List(Feature)),
    TaskSpec(29, 1, "App store screenshots", "Screenshots for the six device sizes.", demo, List(Todo, Blocked), TaskPriority.Medium, mobileBeta, -4, 6, None, List(Docs)),
    TaskSpec(30, 1, "Migrate to the new navigation library", "Replace the deprecated navigation stack.", second, List(Backlog), TaskPriority.Planned, None, 5, 30, None, List(Refactor)),
    TaskSpec(31, 2, "Restructure the navigation", "Group the pages by audience instead of by feature.", demo, List(Todo, InProgress, Done), TaskPriority.Medium, docsRevamp, -20, -10, Some(-11), List(Docs)),
    TaskSpec(32, 2, "Add search to the site", "Full text search over all pages.", second, List(Todo, InProgress, Testing), TaskPriority.High, docsRevamp, -14, 3, None, List(Feature)),
    TaskSpec(33, 2, "Write the style guide", "Tone, headings and code sample rules.", demo, List(Todo, InProgress, Done), TaskPriority.Medium, docsRevamp, -18, -8, Some(-8), List(Docs), List("Style guide" -> "https://example.com/docs/style")),
    TaskSpec(34, 2, "Redirect the old URLs", "Map every old URL to its new page.", second, List(Backlog, Todo), TaskPriority.Medium, docsRevamp, 1, 8, None, List(Chore)),
    TaskSpec(35, 2, "Tutorial: your first log entry", "Step by step with screenshots.", demo, List(Todo, WontDo), TaskPriority.Low, docsTutorials, -45, -30, Some(-31), List(Docs)),
    TaskSpec(36, 2, "Fix the broken links report", "The weekly report lists links that were already fixed.", second, List(Todo, InProgress), TaskPriority.Low, None, -5, 4, None, List(Bug))
  )
}
