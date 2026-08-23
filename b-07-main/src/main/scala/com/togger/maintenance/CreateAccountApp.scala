package com.togger.maintenance

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.togger.Constants.{DB_NAME, DB_PASSWORD, DB_USERNAME}
import com.togger.account._
import com.togger.common.{Password, TgClockImpl, TgIds}
import com.togger.controllers.organization.OrganizationService
import com.togger.controllers.project.{DefaultTaskStatusService, ProjectService}
import com.togger.logentry.{LogEntry, LogEntryRepoImpl, LogEntryService, LogEntryStatus, LogEntryVersion, LogEntryVersionRepoImpl}
import com.togger.login.SessionTokenFactoryImpl
import com.togger.organization.{Organization, OrganizationAccountRepoImpl, OrganizationRepoImpl}
import com.togger.project.{Project, ProjectAccountRepoImpl, ProjectRepoImpl}
import com.togger.task._
import com.togger.taskStatus.TaskStatusRepoImpl
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.time._
import java.util.UUID

object CreateAccountApp extends IOApp {

  private val hashedPassword =
    HashedPasswordImpl.fromPassword(Password.fromString("A!good_1_password_is_good").toOption.get)
  private val now = Instant.now

  override def run(args: List[String]): IO[ExitCode] = {

    val xa = Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = s"jdbc:postgresql:$DB_NAME",
      user = DB_USERNAME,
      password = DB_PASSWORD,
      logHandler = None
    )

    val accountTypeRepo = new AccountTypeRepo()
    val accountRepo = new AccountRepoImpl()
    val accountSessionRepo = new AccountSessionRepo()
    val sessionTokenFactory = new SessionTokenFactoryImpl
    val tgClock = new TgClockImpl

    val accountService =
      new AccountService(
        accountRepo = accountRepo,
        accountSessionRepo = accountSessionRepo,
        sessionTokenFactory = sessionTokenFactory,
        tgClock = tgClock,
        transactor = xa
      )

    val organizationRepo = new OrganizationRepoImpl
    val organizationAccountRepo = new OrganizationAccountRepoImpl
    val projectRepo = new ProjectRepoImpl
    val projectAccountRepo = new ProjectAccountRepoImpl
    val taskStatusRepo = new TaskStatusRepoImpl
    val taskRepo = new TaskRepoImpl
    val taskVersionRepo = new TaskVersionRepoImpl
    val linkRepo = new LinkRepoImpl
    val linkVersionRepo = new LinkVersionRepoImpl
    val logEntryRepo = new LogEntryRepoImpl
    val logEntryVersionRepo = new LogEntryVersionRepoImpl
    val defaultTaskStatusService = new DefaultTaskStatusService(tgClock)

    val organizationService =
      new OrganizationService(
        organizationRepo = organizationRepo,
        organizationAccountRepo = organizationAccountRepo,
        projectRepo = projectRepo,
        tgClock = tgClock,
        transactor = xa
      )

    val projectService =
      new ProjectService(
        projectRepo = projectRepo,
        projectAccountRepo = projectAccountRepo,
        taskStatusRepo = taskStatusRepo,
        tgClock = tgClock,
        defaultTaskStatusService = defaultTaskStatusService,
        transactor = xa
      )

    val taskService =
      new TaskService(
        taskRepo = taskRepo,
        taskVersionRepo = taskVersionRepo,
        linkRepo = linkRepo,
        linkVersionRepo = linkVersionRepo,
        logEntryRepo = logEntryRepo,
        logEntryVersionRepo = logEntryVersionRepo,
        transactor = xa
      )

    val logEntryService = new LogEntryService(logEntryRepo = logEntryRepo, logEntryVersionRepo = logEntryVersionRepo, transactor = xa)

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
          name = "The Daily Bugle",
          description = "",
          createdAt = now,
          updatedAt = now
        ),
        owner.id,
        Seq.empty
      )
      savedOrg = savedOrgResult._1

      savedProjectResult <- projectService.saveProject(
        Project(
          id = TgIds.UNASSIGNED_UUID,
          organizationId = savedOrg.id,
          name = "Spiderman",
          description = "",
          createdAt = now,
          updatedAt = now
        ),
        owner.id,
        Seq.empty
      )
      savedProject = savedProjectResult._1

      taskStatuses <- taskStatusRepo.findByProjectId(savedProject.id).transact(xa)
      inProgressStatus = taskStatuses.find(_.name == "🏗️ In progress").get
      doneStatus = taskStatuses.find(_.name == "✅ Done").get

      task1Result <- taskService.createTask(
        Task(id = TgIds.UNASSIGNED_UUID, projectId = savedProject.id, createdBy = owner.id, createdAt = now),
        TaskVersion(
          version = TgIds.UNASSIGNED_UUID,
          taskId = TgIds.UNASSIGNED_UUID,
          name = "Photographs of Spiderman",
          description = "",
          assignedTo = None,
          taskStatusId = inProgressStatus.id,
          createdAt = now,
          updatedAt = now
        ),
        Seq(
          LinkWithOneVersion(
            Link(id = TgIds.UNASSIGNED_UUID, taskId = TgIds.UNASSIGNED_UUID, createdAt = now),
            LinkVersion(
              version = TgIds.UNASSIGNED_UUID,
              linkId = TgIds.UNASSIGNED_UUID,
              name = "Link to pics",
              url = "www.spiderman.com",
              createdAt = now,
              updatedAt = now
            )
          )
        )
      )
      task1 = task1Result._1
      _ <- logEntryService.createLogEntry(
        LogEntry(id = TgIds.UNASSIGNED_UUID, taskId = task1.task.id, createdBy = owner.id, createdAt = now),
        LogEntryVersion(
          version = TgIds.UNASSIGNED_UUID,
          logEntryId = TgIds.UNASSIGNED_UUID,
          textInternal = "Working on it",
          textExternal = "Working on it",
          status = LogEntryStatus.InProgress,
          createdBy = owner.id,
          createdAt = now,
          updatedAt = now
        )
      )

      task2Result <- taskService.createTask(
        Task(id = TgIds.UNASSIGNED_UUID, projectId = savedProject.id, createdBy = owner.id, createdAt = now),
        TaskVersion(
          version = TgIds.UNASSIGNED_UUID,
          taskId = TgIds.UNASSIGNED_UUID,
          name = "Who is Spiderman?",
          description = "",
          assignedTo = None,
          taskStatusId = doneStatus.id,
          createdAt = now,
          updatedAt = now
        ),
        Seq.empty
      )
      task2 = task2Result._1
      _ <- logEntryService.createLogEntry(
        LogEntry(id = TgIds.UNASSIGNED_UUID, taskId = task2.task.id, createdBy = owner.id, createdAt = now),
        LogEntryVersion(
          version = TgIds.UNASSIGNED_UUID,
          logEntryId = TgIds.UNASSIGNED_UUID,
          textInternal = "Don't know",
          textExternal = "Don't know",
          status = LogEntryStatus.Completed,
          createdBy = owner.id,
          createdAt = now,
          updatedAt = now
        )
      )
    } yield ExitCode.Success

  }

  def createAccount(firstName: String, lastName: String, email: String, accountTypeId: UUID) =
    Account(
      id = TgIds.UNASSIGNED_UUID,
      firstName = firstName,
      lastName = lastName,
      email = email,
      password = hashedPassword,
      accountTypeId = accountTypeId,
      verified = true,
      enabled = true,
      createdAt = now,
      updatedAt = now
    )

}
