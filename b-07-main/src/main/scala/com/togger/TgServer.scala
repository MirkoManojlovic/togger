package com.togger
import cats.effect.IO
import cats.effect.Resource
import com.togger.account.AccountEndpoints
import com.togger.account.AccountManagementRepoImpl
import com.togger.account.AccountManagementService
import com.togger.account.AccountNameLookup
import com.togger.account.AccountServiceCore
import com.togger.account.AccountTypeRepo
import com.togger.accountmapping.AccountMappingEndpoints
import com.togger.accountmapping.AccountMappingRepoImpl
import com.togger.accountmapping.AccountMappingService
import com.togger.ai.TgAiClient
import com.togger.ai.TgAiModelEndpoints
import com.togger.ai.TgAiService
import com.togger.ai.TgOllama
import com.togger.ai.TgOpenAiClient
import com.togger.ai.TgStaticApiKeyProvider
import com.togger.auth.BaseEndpoint
import com.togger.auth.SecuredEndpoint
import com.togger.common.DateRangeValidation
import com.togger.contribution.ContributionEndpoints
import com.togger.contribution.ContributionRepoImpl
import com.togger.contribution.ContributionService
import com.togger.controllers.account.AccountController
import com.togger.controllers.account.AccountManagementController
import com.togger.controllers.contribution.ContributionController
import com.togger.controllers.logentry.LogEntryController
import com.togger.controllers.milestone.MilestoneAiSummaryController
import com.togger.controllers.milestone.MilestoneAiSummaryStreamEndpoints
import com.togger.controllers.milestone.MilestoneController
import com.togger.controllers.milestone.MilestoneDateController
import com.togger.controllers.milestone.MilestoneStepController
import com.togger.controllers.milestone.MilestoneStepTemplateController
import com.togger.controllers.milestone.MilestoneSummaryController
import com.togger.controllers.milestone.MilestoneTemplateController
import com.togger.controllers.ai.TgAiModelController
import com.togger.controllers.tgaiaction.TgAiActionController
import com.togger.controllers.tgprompttemplate.TgPromptTemplateController
import com.togger.tgaiaction.TgAiActionDefaultRepoImpl
import com.togger.tgaiaction.TgAiActionEndpointRepoImpl
import com.togger.tgaiaction.TgAiActionEndpoints
import com.togger.tgaiaction.TgAiActionRepoImpl
import com.togger.tgaiaction.TgAiActionService
import com.togger.controllers.timebox.TimeboxController
import com.togger.controllers.organization.OrganizationController
import com.togger.controllers.organization.OrganizationService
import com.togger.controllers.project.DefaultMilestoneStepTemplateService
import com.togger.controllers.project.DefaultMilestoneTemplateService
import com.togger.controllers.project.DefaultTaskLabelService
import com.togger.controllers.project.DefaultTaskTemplateService
import com.togger.controllers.project.DefaultTaskStatusService
import com.togger.controllers.project.ProjectController
import com.togger.controllers.project.ProjectService
import com.togger.controllers.project.DataExportController
import com.togger.controllers.accountmapping.AccountMappingController
import com.togger.controllers.ai.TgAiModelController
import com.togger.controllers.contribution.ContributionController
import com.togger.controllers.dataexportconfig.DataExportConfigController
import com.togger.controllers.dataimportconfig.DataImportConfigController
import com.togger.controllers.logentry.LogEntryController
import com.togger.controllers.milestone.MilestoneAiSummaryController
import com.togger.controllers.milestone.MilestoneAiSummaryStreamEndpoints
import com.togger.controllers.milestone.MilestoneController
import com.togger.controllers.milestone.MilestoneDateController
import com.togger.controllers.milestone.MilestoneSummaryController
import com.togger.controllers.organization.OrganizationController
import com.togger.controllers.organization.OrganizationService
import com.togger.controllers.project.DataExportController
import com.togger.controllers.project.DefaultTaskLabelService
import com.togger.controllers.project.DefaultTaskStatusService
import com.togger.controllers.project.DefaultTaskTemplateService
import com.togger.controllers.project.ProjectController
import com.togger.controllers.project.ProjectService
import com.togger.controllers.projectlabel.ProjectLabelCategoryController
import com.togger.controllers.projectlabel.ProjectLabelController
import com.togger.controllers.projectmapping.ProjectMappingController
import com.togger.controllers.task.LinkController
import com.togger.controllers.task.TaskController
import com.togger.controllers.task.TaskLabelController
import com.togger.controllers.task.TaskStatusController
import com.togger.controllers.task.TaskTemplateController
import com.togger.controllers.tgaiaction.TgAiActionController
import com.togger.controllers.tgprompttemplate.TgPromptTemplateController
import com.togger.controllers.timebox.TimeboxController
import com.togger.dataexportconfig.DataExportConfigEndpoints
import com.togger.dataexportconfig.DataExportConfigRepoImpl
import com.togger.dataexportconfig.DataExportConfigService
import com.togger.dataimportconfig.DataImportConfigEndpoints
import com.togger.dataimportconfig.DataImportConfigRepoImpl
import com.togger.dataimportconfig.DataImportConfigService
import com.togger.dep.AuthDependenciesModule
import com.togger.dump._
import com.togger.http.HttpModule
import com.togger.logentry.LogEntryEndpoints
import com.togger.logentry.LogEntryRepoImpl
import com.togger.logentry.LogEntryService
import com.togger.logentry.LogEntryVersionRepoImpl
import com.togger.milestone._
import com.togger.organization.OrganizationAccountRepoImpl
import com.togger.organization.OrganizationEndpoints
import com.togger.organization.OrganizationRepoImpl
import com.togger.project.ProjectAccountRepoImpl
import com.togger.project.ProjectEndpoints
import com.togger.project.ProjectLabelAssignmentRepoImpl
import com.togger.project.ProjectRepoImpl
import com.togger.projectlabel.ProjectLabelCategoryEndpoints
import com.togger.projectlabel.ProjectLabelCategoryRepoImpl
import com.togger.projectlabel.ProjectLabelCategoryService
import com.togger.projectlabel.ProjectLabelEndpoints
import com.togger.projectlabel.ProjectLabelRepoImpl
import com.togger.projectlabel.ProjectLabelService
import com.togger.projectmapping.ProjectMappingEndpoints
import com.togger.projectmapping.ProjectMappingRepoImpl
import com.togger.projectmapping.ProjectMappingService
import com.togger.task._
import com.togger.taskStatus.TaskStatusRepoImpl
import com.togger.tasktemplate.TaskTemplateRepoImpl
import com.togger.tasktemplate.TaskTemplateService
import com.togger.tasktemplate.TaskTemplateVersionRepoImpl
import com.togger.tgaiaction.TgAiActionDefaultRepoImpl
import com.togger.tgaiaction.TgAiActionEndpointRepoImpl
import com.togger.tgaiaction.TgAiActionEndpoints
import com.togger.tgaiaction.TgAiActionRepoImpl
import com.togger.tgaiaction.TgAiActionService
import com.togger.tgprompttemplate.TgPromptTemplateEndpoints
import com.togger.tgprompttemplate.TgPromptTemplateRepoImpl
import com.togger.tgprompttemplate.TgPromptTemplateService
import com.togger.tgprompttemplate.TgPromptTemplateVersionRepoImpl
import com.togger.timebox._
import com.togger.milestonesteptemplate.MilestoneStepTemplateRepoImpl
import com.togger.milestonesteptemplate.MilestoneStepTemplateService
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersionRepoImpl
import com.togger.milestonetemplate.MilestoneTemplateRepoImpl
import com.togger.milestonetemplate.MilestoneTemplateService
import com.togger.milestonetemplate.MilestoneTemplateVersionRepoImpl
import com.togger.dataexportconfig.DataExportConfigEndpoints
import com.togger.dataexportconfig.DataExportConfigRepoImpl
import com.togger.dataexportconfig.DataExportConfigService
import com.togger.dataimportconfig.DataImportConfigEndpoints
import com.togger.dataimportconfig.DataImportConfigRepoImpl
import com.togger.dataimportconfig.DataImportConfigService
import com.togger.projectlabel.ProjectLabelCategoryEndpoints
import com.togger.projectlabel.ProjectLabelCategoryRepoImpl
import com.togger.projectlabel.ProjectLabelCategoryService
import com.togger.projectlabel.ProjectLabelEndpoints
import com.togger.projectlabel.ProjectLabelRepoImpl
import com.togger.projectlabel.ProjectLabelService
import doobie.Transactor
import org.http4s.server.Server
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamBackend
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

