package com.togger.maintenance

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.togger.account._
import com.togger.common.TgIds
import com.togger.logentry.{LogEntry, LogEntryStatus, LogEntryVersion}
import com.togger.milestone.{Milestone, MilestoneStatus, MilestoneVersion}
import com.togger.organization.Organization
import com.togger.project.Project
import com.togger.task._
import doobie.implicits._
import doobie.ConnectionIO

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.UUID

object CreateAccountApp extends IOApp with SeedAppModule {

  override def run(args: List[String]): IO[ExitCode] = {

    val logEntryStatusPool = List(
      LogEntryStatus.InProgress,
      LogEntryStatus.Blocked,
      LogEntryStatus.Completed,
      LogEntryStatus.Abandoned,
      LogEntryStatus.Paused,
      LogEntryStatus.Holiday,
      LogEntryStatus.Vacation,
      LogEntryStatus.Handover
    )

    val logEntrySpanStart = LocalDate.of(2026, 6, 1)
    val logEntrySpanEnd = LocalDate.of(2026, 9, 1)
    val logEntrySpanDays = ChronoUnit.DAYS.between(logEntrySpanStart, logEntrySpanEnd).toInt
    val totalSeedTasks = 20
    val logEntriesPerTask = 15

    def dateForDayOffset(dayOffset: Int): Instant =
      logEntrySpanStart.plusDays(dayOffset.toLong).atStartOfDay(ZoneOffset.UTC).toInstant

    // Spreads each task's log entries across the June 01 - September 01 span, starting at a
    // different day per task (unique per taskIndex) and wrapping around the span if it overruns.
    def entryDatesForTask(taskIndex: Int): List[Instant] = {
      val startDayOffset = (taskIndex * logEntrySpanDays) / totalSeedTasks
      (0 until logEntriesPerTask).toList.map(i => dateForDayOffset((startDayOffset + i) % (logEntrySpanDays + 1)))
    }

    // Deterministic per-task shuffle (seeded on taskIndex) so status sequences differ task to
    // task, while still allowing the same status to repeat for a couple of consecutive days.
    def statusesForTask(taskIndex: Int): List[LogEntryStatus] = {
      val random = new scala.util.Random(taskIndex * 104729L + 7L)
      val statuses = scala.collection.mutable.ListBuffer[LogEntryStatus]()
      while (statuses.size < logEntriesPerTask) {
        val status = logEntryStatusPool(random.nextInt(logEntryStatusPool.size))
        val repeatDays = 1 + random.nextInt(3)
        statuses ++= List.fill(math.min(repeatDays, logEntriesPerTask - statuses.size))(status)
      }
      statuses.toList
    }

    def createTaskWithLogEntries(
      projectId: UUID,
      milestoneId: UUID,
      taskStatusId: UUID,
      ownerId: UUID,
      taskName: String,
      taskIndex: Int
    ): IO[Unit] =
      for {
        taskResult <- taskService.createTask(
          Task(id = TgIds.UNASSIGNED_UUID, projectId = projectId, createdBy = ownerId, createdAt = now),
          TaskVersion(
            version = TgIds.UNASSIGNED_UUID,
            taskId = TgIds.UNASSIGNED_UUID,
            name = taskName,
            description = "",
            assignedTo = None,
            taskStatusId = taskStatusId,
            milestone = Some(milestoneId),
            createdAt = now,
            updatedAt = now
          ),
          Seq.empty
        )
        task = taskResult._1
        entryDates = entryDatesForTask(taskIndex)
        entryStatuses = statusesForTask(taskIndex)
        _ <- (0 until logEntriesPerTask).toList.traverse { i =>
          val entryDate = entryDates(i)
          val status = entryStatuses(i)
          logEntryService.createLogEntry(
            LogEntry(id = TgIds.UNASSIGNED_UUID, taskId = task.task.id, createdBy = ownerId, createdAt = entryDate),
            LogEntryVersion(
              version = TgIds.UNASSIGNED_UUID,
              logEntryId = TgIds.UNASSIGNED_UUID,
              textInternal = s"Log entry ${i + 1} for $taskName",
              textExternal = s"Log entry ${i + 1} for $taskName",
              status = status,
              createdBy = ownerId,
              createdAt = entryDate,
              updatedAt = entryDate
            )
          )
        }
      } yield ()

    def createMilestoneWithTasks(
      milestoneName: String,
      quarterLabel: String,
      projectId: UUID,
      taskStatusId: UUID,
      ownerId: UUID,
      taskIndexOffset: Int
    ): IO[Unit] =
      for {
        milestoneResult <- milestoneService.createMilestone(
          Milestone(id = TgIds.UNASSIGNED_UUID, projectId = projectId, createdAt = now),
          MilestoneVersion(
            version = TgIds.UNASSIGNED_UUID,
            milestoneId = TgIds.UNASSIGNED_UUID,
            name = milestoneName,
            description = "",
            status = MilestoneStatus.InProgress,
            startDate = LocalDate.now(),
            estimatedEndDate = LocalDate.now().plusMonths(1),
            createdAt = now,
            updatedAt = now
          )
        )
        milestone = milestoneResult.milestone
        _ <- (1 to 10).toList.traverse { i =>
          val taskNumber = f"$i%02d"
          createTaskWithLogEntries(
            projectId = projectId,
            milestoneId = milestone.id,
            taskStatusId = taskStatusId,
            ownerId = ownerId,
            taskName = s"Task - Alpha - $quarterLabel - $taskNumber",
            taskIndex = taskIndexOffset + (i - 1)
          )
        }
      } yield ()

    def createAccountTypes: ConnectionIO[List[AccountType]] =
      Seq("developer", "manager", "qa")
        .map(accountName =>
          accountTypeRepo.insert(
            AccountType(
              id = UUID.randomUUID(),
              name = accountName,
              createdAt = now,
              updatedAt = now
            )
          )
        )
        .toList
        .sequence

    for {
      accountTypes <- createAccountTypes.transact(xa)
      accountType = accountTypes.head
      accounts = Seq(
        createAccount("Dusan", "Manojlovic", "devone@email.com", accountType.id),
        createAccount("John", "Doe", "john@email.com", accountType.id),
        createAccount("John", "Smith", "shark@email.com", accountType.id),
        createAccount("Jack", "Curry", "bird@email.com", accountType.id),
        createAccount("Steve", "Murry", "bird1@email.com", accountType.id),
        createAccount("George", "Washington", "dog@email.com", accountType.id)
      )
      createdAccounts <- accounts.map(account => accountService.createAccount(account)).sequence
      owner = createdAccounts.head

      savedOrgResult <- organizationService.saveOrganization(
        Organization(
          id = TgIds.UNASSIGNED_UUID,
          name = "Maths",
          description = "",
          icon = "🏢",
          createdAt = now,
          updatedAt = now
        ),
        owner.id,
        Seq.empty
      )
      savedOrg = savedOrgResult._1

      savedAlphaResult <- projectService.saveProject(
        Project(
          id = TgIds.UNASSIGNED_UUID,
          organizationId = savedOrg.id,
          name = "Alpha",
          description = "",
          icon = "📁",
          createdAt = now,
          updatedAt = now
        ),
        owner.id,
        Seq.empty
      )
      savedAlpha = savedAlphaResult._1

      alphaTaskStatuses <- taskStatusRepo.findByProjectId(savedAlpha.id).transact(xa)
      alphaInProgressStatus = alphaTaskStatuses.find(_.name == DefaultTaskStatus.InProgress.statusName).get
      alphaDoneStatus = alphaTaskStatuses.find(_.name == DefaultTaskStatus.Done.statusName).get

      alphaTask1Result <- taskService.createTask(
        Task(id = TgIds.UNASSIGNED_UUID, projectId = savedAlpha.id, createdBy = owner.id, createdAt = now),
        TaskVersion(
          version = TgIds.UNASSIGNED_UUID,
          taskId = TgIds.UNASSIGNED_UUID,
          name = "Task 01 - Alpha",
          description = "",
          assignedTo = None,
          taskStatusId = alphaInProgressStatus.id,
          createdAt = now,
          updatedAt = now
        ),
        Seq(
          LinkWithOneVersion(
            Link(id = TgIds.UNASSIGNED_UUID, taskId = TgIds.UNASSIGNED_UUID, createdAt = now),
            LinkVersion(
              version = TgIds.UNASSIGNED_UUID,
              linkId = TgIds.UNASSIGNED_UUID,
              name = "Link - Alpha",
              url = "www.alpha.com",
              createdAt = now,
              updatedAt = now
            )
          )
        )
      )
      alphaTask1 = alphaTask1Result._1
      _ <- logEntryService.createLogEntry(
        LogEntry(id = TgIds.UNASSIGNED_UUID, taskId = alphaTask1.task.id, createdBy = owner.id, createdAt = now),
        LogEntryVersion(
          version = TgIds.UNASSIGNED_UUID,
          logEntryId = TgIds.UNASSIGNED_UUID,
          textInternal = "Log entry Alpha 01",
          textExternal = "Log entry Alpha 01",
          status = LogEntryStatus.InProgress,
          createdBy = owner.id,
          createdAt = now,
          updatedAt = now
        )
      )

      alphaTask2Result <- taskService.createTask(
        Task(id = TgIds.UNASSIGNED_UUID, projectId = savedAlpha.id, createdBy = owner.id, createdAt = now),
        TaskVersion(
          version = TgIds.UNASSIGNED_UUID,
          taskId = TgIds.UNASSIGNED_UUID,
          name = "Task02 - Alpha",
          description = "",
          assignedTo = None,
          taskStatusId = alphaDoneStatus.id,
          createdAt = now,
          updatedAt = now
        ),
        Seq.empty
      )
      alphaTask2 = alphaTask2Result._1
      _ <- logEntryService.createLogEntry(
        LogEntry(id = TgIds.UNASSIGNED_UUID, taskId = alphaTask2.task.id, createdBy = owner.id, createdAt = now),
        LogEntryVersion(
          version = TgIds.UNASSIGNED_UUID,
          logEntryId = TgIds.UNASSIGNED_UUID,
          textInternal = "Log entry Alpha 02",
          textExternal = "Log entry Alpha 02",
          status = LogEntryStatus.Completed,
          createdBy = owner.id,
          createdAt = now,
          updatedAt = now
        )
      )

      _ <- createMilestoneWithTasks("Milestone Q1", "Q1", savedAlpha.id, alphaInProgressStatus.id, owner.id, 0)
      _ <- createMilestoneWithTasks("Milestone Q2", "Q2", savedAlpha.id, alphaInProgressStatus.id, owner.id, 10)

      savedBetaResult <- projectService.saveProject(
        Project(
          id = TgIds.UNASSIGNED_UUID,
          organizationId = savedOrg.id,
          name = "Beta",
          description = "",
          icon = "📁",
          createdAt = now,
          updatedAt = now
        ),
        owner.id,
        Seq.empty
      )
      savedBeta = savedBetaResult._1

      betaTaskStatuses <- taskStatusRepo.findByProjectId(savedBeta.id).transact(xa)
      betaInProgressStatus = betaTaskStatuses.find(_.name == DefaultTaskStatus.InProgress.statusName).get
      betaDoneStatus = betaTaskStatuses.find(_.name == DefaultTaskStatus.Done.statusName).get
      betaWaitingStatus = betaTaskStatuses.find(_.name == DefaultTaskStatus.InReviewWaiting.statusName).get

      betaTask1Result <- taskService.createTask(
        Task(id = TgIds.UNASSIGNED_UUID, projectId = savedBeta.id, createdBy = owner.id, createdAt = now),
        TaskVersion(
          version = TgIds.UNASSIGNED_UUID,
          taskId = TgIds.UNASSIGNED_UUID,
          name = "Task 01 - Beta",
          description = "",
          assignedTo = None,
          taskStatusId = betaInProgressStatus.id,
          createdAt = now,
          updatedAt = now
        ),
        Seq(
          LinkWithOneVersion(
            Link(id = TgIds.UNASSIGNED_UUID, taskId = TgIds.UNASSIGNED_UUID, createdAt = now),
            LinkVersion(
              version = TgIds.UNASSIGNED_UUID,
              linkId = TgIds.UNASSIGNED_UUID,
              name = "Link - Beta",
              url = "www.beta.com",
              createdAt = now,
              updatedAt = now
            )
          )
        )
      )
      betaTask1 = betaTask1Result._1
      _ <- logEntryService.createLogEntry(
        LogEntry(id = TgIds.UNASSIGNED_UUID, taskId = betaTask1.task.id, createdBy = owner.id, createdAt = now),
        LogEntryVersion(
          version = TgIds.UNASSIGNED_UUID,
          logEntryId = TgIds.UNASSIGNED_UUID,
          textInternal = "Log entry Beta 01",
          textExternal = "Log entry Beta 01",
          status = LogEntryStatus.InProgress,
          createdBy = owner.id,
          createdAt = now,
          updatedAt = now
        )
      )

      betaTask2Result <- taskService.createTask(
        Task(id = TgIds.UNASSIGNED_UUID, projectId = savedBeta.id, createdBy = owner.id, createdAt = now),
        TaskVersion(
          version = TgIds.UNASSIGNED_UUID,
          taskId = TgIds.UNASSIGNED_UUID,
          name = "Task02 - Beta",
          description = "",
          assignedTo = None,
          taskStatusId = betaDoneStatus.id,
          createdAt = now,
          updatedAt = now
        ),
        Seq.empty
      )
      betaTask2 = betaTask2Result._1
      _ <- logEntryService.createLogEntry(
        LogEntry(id = TgIds.UNASSIGNED_UUID, taskId = betaTask2.task.id, createdBy = owner.id, createdAt = now),
        LogEntryVersion(
          version = TgIds.UNASSIGNED_UUID,
          logEntryId = TgIds.UNASSIGNED_UUID,
          textInternal = "Log entry Beta 02",
          textExternal = "Log entry Beta 02",
          status = LogEntryStatus.Completed,
          createdBy = owner.id,
          createdAt = now,
          updatedAt = now
        )
      )

      betaTask3Result <- taskService.createTask(
        Task(id = TgIds.UNASSIGNED_UUID, projectId = savedBeta.id, createdBy = owner.id, createdAt = now),
        TaskVersion(
          version = TgIds.UNASSIGNED_UUID,
          taskId = TgIds.UNASSIGNED_UUID,
          name = "Task03 - Beta",
          description = "",
          assignedTo = None,
          taskStatusId = betaWaitingStatus.id,
          createdAt = now,
          updatedAt = now
        ),
        Seq.empty
      )
      betaTask3 = betaTask3Result._1
      _ <- logEntryService.createLogEntry(
        LogEntry(id = TgIds.UNASSIGNED_UUID, taskId = betaTask3.task.id, createdBy = owner.id, createdAt = now),
        LogEntryVersion(
          version = TgIds.UNASSIGNED_UUID,
          logEntryId = TgIds.UNASSIGNED_UUID,
          textInternal = "Log entry Beta 03",
          textExternal = "Log entry Beta 03",
          status = LogEntryStatus.Paused,
          createdBy = owner.id,
          createdAt = now,
          updatedAt = now
        )
      )
    } yield ExitCode.Success

  }

}
