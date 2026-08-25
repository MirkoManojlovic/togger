package com.togger.maintenance

import cats.effect.IO
import com.togger.Constants.{DB_NAME, DB_PASSWORD, DB_USERNAME}
import com.togger.account._
import com.togger.common.{Password, TgClockImpl, TgIds}
import com.togger.controllers.organization.OrganizationService
import com.togger.controllers.project.{DefaultTaskStatusService, ProjectService}
import com.togger.logentry.{LogEntryRepoImpl, LogEntryService, LogEntryVersionRepoImpl}
import com.togger.login.SessionTokenFactoryImpl
import com.togger.organization.{OrganizationAccountRepoImpl, OrganizationRepoImpl}
import com.togger.project.{ProjectAccountRepoImpl, ProjectRepoImpl}
import com.togger.task._
import com.togger.taskStatus.TaskStatusRepoImpl
import doobie.Transactor

import java.time.Instant
import java.util.UUID

trait SeedAppModule {

  protected val hashedPassword =
    HashedPasswordImpl.fromPassword(Password.fromString("A!good_1_password_is_good").toOption.get)
  protected val now: Instant = Instant.now

  protected val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
    driver = "org.postgresql.Driver",
    url = s"jdbc:postgresql:$DB_NAME",
    user = DB_USERNAME,
    password = DB_PASSWORD,
    logHandler = None
  )

  protected val accountTypeRepo = new AccountTypeRepo()
  protected val accountRepo = new AccountRepoImpl()
  protected val accountSessionRepo = new AccountSessionRepo()
  protected val sessionTokenFactory = new SessionTokenFactoryImpl
  protected val tgClock = new TgClockImpl

  protected val accountService =
    new AccountService(
      accountRepo = accountRepo,
      accountSessionRepo = accountSessionRepo,
      sessionTokenFactory = sessionTokenFactory,
      tgClock = tgClock,
      transactor = xa
    )

  protected val organizationRepo = new OrganizationRepoImpl
  protected val organizationAccountRepo = new OrganizationAccountRepoImpl
  protected val projectRepo = new ProjectRepoImpl
  protected val projectAccountRepo = new ProjectAccountRepoImpl
  protected val taskStatusRepo = new TaskStatusRepoImpl
  protected val taskRepo = new TaskRepoImpl
  protected val taskVersionRepo = new TaskVersionRepoImpl
  protected val linkRepo = new LinkRepoImpl
  protected val linkVersionRepo = new LinkVersionRepoImpl
  protected val logEntryRepo = new LogEntryRepoImpl
  protected val logEntryVersionRepo = new LogEntryVersionRepoImpl
  protected val defaultTaskStatusService = new DefaultTaskStatusService(tgClock)

  protected val organizationService =
    new OrganizationService(
      organizationRepo = organizationRepo,
      organizationAccountRepo = organizationAccountRepo,
      projectRepo = projectRepo,
      tgClock = tgClock,
      transactor = xa
    )

  protected val projectService =
    new ProjectService(
      projectRepo = projectRepo,
      projectAccountRepo = projectAccountRepo,
      taskStatusRepo = taskStatusRepo,
      tgClock = tgClock,
      defaultTaskStatusService = defaultTaskStatusService,
      transactor = xa
    )

  protected val taskService =
    new TaskService(
      taskRepo = taskRepo,
      taskVersionRepo = taskVersionRepo,
      linkRepo = linkRepo,
      linkVersionRepo = linkVersionRepo,
      logEntryRepo = logEntryRepo,
      logEntryVersionRepo = logEntryVersionRepo,
      transactor = xa
    )

  protected val logEntryService =
    new LogEntryService(logEntryRepo = logEntryRepo, logEntryVersionRepo = logEntryVersionRepo, transactor = xa)

  protected def createAccount(firstName: String, lastName: String, email: String, accountTypeId: UUID): Account =
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
