package com.togger.maintenance

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.togger.account._
import com.togger.common.TgIds
import com.togger.logentry.{LogEntry, LogEntryStatus, LogEntryVersion}
import com.togger.organization.Organization
import com.togger.project.Project
import com.togger.task._
import doobie.implicits._
import doobie.ConnectionIO

import java.util.UUID

object CreateAccountApp extends IOApp with SeedAppModule {

  override def run(args: List[String]): IO[ExitCode] = {

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
          createdAt = now,
          updatedAt = now
        ),
        owner.id,
        Seq.empty
      )
      savedAlpha = savedAlphaResult._1

      alphaTaskStatuses <- taskStatusRepo.findByProjectId(savedAlpha.id).transact(xa)
      alphaInProgressStatus = alphaTaskStatuses.find(_.name == "🏗️ In progress").get
      alphaDoneStatus = alphaTaskStatuses.find(_.name == "✅ Done").get

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

      savedBetaResult <- projectService.saveProject(
        Project(
          id = TgIds.UNASSIGNED_UUID,
          organizationId = savedOrg.id,
          name = "Beta",
          description = "",
          createdAt = now,
          updatedAt = now
        ),
        owner.id,
        Seq.empty
      )
      savedBeta = savedBetaResult._1

      betaTaskStatuses <- taskStatusRepo.findByProjectId(savedBeta.id).transact(xa)
      betaInProgressStatus = betaTaskStatuses.find(_.name == "🏗️ In progress").get
      betaDoneStatus = betaTaskStatuses.find(_.name == "✅ Done").get
      betaWaitingStatus = betaTaskStatuses.find(_.name == "👀 In review/Waiting").get

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
