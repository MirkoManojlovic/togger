package com.togger.maintenance

import cats.effect.IO
import com.togger.Constants.DB_PASSWORD
import com.togger.Constants.DB_USERNAME
import com.togger.DbMode
import com.togger.account._
import com.togger.common.Password
import com.togger.common.TgClockImpl
import com.togger.common.TgIds
import com.togger.controllers.organization.OrganizationService
import com.togger.controllers.project.DefaultMilestoneStepTemplateService
import com.togger.controllers.project.DefaultMilestoneTemplateService
import com.togger.controllers.project.DefaultTaskLabelService
import com.togger.controllers.project.DefaultTaskStatusService
import com.togger.controllers.project.DefaultTaskTemplateService
import com.togger.controllers.project.ProjectService
import com.togger.deletedentity.DeletedEntityRepoImpl
import com.togger.logentry.LogEntryRepoImpl
import com.togger.logentry.LogEntryService
import com.togger.logentry.LogEntryVersionRepoImpl
import com.togger.login.SessionTokenFactoryImpl
import com.togger.milestone.MilestoneRepoImpl
import com.togger.milestone.MilestoneService
import com.togger.milestone.MilestoneStepRepoImpl
import com.togger.milestone.MilestoneStepVersionRepoImpl
import com.togger.milestone.MilestoneVersionRepoImpl
import com.togger.milestone.MilestoneWithStepsService
import com.togger.milestonesteptemplate.MilestoneStepTemplateRepoImpl
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersionRepoImpl
import com.togger.milestonetemplate.MilestoneTemplateRepoImpl
import com.togger.milestonetemplate.MilestoneTemplateVersionRepoImpl
import com.togger.organization.OrganizationAccountRepoImpl
import com.togger.organization.OrganizationRepoImpl
import com.togger.project.ProjectAccountRepoImpl
import com.togger.project.ProjectLabelAssignmentRepoImpl
import com.togger.project.ProjectRepoImpl
import com.togger.task._
import com.togger.taskStatus.TaskStatusRepoImpl
import com.togger.tasktemplate.TaskTemplateRepoImpl
import com.togger.tasktemplate.TaskTemplateVersionRepoImpl
import doobie.Transactor

import java.time.Instant
import java.util.UUID

trait SeedAppModule {

  protected val hashedPassword =
    HashedPasswordImpl.fromPassword(Password.fromString("A!good_1_password_is_good").toOption.get)
  protected val now: Instant = Instant.now

  protected val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
    driver = "org.postgresql.Driver",
    url = s"jdbc:postgresql:${DbMode.Development.dbName}",
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
  protected val projectLabelAssignmentRepo = new ProjectLabelAssignmentRepoImpl
  protected val taskStatusRepo = new TaskStatusRepoImpl
  protected val taskLabelRepo = new TaskLabelRepoImpl
  protected val taskLabelAssignmentRepo = new TaskLabelAssignmentRepoImpl
  protected val deletedEntityRepo = new DeletedEntityRepoImpl
  protected val taskRepo = new TaskRepoImpl
  protected val taskVersionRepo = new TaskVersionRepoImpl
  protected val linkRepo = new LinkRepoImpl
  protected val linkVersionRepo = new LinkVersionRepoImpl
  protected val logEntryRepo = new LogEntryRepoImpl
  protected val logEntryVersionRepo = new LogEntryVersionRepoImpl
  protected val milestoneRepo = new MilestoneRepoImpl
  protected val milestoneVersionRepo = new MilestoneVersionRepoImpl
  protected val defaultTaskStatusService = new DefaultTaskStatusService(tgClock)
  protected val defaultTaskLabelService = new DefaultTaskLabelService(tgClock)
  protected val taskTemplateRepo = new TaskTemplateRepoImpl
  protected val taskTemplateVersionRepo = new TaskTemplateVersionRepoImpl
  protected val defaultTaskTemplateService = new DefaultTaskTemplateService(tgClock)
  protected val milestoneStepTemplateRepo = new MilestoneStepTemplateRepoImpl
  protected val milestoneStepTemplateVersionRepo = new MilestoneStepTemplateVersionRepoImpl
  protected val defaultMilestoneStepTemplateService = new DefaultMilestoneStepTemplateService(tgClock)
  protected val milestoneTemplateVersionRepo = new MilestoneTemplateVersionRepoImpl
  protected val milestoneTemplateRepo = new MilestoneTemplateRepoImpl(milestoneTemplateVersionRepo)
  protected val defaultMilestoneTemplateService = new DefaultMilestoneTemplateService(tgClock)

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
      taskLabelRepo = taskLabelRepo,
      projectLabelAssignmentRepo = projectLabelAssignmentRepo,
      tgClock = tgClock,
      defaultTaskStatusService = defaultTaskStatusService,
      defaultTaskLabelService = defaultTaskLabelService,
      taskTemplateRepo = taskTemplateRepo,
      taskTemplateVersionRepo = taskTemplateVersionRepo,
      defaultTaskTemplateService = defaultTaskTemplateService,
      milestoneStepTemplateRepo = milestoneStepTemplateRepo,
      milestoneStepTemplateVersionRepo = milestoneStepTemplateVersionRepo,
      defaultMilestoneStepTemplateService = defaultMilestoneStepTemplateService,
      milestoneTemplateRepo = milestoneTemplateRepo,
      milestoneTemplateVersionRepo = milestoneTemplateVersionRepo,
      defaultMilestoneTemplateService = defaultMilestoneTemplateService,
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
      taskLabelAssignmentRepo = taskLabelAssignmentRepo,
      deletedEntityRepo = deletedEntityRepo,
      tgClock = tgClock,
      transactor = xa
    )

  protected val logEntryService =
    new LogEntryService(
      logEntryRepo = logEntryRepo,
      logEntryVersionRepo = logEntryVersionRepo,
      taskRepo = taskRepo,
      deletedEntityRepo = deletedEntityRepo,
      tgClock = tgClock,
      transactor = xa
    )

  protected val milestoneService =
    new MilestoneService(
      milestoneRepo = milestoneRepo,
      milestoneVersionRepo = milestoneVersionRepo,
      transactor = xa
    )

  /** Creates a milestone with its first steps; without steps it gets the default one. */
  protected val milestoneWithStepsService =
    new MilestoneWithStepsService(
      milestoneRepo = milestoneRepo,
      milestoneVersionRepo = milestoneVersionRepo,
      milestoneStepRepo = new MilestoneStepRepoImpl,
      milestoneStepVersionRepo = new MilestoneStepVersionRepoImpl,
      transactor = xa
    )

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
