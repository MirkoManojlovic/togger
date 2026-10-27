package com.togger.logentry

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.milestone.{Milestone, MilestoneRepoImpl}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.task.{Task, TaskPriority, TaskRepoImpl, TaskVersion, TaskVersionRepoImpl}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.{Instant, LocalDate}
import java.util.UUID

class LogEntryRepoMilestoneTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  private val logEntryRepo = new LogEntryRepoImpl
  private val logEntryVersionRepo = new LogEntryVersionRepoImpl
  private val taskRepo = new TaskRepoImpl
  private val taskVersionRepo = new TaskVersionRepoImpl
  private val milestoneRepo = new MilestoneRepoImpl

  private def day(dayOfMonth: Int): LocalDate = LocalDate.of(2026, 10, dayOfMonth)

  private def newTask(projectId: UUID, creator: UUID, statusId: UUID, milestone: Option[UUID]): ConnectionIO[Task] =
    for {
      task <- taskRepo.insert(Task(UUID.randomUUID(), projectId, creator, nowAsInstant))
      _ <- taskVersionRepo.insert(
        TaskVersion(
          version = UUID.randomUUID(),
          taskId = task.id,
          name = "Task",
          description = "Description",
          assignedTo = None,
          closedBy = None,
          taskStatusId = statusId,
          milestone = milestone,
          priority = TaskPriority.Medium,
          startDate = day(1),
          dueDate = day(28),
          endDate = None,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
    } yield task

  /** Inserts an entry with one version per text, the last text being the latest version. */
  private def newEntry(task: Task, author: UUID, createdAt: String, texts: String*): ConnectionIO[LogEntry] =
    for {
      entry <- logEntryRepo.insert(LogEntry(UUID.randomUUID(), task.id, author, Instant.parse(createdAt)))
      _ <- texts.toList.traverse(text =>
        logEntryVersionRepo.insert(
          LogEntryVersion(
            UUID.randomUUID(),
            entry.id,
            text,
            s"$text external",
            LogEntryStatus.InProgress,
            author,
            nowAsInstant,
            nowAsInstant
          )
        )
      )
    } yield entry

  testNameFromClassName should {

    "find the latest version of the milestone's log entries on the inclusive days, oldest first" in { transactor =>
      (for {
        account <- createAccount
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        status <- createTaskStatus(projectId)
        milestone <- milestoneRepo.insert(Milestone(UUID.randomUUID(), projectId, nowAsInstant))
        other <- milestoneRepo.insert(Milestone(UUID.randomUUID(), projectId, nowAsInstant))
        inMilestone <- newTask(projectId, account.id, status.id, Some(milestone.id))
        inOther <- newTask(projectId, account.id, status.id, Some(other.id))
        withoutMilestone <- newTask(projectId, account.id, status.id, None)
        _ <- newEntry(inMilestone, account.id, "2026-10-09T23:59:59Z", "before range")
        first <- newEntry(inMilestone, account.id, "2026-10-10T00:00:00Z", "old text", "first latest")
        second <- newEntry(inMilestone, account.id, "2026-10-12T23:59:59Z", "second latest")
        _ <- newEntry(inMilestone, account.id, "2026-10-13T00:00:00Z", "after range")
        _ <- newEntry(inOther, account.id, "2026-10-11T10:00:00Z", "other milestone")
        _ <- newEntry(withoutMilestone, account.id, "2026-10-11T10:00:00Z", "no milestone")
        found <- logEntryRepo.findLatestByMilestoneIdBetween(milestone.id, day(10), day(12), 10)
      } yield {
        (found.map(_.logEntry.id), found.map(_.version.textInternal)) shouldBe (
          List(first.id, second.id),
          List("first latest", "second latest")
        )
      }).transact(transactor)
    }

    "stop at the limit" in { transactor =>
      (for {
        account <- createAccount
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        status <- createTaskStatus(projectId)
        milestone <- milestoneRepo.insert(Milestone(UUID.randomUUID(), projectId, nowAsInstant))
        task <- newTask(projectId, account.id, status.id, Some(milestone.id))
        _ <- newEntry(task, account.id, "2026-10-10T10:00:00Z", "one")
        _ <- newEntry(task, account.id, "2026-10-11T10:00:00Z", "two")
        found <- logEntryRepo.findLatestByMilestoneIdBetween(milestone.id, day(10), day(12), 1)
      } yield found.map(_.version.textInternal) shouldBe List("one")).transact(transactor)
    }
  }
}
