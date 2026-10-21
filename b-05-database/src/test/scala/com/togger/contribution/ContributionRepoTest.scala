package com.togger.contribution

import cats.implicits._
import com.togger.account.{Account, AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.dto.ContributionsFilter
import com.togger.logentry.{LogEntry, LogEntryRepoImpl, LogEntryStatus, LogEntryVersion, LogEntryVersionRepoImpl}
import com.togger.milestone.{Milestone, MilestoneRepoImpl, MilestoneStatus, MilestoneVersion, MilestoneVersionRepoImpl}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.task.{
  Task,
  TaskLabel,
  TaskLabelAssignment,
  TaskLabelAssignmentRepoImpl,
  TaskLabelRepoImpl,
  TaskPriority,
  TaskRepoImpl,
  TaskVersion,
  TaskVersionRepoImpl
}
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
  private val taskLabelRepo = new TaskLabelRepoImpl
  private val taskLabelAssignmentRepo = new TaskLabelAssignmentRepoImpl
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
    * `first`, still open. t2 is "Done" and critical, the others are in the first status and medium. t1 is labelled
    * "bug", t2 "bug" and "feature", t3 has no label.
    *
    * Log entries: e1 of t1 by `first` on the 13th, completed by its latest version (an older one was in progress); e2
    * of t1 by `first` on the 13th, in progress; e3 of t2 by `second` on the 14th at 23:30 UTC, completed; e4 of t3 by
    * `first` on the 20th, on vacation.
    */
  private case class Seeded(
    projectId: UUID,
    first: Account,
    second: Account,
    milestone: Milestone,
    statusName: String
  )

  private def seed(): ConnectionIO[Seeded] =
    for {
      first <- createAccount
      second <- new AccountRepoImpl().insert(first.copy(id = UUID.randomUUID(), email = "second@email.com"))
      orgAndProject <- createOrgAndProject
      projectId = orgAndProject._2.id
      status <- createTaskStatus(projectId)
      done <- taskStatusRepo.insert(taskStatus.copy(id = UUID.randomUUID(), projectId = projectId, name = "Done"))
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
        done.id,
        1,
        priority = TaskPriority.Critical,
        assignedTo = Some(second.id),
        closedBy = Some(second.id),
        milestone = Some(milestone.id),
        startDate = day(13),
        endDate = Some(day(20))
      )
      _ <- newTaskVersion(t3, status.id, 1, assignedTo = Some(first.id), startDate = day(15))
      bug <- newLabel(projectId, "bug")
      feature <- newLabel(projectId, "feature")
      _ <- taskLabelAssignmentRepo.insert(TaskLabelAssignment(t1.id, bug.id, nowAsInstant))
      _ <- taskLabelAssignmentRepo.insert(TaskLabelAssignment(t2.id, bug.id, nowAsInstant))
      _ <- taskLabelAssignmentRepo.insert(TaskLabelAssignment(t2.id, feature.id, nowAsInstant))
      _ <- newEntry(t1, first, "2026-10-13T10:00:00Z", LogEntryStatus.InProgress, LogEntryStatus.Completed)
      _ <- newEntry(t1, first, "2026-10-13T15:00:00Z", LogEntryStatus.InProgress)
      _ <- newEntry(t2, second, "2026-10-14T23:30:00Z", LogEntryStatus.Completed)
      _ <- newEntry(t3, first, "2026-10-20T08:00:00Z", LogEntryStatus.Vacation)
    } yield Seeded(projectId, first, second, milestone, status.name)

  private def newTask(projectId: UUID, creator: Account): ConnectionIO[Task] =
    taskRepo.insert(Task(UUID.randomUUID(), projectId, creator.id, nowAsInstant))

  private def newLabel(projectId: UUID, name: String): ConnectionIO[TaskLabel] =
    taskLabelRepo.insert(
      TaskLabel(UUID.randomUUID(), projectId, name, s"$name description", nowAsInstant, nowAsInstant)
    )

  private def newTaskVersion(
    task: Task,
    statusId: UUID,
    number: Long,
    assignedTo: Option[UUID] = None,
    closedBy: Option[UUID] = None,
    milestone: Option[UUID] = None,
    priority: TaskPriority = TaskPriority.Medium,
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
        priority = priority,
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

    "count the tasks by the status of their latest version, in their milestone" in { transactor =>
      (for {
        seeded <- seed()
        rows <- contributionRepo.tasksByStatus(seeded.projectId, ContributionsFilter.empty)
      } yield rows.toSet shouldBe Set(
        TaskStatusCountRow(Some(seeded.milestone.id), seeded.statusName, 1L),
        TaskStatusCountRow(Some(seeded.milestone.id), "Done", 1L),
        TaskStatusCountRow(None, seeded.statusName, 1L)
      )).transact(transactor)
    }

    "count the tasks by the priority of their latest version" in { transactor =>
      (for {
        seeded <- seed()
        rows <- contributionRepo.tasksByPriority(seeded.projectId, ContributionsFilter.empty)
      } yield rows.toSet shouldBe Set(
        TaskPriorityCountRow(Some(seeded.milestone.id), TaskPriority.Medium, 1L),
        TaskPriorityCountRow(Some(seeded.milestone.id), TaskPriority.Critical, 1L),
        TaskPriorityCountRow(None, TaskPriority.Medium, 1L)
      )).transact(transactor)
    }

    "count a task once for every label it has, and the tasks without a label together" in { transactor =>
      (for {
        seeded <- seed()
        rows <- contributionRepo.tasksByLabel(seeded.projectId, ContributionsFilter.empty)
      } yield rows.toSet shouldBe Set(
        TaskLabelCountRow(Some(seeded.milestone.id), Some("bug"), 2L),
        TaskLabelCountRow(Some(seeded.milestone.id), Some("feature"), 1L),
        TaskLabelCountRow(None, None, 1L)
      )).transact(transactor)
    }

    "filter the attribute counts by the start date of the latest version" in { transactor =>
      (for {
        seeded <- seed()
        statuses <- contributionRepo.tasksByStatus(seeded.projectId, range(14, 31))
        priorities <- contributionRepo.tasksByPriority(seeded.projectId, range(14, 31))
        labels <- contributionRepo.tasksByLabel(seeded.projectId, range(14, 31))
      } yield {
        statuses shouldBe List(TaskStatusCountRow(None, seeded.statusName, 1L))
        priorities shouldBe List(TaskPriorityCountRow(None, TaskPriority.Medium, 1L))
        labels shouldBe List(TaskLabelCountRow(None, None, 1L))
      }).transact(transactor)
    }

    "list the milestones of the project by their creation time, newest first, with the latest version of each" in { transactor =>
      (for {
        seeded <- seed()
        older <- milestoneRepo.insert(
          Milestone(UUID.randomUUID(), seeded.projectId, seeded.milestone.createdAt.minusSeconds(3600))
        )
        _ <- milestoneVersion(seeded.milestone, "Old name", MilestoneStatus.InProgress, day(30))
        _ <- milestoneVersion(seeded.milestone, "First", MilestoneStatus.Done, day(20))
        _ <- milestoneVersion(older, "Second", MilestoneStatus.InProgress, day(25))
        rows <- contributionRepo.milestoneHeaders(seeded.projectId)
        otherProject <- contributionRepo.milestoneHeaders(UUID.randomUUID())
      } yield {
        // created later and due earlier: the creation time decides
        rows shouldBe List(
          MilestoneHeaderRow(seeded.milestone.id, "First", MilestoneStatus.Done, day(1), day(20)),
          MilestoneHeaderRow(older.id, "Second", MilestoneStatus.InProgress, day(1), day(25))
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