trait TransactorModule {
  val transactor: Transactor[IO]
}

class TransactorModuleApp(val transactor: Transactor[IO]) extends TransactorModule {}

object TgServer {

  def instance(transactorModule: TransactorModule): Resource[IO, Server] =
    HttpClientFs2Backend.resource[IO]().flatMap(aiBackend => instance(transactorModule, aiBackend))

  private def instance(
    transactorModule: TransactorModule,
    aiBackend: StreamBackend[IO, Fs2Streams[IO]]
  ): Resource[IO, Server] = {

    lazy val authDependenciesModule = new AuthDependenciesModule(transactorModule.transactor)
    import authDependenciesModule._

    lazy val dependenciesModule =
      new DependenciesModule(
        transactorModule.transactor,
        securedEndpoint,
        authDependenciesModule,
        aiBackend
      )
    import dependenciesModule._

    lazy val httpModule = new HttpModule()
    Resource
      .eval(new ProjectLabelSeedService(transactorModule.transactor).run)
      .evalMap(_ => new AiEndpointSeedService(transactorModule.transactor).run)
      .evalMap(_ => new McpAccountSeedService(transactorModule.transactor).run)
      .flatMap(_ =>
        httpModule.createServer(
          List(
            loginController.login,
            loginController.expireOldSessions,
            loginController.logoutEveryone,
            loginController.changePassword,
            loginController.updateAccountInfo,
            loginController.exampleAuth,
            taskController.findById,
            taskController.findByProjectId,
            taskController.save,
            taskController.saveBulk,
            taskController.update,
            taskController.delete,
            taskController.listVersions,
            taskController.updateVersion,
            taskController.deleteVersion,
            linkController.listVersions,
            linkController.updateVersion,
            linkController.deleteVersion,
            projectController.findByid,
            projectController.update,
            projectController.delete,
            projectController.save,
            projectController.findByOrganizationIdAndAccountId,
            projectController.findAllMembers,
            projectController.searchByLabels,
            accountController.searchByFirstNameLastNameEmail,
            accountManagementController.findAll,
            accountManagementController.findAccountTypes,
            accountManagementController.findById,
            accountManagementController.update,
            accountManagementController.delete,
            contributionController.findByProjectId,
            logEntryController.findByTaskId,
            logEntryController.findByProjectId,
            logEntryController.save,
            logEntryController.updateLogEntry,
            logEntryController.listVersions,
            logEntryController.updateVersion,
            logEntryController.deleteVersion,
            timeboxController.findById,
            timeboxController.findByProjectId,
            timeboxController.save,
            timeboxController.saveNewVersion,
            timeboxController.listVersions,
            timeboxController.delete,
            milestoneController.findById,
            milestoneController.findByProjectId,
            milestoneController.save,
            milestoneController.update,
            milestoneController.delete,
            milestoneController.listVersions,
            milestoneController.updateVersion,
            milestoneController.deleteVersion,
            milestoneStepController.findById,
            milestoneStepController.findByMilestoneId,
            milestoneStepController.findByProjectId,
            milestoneStepController.save,
            milestoneStepController.update,
            milestoneStepController.reorder,
            milestoneStepController.delete,
            milestoneStepController.listVersions,
            milestoneStepTemplateController.save,
            milestoneStepTemplateController.findById,
            milestoneStepTemplateController.update,
            milestoneStepTemplateController.findByProjectId,
            milestoneStepTemplateController.listVersions,
            milestoneStepTemplateController.delete,
            milestoneTemplateController.save,
            milestoneTemplateController.findById,
            milestoneTemplateController.update,
            milestoneTemplateController.findByProjectId,
            milestoneTemplateController.listVersions,
            milestoneTemplateController.delete,
            milestoneDateController.findById,
            milestoneDateController.findByMilestoneId,
            milestoneDateController.findByProjectId,
            milestoneDateController.save,
            milestoneDateController.update,
            milestoneDateController.delete,
            milestoneDateController.listVersions,
            milestoneDateController.updateVersion,
            milestoneDateController.deleteVersion,
            milestoneSummaryController.findById,
            milestoneSummaryController.findByMilestoneId,
            milestoneSummaryController.findByProjectId,
            milestoneSummaryController.save,
            milestoneSummaryController.update,
            milestoneSummaryController.delete,
            milestoneSummaryController.listVersions,
            milestoneSummaryController.updateVersion,
            milestoneSummaryController.deleteVersion,
            milestoneAiSummaryController.summarise,
            organizationController.findAll,
            organizationController.findByAccountId,
            organizationController.save,
            organizationController.update,
            organizationController.delete,
            organizationController.findById,
            organizationController.findAllMembers,
            taskStatusController.findByProjectId,
            taskStatusController.findById,
            taskStatusController.save,
            taskStatusController.update,
            taskStatusController.delete,
            taskLabelController.findByProjectId,
            taskLabelController.findById,
            taskLabelController.save,
            taskLabelController.update,
            taskLabelController.delete,
            taskTemplateController.save,
            taskTemplateController.findById,
            taskTemplateController.update,
            taskTemplateController.findByProjectId,
            taskTemplateController.findByLabel,
            taskTemplateController.listVersions,
            taskTemplateController.delete,
            tgPromptTemplateController.save,
            tgPromptTemplateController.newVersion,
            tgPromptTemplateController.findAll,
            tgPromptTemplateController.findById,
            tgPromptTemplateController.listVersions,
            tgPromptTemplateController.deleteVersion,
            tgPromptTemplateController.delete,
            tgAiActionController.save,
            tgAiActionController.update,
            tgAiActionController.findAll,
            tgAiActionController.listEndpoints,
            tgAiActionController.findDefaults,
            tgAiActionController.setDefault,
            tgAiActionController.clearDefault,
            tgAiActionController.findById,
            tgAiActionController.delete,
            tgAiModelController.list,
            dataExportController.exportTasks,
            dataExportController.checkStatus,
            dataExportController.exportByConfig,
            dataExportController.checkStatusByConfig,
            dataExportController.importData,
            dataExportController.importNewProject,
            dataExportController.checkImportStatus,
            dataExportController.exportPending,
            dataExportController.importPending,
            dataExportConfigController.findById,
            dataExportConfigController.save,
            dataExportConfigController.update,
            dataExportConfigController.delete,
            dataExportConfigController.findByProjectId,
            dataImportConfigController.findById,
            dataImportConfigController.save,
            dataImportConfigController.update,
            dataImportConfigController.delete,
            dataImportConfigController.findByProjectId,
            projectMappingController.save,
            projectMappingController.findByProjectIdDb,
            projectMappingController.findByProjectIdDump,
            projectMappingController.delete,
            accountMappingController.save,
            accountMappingController.findByAccountIdDb,
            accountMappingController.findByAccountIdDump,
            accountMappingController.delete,
            projectLabelController.findAll,
            projectLabelController.findById,
            projectLabelController.save,
            projectLabelController.update,
            projectLabelController.delete,
            projectLabelCategoryController.findAll,
            projectLabelCategoryController.findById,
            projectLabelCategoryController.save,
            projectLabelCategoryController.update,
            projectLabelCategoryController.delete
          )
        )
      )
  }
}

