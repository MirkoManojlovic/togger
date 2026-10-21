package com.togger.contribution

import cats.implicits._
import com.togger.account.{Account, AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.dto.ContributionsFilter
import com.togger.logentry.{LogEntry, LogEntryRepoImpl, LogEntryStatus, LogEntryVersion, LogEntryVersionRepoImpl}
import com.togger.milestone.{Milestone, MilestoneRepoImpl, MilestoneStatus, MilestoneVersion, MilestoneVersionRepoImpl}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.task.{Task, TaskRepoImpl, TaskVersion, TaskVersionRepoImpl}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.{Instant, LocalDate}
import java.util.UUID

class ContributionRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  private val contributionRepo = new ContributionRepoImpl
  private val taskRepo = new TaskRepoImpl
  private val taskVersionRepo = new TaskVersionRepoImpl
  private val milestoneRepo = new MilestoneRepoImpl
  private val milestoneVersionRepo = new MilestoneVersionRepoImpl
  private val logEntryRepo = new LogEntryRepoImpl
  private val logEntryVersionRepo = new LogEntryVersionRepoImpl

  private def day(dayOfMonth: Int): LocalDate = LocalDate.of(2026, 10, dayOfMonth)

  private def range(from: Int, to: Int): ContributionsFilter =
    ContributionsFilter(Some(day(from)), Some(day(to)))

  // the repos give every inserted version a new time ordered id, so versions inserted later are the newer ones
  private def versionId(number: Long): UUID = new UUID(number, 0L)

  /** Tasks: t1 in the milestone, assigned to and closed by `first`, older version assigned to `second` without
    * a milestone; t2 in the milestone, assigned to and closed by `second`; t3 without a milestone, assigned to
    * `first`, still open.
    *
    * Log entries: e1 of t1 by `first` on the 13th, completed by its latest version (an older one was in progress); e2
    * of t1 by `first` on the 13th, in progress; e3 of t2 by `second` on the 14th at 23:30 UTC, completed; e4 of t3 by
    * `first` on the 20th, on vacation.
    */
  private case class Seeded(projectId: UUID, first: Account, second: Account, milestone: Milestone)

  private def seed(): ConnectionIO[Seeded] =
    for {
      first <- createAccount
      second <- new AccountRepoImpl().insert(first.copy(id = UUID.randomUUID(), email = "second@email.com"))
      orgAndProject <- createOrgAndProject
      projectId = orgAndProject._2.id
      status <- createTaskStatus(projectId)
      milestone <- milestoneRepo.insert(Milestone(UUID.randomUUID(), projectId, nowAsInstant))
      t1 <- newTask(projectId, first)
      t2 <- newTask(projectId, first)
      t3 <- newTask(projectId, first)
      _ <- newTaskVersion(t1, status.id, 1, assignedTo = Some(second.id))
      _ <- newTaskVersion(
        t1,
        status.id,
        2,
        assignedTo = Some(first.id),
        closedBy = Some(first.id),
        milestone = Some(milestone.id),
        startDate = day(12),
        endDate = Some(day(18))
      )
      _ <- newTaskVersion(
        t2,
        status.id,
        1,
        assignedTo = Some(second.id),
        closedBy = Some(second.id),
        milestone = Some(milestone.id),
        startDate = day(13),
        endDate = Some(day(20))
      )
      _ <- newTaskVersion(t3, status.id, 1, assignedTo = Some(first.id), startDate = day(15))
      _ <- newEntry(t1, first, "2026-10-13T10:00:00Z", LogEntryStatus.InProgress, LogEntryStatus.Completed)
      _ <- newEntry(t1, first, "2026-10-13T15:00:00Z", LogEntryStatus.InProgress)
      _ <- newEntry(t2, second, "2026-10-14T23:30:00Z", LogEntryStatus.Completed)
      _ <- newEntry(t3, first, "2026-10-20T08:00:00Z", LogEntryStatus.Vacation)
    } yield Seeded(projectId, first, second, milestone)

  private def newTask(projectId: UUID, creator: Account): ConnectionIO[Task] =
    taskRepo.insert(Task(UUID.randomUUID(), projectId, creator.id, nowAsInstant))

  private def newTaskVersion(
    task: Task,
    statusId: UUID,
    number: Long,
    assignedTo: Option[UUID] = None,
    closedBy: Option[UUID] = None,
    milestone: Option[UUID] = None,
    startDate: LocalDate = day(12),
    endDate: Option[LocalDate] = None
  ): ConnectionIO[TaskVersion] =
    taskVersionRepo.insert(
      TaskVersion(
        version = versionId(number),
        taskId = task.id,
        name = s"Task $number",
        description = "Description",
        assignedTo = assignedTo,
        closedBy = closedBy,
        taskStatusId = statusId,
        milestone = milestone,
        startDate = startDate,
        dueDate = day(19),
        endDate = endDate,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

  private def milestoneVersion(
    milestone: Milestone,
    name: String,
    status: MilestoneStatus,
    dueDate: LocalDate
  ): ConnectionIO[MilestoneVersion] =
    milestoneVersionRepo.insert(
      MilestoneVersion(
        version = UUID.randomUUID(),
        milestoneId = milestone.id,
        name = name,
        description = "Description",
        status = status,
        startDate = day(1),
        dueDate = dueDate,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

  /** The statuses are the versions of the entry from the oldest to the latest. */
  private def newEntry(task: Task, author: Account, createdAt: String, statuses: LogEntryStatus*): ConnectionIO[Unit] =
    for {
      entry <- logEntryRepo.insert(LogEntry(UUID.randomUUID(), task.id, author.id, Instant.parse(createdAt)))
      _ <- statuses.toList.zipWithIndex.traverse { case (status, index) =>
        logEntryVersionRepo.insert(
          LogEntryVersion(
            version = versionId(index + 1L),
            logEntryId = entry.id,
            textInternal = "Internal",
            textExternal = "External",
            status = status,
            createdBy = author.id,
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
      }
    } yield ()

  testNameFromClassName should {

    "aggregate the log entries by author, milestone, day and latest status, and keep the unfiltered vacation entry" in {
      transactor =>
        (for {
          seeded <- seed()
          rows <- contributionRepo.entryActivity(seeded.projectId, ContributionsFilter.empty)
        } yield {
          val milestone = Some(seeded.milestone.id)
          rows.toSet shouldBe Set(
            EntryActivityRow(seeded.first.id, milestone, day(13), LogEntryStatus.Completed, 1L),
            EntryActivityRow(seeded.first.id, milestone, day(13), LogEntryStatus.InProgress, 1L),
            EntryActivityRow(seeded.second.id, milestone, day(14), LogEntryStatus.Completed, 1L),
            EntryActivityRow(seeded.first.id, None, day(20), LogEntryStatus.Vacation, 1L)
          )
        }).transact(transactor)
    }

    "filter the log entries by the inclusive UTC days of the range" in { transactor =>
      (for {
        seeded <- seed()
        onlyFirstDay <- contributionRepo.entryActivity(seeded.projectId, range(13, 13))
        onlySecond <- contributionRepo.entryActivity(seeded.projectId, range(14, 14))
        fromTheFourteenth <- contributionRepo.entryActivity(seeded.projectId, ContributionsFilter(from = Some(day(14))))
        upToTheThirteenth <- contributionRepo.entryActivity(seeded.projectId, ContributionsFilter(to = Some(day(13))))
        none <- contributionRepo.entryActivity(seeded.projectId, range(15, 19))
      } yield {
        onlyFirstDay.map(_.memberId).toSet shouldBe Set(seeded.first.id)
        onlyFirstDay.map(_.count).sum shouldBe 2L
        onlySecond.map(_.memberId) shouldBe List(seeded.second.id)
        fromTheFourteenth.map(_.count).sum shouldBe 2L
        upToTheThirteenth.map(_.count).sum shouldBe 2L
        none shouldBe empty
      }).transact(transactor)
    }

    "only see the log entries of the project" in { transactor =>
      (for {
        _ <- seed()
        rows <- contributionRepo.entryActivity(UUID.randomUUID(), ContributionsFilter.empty)
      } yield rows shouldBe empty).transact(transactor)
    }

    "count the tasks by the assignee and milestone of their latest version" in { transactor =>
      (for {
        seeded <- seed()
        rows <- contributionRepo.tasksAssigned(seeded.projectId, ContributionsFilter.empty)
      } yield {
        val milestone = Some(seeded.milestone.id)
        rows.toSet shouldBe Set(
          TaskAssignedRow(seeded.first.id, milestone, 1L),
          TaskAssignedRow(seeded.second.id, milestone, 1L),
          TaskAssignedRow(seeded.first.id, None, 1L)
        )
      }).transact(transactor)
    }

    "filter the assigned tasks by the start date of the latest version" in { transactor =>
      (for {
        seeded <- seed()
        rows <- contributionRepo.tasksAssigned(seeded.projectId, range(14, 31))
      } yield rows shouldBe List(TaskAssignedRow(seeded.first.id, None, 1L))).transact(transactor)
    }

    "count the closed tasks by the closer and milestone of their latest version" in { transactor =>
      (for {
        seeded <- seed()
        rows <- contributionRepo.tasksClosed(seeded.projectId, ContributionsFilter.empty)
      } yield {
        val milestone = Some(seeded.milestone.id)
        rows.toSet shouldBe Set(
          TaskClosedRow(seeded.first.id, milestone, 1L),
          TaskClosedRow(seeded.second.id, milestone, 1L)
        )
      }).transact(transactor)
    }

    "filter the closed tasks by their end date" in { transactor =>
      (for {
        seeded <- seed()
        rows <- contributionRepo.tasksClosed(seeded.projectId, range(19, 31))
      } yield rows shouldBe List(TaskClosedRow(seeded.second.id, Some(seeded.milestone.id), 1L)))
        .transact(transactor)
    }

    "list the milestones of the project by the due date of their latest version" in { transactor =>
      (for {
        seeded <- seed()
        later <- milestoneRepo.insert(Milestone(UUID.randomUUID(), seeded.projectId, nowAsInstant))
        _ <- milestoneVersion(seeded.milestone, "Old name", MilestoneStatus.InProgress, day(30))
        _ <- milestoneVersion(seeded.milestone, "First", MilestoneStatus.Done, day(20))
        _ <- milestoneVersion(later, "Second", MilestoneStatus.InProgress, day(25))
        rows <- contributionRepo.milestoneHeaders(seeded.projectId)
        otherProject <- contributionRepo.milestoneHeaders(UUID.randomUUID())
      } yield {
        rows shouldBe List(
          MilestoneHeaderRow(seeded.milestone.id, "First", MilestoneStatus.Done, day(1), day(20)),
          MilestoneHeaderRow(later.id, "Second", MilestoneStatus.InProgress, day(1), day(25))
        )
        otherProject shouldBe empty
      }).transact(transactor)
    }

    "count all and ended tasks per milestone, with the tasks without a milestone together" in { transactor =>
      (for {
        seeded <- seed()
        rows <- contributionRepo.milestoneTaskTotals(seeded.projectId)
      } yield rows.toSet shouldBe Set(
        MilestoneTaskTotalsRow(Some(seeded.milestone.id), 2L, 2L),
        MilestoneTaskTotalsRow(None, 1L, 0L)
      )).transact(transactor)
    }
  }
}