class DependenciesModule(
  val transactor: Transactor[IO],
  securedEndpoint: SecuredEndpoint,
  authDependenciesModule: AuthDependenciesModule,
  aiBackend: StreamBackend[IO, Fs2Streams[IO]]
) {
  import authDependenciesModule._
  lazy val logEntryRepo = new LogEntryRepoImpl
  lazy val logEntryVersionRepo = new LogEntryVersionRepoImpl
  lazy val logEntryService = new LogEntryService(logEntryRepo, logEntryVersionRepo, transactor)
  lazy val logEntryEndpoints = new LogEntryEndpoints(securedEndpoint)
  lazy val accountNameLookup = new AccountNameLookup(accountRepo, transactor)
  lazy val logEntryController = new LogEntryController(logEntryEndpoints, logEntryService, accountNameLookup)

  lazy val contributionRepo = new ContributionRepoImpl
  lazy val contributionService =
    new ContributionService(contributionRepo, projectRepo, projectAccountRepo, transactor)
  lazy val contributionEndpoints = new ContributionEndpoints(securedEndpoint)
  lazy val contributionController = new ContributionController(contributionEndpoints, contributionService)

  lazy val milestoneRepo = new MilestoneRepoImpl
  lazy val milestoneVersionRepo = new MilestoneVersionRepoImpl
  lazy val milestoneService = new MilestoneService(milestoneRepo, milestoneVersionRepo, transactor)
  lazy val milestoneEndpoints = new MilestoneEndpoints(securedEndpoint)
  lazy val dateRangeValidation = new DateRangeValidation
  lazy val milestoneController =
    new MilestoneController(milestoneEndpoints, milestoneService, dateRangeValidation, projectService, milestoneStepService, milestoneWithStepsService)

  lazy val milestoneStepRepo = new MilestoneStepRepoImpl
  lazy val milestoneStepVersionRepo = new MilestoneStepVersionRepoImpl
  lazy val milestoneStepService = new MilestoneStepService(milestoneStepRepo, milestoneStepVersionRepo, transactor)
  lazy val milestoneStepEndpoints = new MilestoneStepEndpoints(securedEndpoint)
  lazy val milestoneStepController = new MilestoneStepController(
    milestoneStepEndpoints,
    milestoneStepService,
    milestoneService,
    dateRangeValidation,
    projectService,
    tgClock
  )

  lazy val milestoneStepTemplateRepo = new MilestoneStepTemplateRepoImpl
  lazy val milestoneStepTemplateVersionRepo = new MilestoneStepTemplateVersionRepoImpl
  lazy val milestoneStepTemplateService = new MilestoneStepTemplateService(
    milestoneStepTemplateRepo,
    milestoneStepTemplateVersionRepo,
    milestoneTemplateRepo,
    transactor
  )
  lazy val milestoneStepTemplateEndpoints = new MilestoneStepTemplateEndpoints(securedEndpoint)
  lazy val milestoneStepTemplateController =
    new MilestoneStepTemplateController(milestoneStepTemplateService, milestoneStepTemplateEndpoints, tgClock)

  lazy val milestoneTemplateVersionRepo = new MilestoneTemplateVersionRepoImpl
  lazy val milestoneTemplateRepo = new MilestoneTemplateRepoImpl(milestoneTemplateVersionRepo)
  lazy val milestoneTemplateService = new MilestoneTemplateService(
    milestoneTemplateRepo,
    milestoneTemplateVersionRepo,
    milestoneStepTemplateRepo,
    transactor
  )
  lazy val milestoneTemplateEndpoints = new MilestoneTemplateEndpoints(securedEndpoint)
  lazy val milestoneTemplateController =
    new MilestoneTemplateController(milestoneTemplateService, milestoneTemplateEndpoints, tgClock)

  lazy val milestoneWithStepsService = new MilestoneWithStepsService(
    milestoneRepo,
    milestoneVersionRepo,
    milestoneStepRepo,
    milestoneStepVersionRepo,
    transactor
  )

  lazy val milestoneDateRepo = new MilestoneDateRepoImpl
  lazy val milestoneDateVersionRepo = new MilestoneDateVersionRepoImpl
  lazy val milestoneDateService = new MilestoneDateService(milestoneDateRepo, milestoneDateVersionRepo, transactor)
  lazy val milestoneDateEndpoints = new MilestoneDateEndpoints(securedEndpoint)
  lazy val milestoneDateController =
    new MilestoneDateController(milestoneDateEndpoints, milestoneDateService, milestoneService, dateRangeValidation)
  lazy val milestoneSummaryRepo = new MilestoneSummaryRepoImpl
  lazy val milestoneSummaryVersionRepo = new MilestoneSummaryVersionRepoImpl
  lazy val milestoneSummaryService =
    new MilestoneSummaryService(milestoneSummaryRepo, milestoneSummaryVersionRepo, transactor)
  lazy val milestoneSummaryEndpoints = new MilestoneSummaryEndpoints(securedEndpoint)
  lazy val milestoneSummaryController = new MilestoneSummaryController(
    milestoneSummaryEndpoints,
    milestoneSummaryService,
    milestoneService,
    dateRangeValidation,
    contributionService
  )

  lazy val taskRepo = new TaskRepoImpl
  lazy val taskVersionRepo = new TaskVersionRepoImpl
  lazy val taskVersionService = new TaskVersionService(taskVersionRepo, transactor)
  lazy val linkRepo = new LinkRepoImpl
  lazy val linkVersionRepo = new LinkVersionRepoImpl
  lazy val linkService = new LinkService(linkVersionRepo, transactor)
  lazy val linkEndpoints = new LinkEndpoints(securedEndpoint)
  lazy val linkController = new LinkController(linkEndpoints, linkService)
  lazy val taskService =
    new TaskService(
      taskRepo,
      taskVersionRepo,
      linkRepo,
      linkVersionRepo,
      logEntryRepo,
      logEntryVersionRepo,
      taskLabelAssignmentRepo,
      transactor
    )
  lazy val taskEndpoints = new TaskEndpoints(securedEndpoint)
  lazy val taskController =
    new TaskController(
      taskService,
      taskVersionService,
      taskEndpoints,
      tgClock,
      dateRangeValidation,
      projectService,
      taskStatusService,
      accountNameLookup,
      milestoneStepService
    )

  lazy val timeboxRepo = new TimeboxRepoImpl
  lazy val timeboxVersionRepo = new TimeboxVersionRepoImpl
  lazy val timeboxService = new TimeboxService(timeboxRepo, timeboxVersionRepo, taskRepo, taskVersionRepo, transactor)
  lazy val timeboxEndpoints = new TimeboxEndpoints(securedEndpoint)
  lazy val timeboxController = new TimeboxController(timeboxEndpoints, timeboxService, dateRangeValidation, tgClock, milestoneStepService)

  lazy val projectEndpoints = new ProjectEndpoints(securedEndpoint)
  lazy val projectRepo = new ProjectRepoImpl
  lazy val projectAccountRepo = new ProjectAccountRepoImpl
  lazy val projectLabelAssignmentRepo = new ProjectLabelAssignmentRepoImpl
  lazy val defaultTaskStatusService = new DefaultTaskStatusService(tgClock)
  lazy val defaultTaskLabelService = new DefaultTaskLabelService(tgClock)
  lazy val defaultTaskTemplateService = new DefaultTaskTemplateService(tgClock)
  lazy val defaultMilestoneStepTemplateService = new DefaultMilestoneStepTemplateService(tgClock)
  lazy val defaultMilestoneTemplateService = new DefaultMilestoneTemplateService(tgClock)
  lazy val projectService =
    new ProjectService(
      projectRepo,
      projectAccountRepo,
      taskStatusRepo,
      taskLabelRepo,
      projectLabelAssignmentRepo,
      tgClock,
      defaultTaskStatusService,
      defaultTaskLabelService,
      taskTemplateRepo,
      taskTemplateVersionRepo,
      defaultTaskTemplateService,
      milestoneStepTemplateRepo,
      milestoneStepTemplateVersionRepo,
      defaultMilestoneStepTemplateService,
      milestoneTemplateRepo,
      milestoneTemplateVersionRepo,
      defaultMilestoneTemplateService,
      transactor
    )
  lazy val projectController = new ProjectController(projectEndpoints, projectService, tgClock)
  lazy val accountEndpoints = new AccountEndpoints(securedEndpoint)
  lazy val accountTypeRepo = new AccountTypeRepo
  val accountServiceCore = new AccountServiceCore(accountRepo, transactor)
  lazy val accountController = new AccountController(accountEndpoints, accountServiceCore)
  lazy val accountManagementService =
    new AccountManagementService(accountRepo, new AccountManagementRepoImpl, tgClock, transactor)
  lazy val accountManagementController =
    new AccountManagementController(accountEndpoints, accountManagementService, adminCheck)

  lazy val baseEndpoint = BaseEndpoint.baseEndpoint

  lazy val organizationRepo = new OrganizationRepoImpl
  lazy val organizationAccountRepo = new OrganizationAccountRepoImpl
  lazy val organizationService =
    new OrganizationService(organizationRepo, organizationAccountRepo, projectRepo, tgClock, transactor)
  lazy val organizationEndpoints = new OrganizationEndpoints(securedEndpoint)
  lazy val organizationController =
    new OrganizationController(organizationEndpoints, organizationService, adminCheck, tgClock)

  lazy val taskStatusRepo = new TaskStatusRepoImpl
  lazy val taskStatusService = new TaskStatusService(taskStatusRepo, tgClock, transactor)
  lazy val taskStatusEndpoints = new TaskStatusEndpoints(securedEndpoint)
  lazy val taskStatusController = new TaskStatusController(taskStatusService, taskStatusEndpoints, tgClock)

  lazy val projectLabelRepo = new ProjectLabelRepoImpl
  lazy val projectLabelService = new ProjectLabelService(projectLabelRepo, tgClock, transactor)
  lazy val projectLabelEndpoints = new ProjectLabelEndpoints(securedEndpoint)
  lazy val projectLabelController = new ProjectLabelController(projectLabelService, projectLabelEndpoints, tgClock)

  lazy val projectLabelCategoryRepo = new ProjectLabelCategoryRepoImpl
  lazy val projectLabelCategoryService = new ProjectLabelCategoryService(projectLabelCategoryRepo, tgClock, transactor)
  lazy val projectLabelCategoryEndpoints = new ProjectLabelCategoryEndpoints(securedEndpoint)
  lazy val projectLabelCategoryController =
    new ProjectLabelCategoryController(projectLabelCategoryService, projectLabelCategoryEndpoints, tgClock)

  lazy val taskLabelRepo = new TaskLabelRepoImpl
  lazy val taskLabelAssignmentRepo = new TaskLabelAssignmentRepoImpl
  lazy val taskLabelService = new TaskLabelService(taskLabelRepo, tgClock, transactor)
  lazy val taskLabelEndpoints = new TaskLabelEndpoints(securedEndpoint)
  lazy val taskLabelController = new TaskLabelController(taskLabelService, taskLabelEndpoints, tgClock)

  lazy val taskTemplateRepo = new TaskTemplateRepoImpl
  lazy val taskTemplateVersionRepo = new TaskTemplateVersionRepoImpl
  lazy val taskTemplateService =
    new TaskTemplateService(taskTemplateRepo, taskTemplateVersionRepo, taskLabelRepo, transactor)
  lazy val taskTemplateEndpoints = new TaskTemplateEndpoints(securedEndpoint)
  lazy val taskTemplateController = new TaskTemplateController(taskTemplateService, taskTemplateEndpoints, tgClock)

  lazy val tgPromptTemplateRepo = new TgPromptTemplateRepoImpl
  lazy val tgPromptTemplateVersionRepo = new TgPromptTemplateVersionRepoImpl
  lazy val tgPromptTemplateService =
    new TgPromptTemplateService(tgPromptTemplateRepo, tgPromptTemplateVersionRepo, transactor)
  lazy val tgPromptTemplateEndpoints = new TgPromptTemplateEndpoints(securedEndpoint)
  lazy val tgPromptTemplateController =
    new TgPromptTemplateController(tgPromptTemplateService, tgPromptTemplateEndpoints, tgClock)
  lazy val tgAiActionRepo = new TgAiActionRepoImpl
  lazy val tgAiActionEndpointRepo = new TgAiActionEndpointRepoImpl
  lazy val tgAiActionDefaultRepo = new TgAiActionDefaultRepoImpl
  lazy val tgAiActionService = new TgAiActionService(
    tgAiActionRepo,
    tgAiActionEndpointRepo,
    tgAiActionDefaultRepo,
    tgPromptTemplateRepo,
    projectAccountRepo,
    transactor
  )
  lazy val tgAiActionEndpoints = new TgAiActionEndpoints(securedEndpoint)
  lazy val tgAiActionController = new TgAiActionController(tgAiActionService, tgAiActionEndpoints, tgClock)

  lazy val tgAiClient: TgAiClient = new TgOpenAiClient(aiBackend, TgOllama.BaseUri, TgOllama.ReadTimeout)
  lazy val tgAiApiKeyProvider = new TgStaticApiKeyProvider(TgOllama.ApiKey)
  lazy val tgAiService = new TgAiService(tgAiClient, tgAiApiKeyProvider)
  lazy val tgAiModelEndpoints = new TgAiModelEndpoints(securedEndpoint)
  lazy val tgAiModelController = new TgAiModelController(tgAiService, tgAiModelEndpoints)

  lazy val milestoneAiSummaryEndpoints = new MilestoneAiSummaryStreamEndpoints(securedEndpoint)
  lazy val milestoneAiSummaryService =
    new MilestoneAiSummaryService(logEntryRepo, tgPromptTemplateService, tgAiService, transactor)
  lazy val milestoneAiSummaryController = new MilestoneAiSummaryController(
    milestoneAiSummaryEndpoints,
    milestoneAiSummaryService,
    milestoneService,
    dateRangeValidation,
    contributionService
  )

  lazy val dataExportDiskOps = new DiskOps
  lazy val dataExportRepo =
    new DataExportRepo(
      projectRepo,
      projectAccountRepo,
      projectLabelAssignmentRepo,
      projectLabelCategoryRepo,
      accountRepo,
      taskRepo,
      taskVersionRepo,
      taskStatusRepo,
      taskLabelRepo,
      taskLabelAssignmentRepo,
      linkRepo,
      linkVersionRepo,
      logEntryRepo,
      logEntryVersionRepo,
      milestoneRepo,
      milestoneVersionRepo,
      milestoneDateRepo,
      milestoneDateVersionRepo,
      milestoneSummaryRepo,
      milestoneSummaryVersionRepo,
      milestoneStepRepo,
      milestoneStepVersionRepo,
      milestoneStepTemplateRepo,
      milestoneStepTemplateVersionRepo,
      milestoneTemplateRepo,
      milestoneTemplateVersionRepo,
      taskTemplateRepo,
      taskTemplateVersionRepo,
      timeboxRepo,
      timeboxVersionRepo,
      tgPromptTemplateRepo,
      tgPromptTemplateVersionRepo
    )
  lazy val dataExportTreeStore = new DataExportTreeStore(dataExportDiskOps)

  lazy val dataImportConfigVerification = new DataImportConfigVerification()

  lazy val dataExportConfigRepo = new DataExportConfigRepoImpl
  lazy val dataExportConfigService = new DataExportConfigService(dataExportConfigRepo, tgClock, transactor)
  lazy val dataExportConfigEndpoints = new DataExportConfigEndpoints(securedEndpoint)
  lazy val dataExportConfigController =
    new DataExportConfigController(
      dataExportConfigEndpoints,
      dataExportConfigService,
      dataImportConfigVerification,
      tgClock
    )

  lazy val dataImportConfigRepo = new DataImportConfigRepoImpl
  lazy val dataImportConfigService = new DataImportConfigService(dataImportConfigRepo, tgClock, transactor)
  lazy val dataImportConfigEndpoints = new DataImportConfigEndpoints(securedEndpoint)
  lazy val dataImportConfigController =
    new DataImportConfigController(
      dataImportConfigEndpoints,
      dataImportConfigService,
      dataImportConfigVerification,
      tgClock
    )

  lazy val projectMappingRepo = new ProjectMappingRepoImpl
  lazy val projectMappingService = new ProjectMappingService(projectMappingRepo, transactor)
  lazy val projectMappingEndpoints = new ProjectMappingEndpoints(securedEndpoint)
  lazy val projectMappingController =
    new ProjectMappingController(projectMappingEndpoints, projectMappingService, tgClock)

  lazy val accountMappingRepo = new AccountMappingRepoImpl
  lazy val accountMappingService = new AccountMappingService(accountMappingRepo, transactor)
  lazy val accountMappingEndpoints = new AccountMappingEndpoints(securedEndpoint)
  lazy val accountMappingController =
    new AccountMappingController(accountMappingEndpoints, accountMappingService, tgClock)

  lazy val dataExportService =
    new DataExportService(dataExportRepo, dataExportTreeStore, dataExportConfigService, tgClock, transactor)
  lazy val dataImportRepo =
    new DataImportRepo(
      taskRepo,
      taskVersionRepo,
      linkRepo,
      linkVersionRepo,
      logEntryRepo,
      logEntryVersionRepo,
      projectMappingRepo,
      taskStatusRepo,
      tgClock,
      accountRepo,
      accountTypeRepo,
      accountMappingRepo,
      taskLabelRepo,
      taskLabelAssignmentRepo,
      projectLabelRepo,
      projectLabelCategoryRepo,
      projectLabelAssignmentRepo,
      projectAccountRepo,
      milestoneRepo,
      milestoneVersionRepo,
      milestoneDateRepo,
      milestoneDateVersionRepo,
      milestoneSummaryRepo,
      milestoneSummaryVersionRepo,
      milestoneStepRepo,
      milestoneStepVersionRepo,
      milestoneStepTemplateRepo,
      milestoneStepTemplateVersionRepo,
      milestoneTemplateRepo,
      milestoneTemplateVersionRepo,
      taskTemplateRepo,
      taskTemplateVersionRepo,
      timeboxRepo,
      timeboxVersionRepo,
      tgPromptTemplateRepo,
      tgPromptTemplateVersionRepo,
      projectRepo
    )
  lazy val dataImportService = new DataImportService(dataExportRepo, dataImportRepo, dataExportTreeStore, transactor)
  lazy val dataSyncPendingService = new DataSyncPendingService(dataExportService, dataImportConfigService)
  lazy val dataExportEndpoints = new DataExportEndpoints(securedEndpoint)
  lazy val dataExportController = new DataExportController(
    dataExportEndpoints,
    dataExportService,
    dataImportService,
    dataSyncPendingService
  )

}
