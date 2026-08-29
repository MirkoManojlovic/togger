package com.togger
import cats.effect.{IO, Resource}
import com.togger.account.{AccountEndpoints, AccountServiceCore, AccountTypeRepo}
import com.togger.auth.{BaseEndpoint, SecuredEndpoint}
import com.togger.controllers.account.AccountController
import com.togger.controllers.logentry.LogEntryController
import com.togger.controllers.organization.{OrganizationController, OrganizationService}
import com.togger.controllers.project.{DefaultTaskStatusService, ProjectController, ProjectService, TaskDumpController}
import com.togger.controllers.accountmapping.AccountMappingController
import com.togger.controllers.projectmapping.ProjectMappingController
import com.togger.controllers.task.{LinkController, TaskController, TaskStatusController}
import com.togger.controllers.taskdumpconfig.TaskDumpConfigController
import com.togger.controllers.taskloadconfig.TaskLoadConfigController
import com.togger.dep.AuthDependenciesModule
import com.togger.dump._
import com.togger.http.HttpModule
import com.togger.logentry.{LogEntryEndpoints, LogEntryRepoImpl, LogEntryService, LogEntryVersionRepoImpl}
import com.togger.organization.{OrganizationAccountRepoImpl, OrganizationEndpoints, OrganizationRepoImpl}
import com.togger.project.{ProjectAccountRepoImpl, ProjectEndpoints, ProjectRepoImpl}
import com.togger.accountmapping.{AccountMappingEndpoints, AccountMappingRepoImpl, AccountMappingService}
import com.togger.projectmapping.{ProjectMappingEndpoints, ProjectMappingRepoImpl, ProjectMappingService}
import com.togger.task._
import com.togger.taskStatus.TaskStatusRepoImpl
import com.togger.taskdumpconfig.{TaskDumpConfigEndpoints, TaskDumpConfigRepoImpl, TaskDumpConfigService}
import com.togger.taskloadconfig.{TaskLoadConfigEndpoints, TaskLoadConfigRepoImpl, TaskLoadConfigService}
import doobie.Transactor
import org.http4s.server.Server

trait TransactorModule {
  val transactor: Transactor[IO]
}

class TransactorModuleApp(val transactor: Transactor[IO]) extends TransactorModule {}

object TgServer {

  def instance(transactorModule: TransactorModule, dumpLimit: Int): Resource[IO, Server] = {

    lazy val authDependenciesModule = new AuthDependenciesModule(transactorModule.transactor)
    import authDependenciesModule._

    lazy val dependenciesModule =
      new DependenciesModule(
        transactorModule.transactor,
        securedEndpoint,
        authDependenciesModule,
        dumpLimit
      )
    import dependenciesModule._

    lazy val httpModule = new HttpModule()
    httpModule.createServer(
      List(
        loginController.login,
        loginController.expireOldSessions,
        loginController.logoutEveryone,
        loginController.changePassword,
        loginController.exampleAuth,
        taskController.findById,
        taskController.findByProjectId,
        taskController.save,
        taskController.update,
        taskController.listVersions,
        taskController.updateVersion,
        taskController.deleteVersion,
        linkController.listVersions,
        linkController.updateVersion,
        linkController.deleteVersion,
        projectController.findByid,
        projectController.update,
        projectController.save,
        projectController.findByOrganizationIdAndAccountId,
        projectController.findAllMembers,
        accountController.searchByFirstNameLastNameEmail,
        logEntryController.findByTaskId,
        logEntryController.save,
        logEntryController.updateLogEntry,
        logEntryController.listVersions,
        logEntryController.updateVersion,
        logEntryController.deleteVersion,
        organizationController.findAll,
        organizationController.findByAccountId,
        organizationController.save,
        organizationController.update,
        organizationController.findById,
        organizationController.findAllMembers,
        organizationController.findOrgAndProjectsByAccountId,
        taskStatusController.findByProjectId,
        taskStatusController.findById,
        taskStatusController.save,
        taskStatusController.update,
        taskStatusController.delete,
        taskDumpController.dumpTasks,
        taskDumpController.checkStatus,
        taskDumpController.dumpByConfig,
        taskDumpController.checkStatusByConfig,
        taskDumpController.loadTasks,
        taskDumpConfigController.findById,
        taskDumpConfigController.save,
        taskDumpConfigController.update,
        taskDumpConfigController.delete,
        taskDumpConfigController.findByProjectId,
        taskLoadConfigController.findById,
        taskLoadConfigController.save,
        taskLoadConfigController.update,
        taskLoadConfigController.delete,
        taskLoadConfigController.findByProjectId,
        projectMappingController.findById,
        projectMappingController.save,
        projectMappingController.update,
        projectMappingController.delete,
        projectMappingController.findByProjectIdDb,
        accountMappingController.save,
        accountMappingController.findByAccountIdDb,
        accountMappingController.findByAccountIdDump,
        accountMappingController.delete
      )
    )
  }
}

class DependenciesModule(
  val transactor: Transactor[IO],
  securedEndpoint: SecuredEndpoint,
  authDependenciesModule: AuthDependenciesModule,
  dumpLimit: Int
) {
  import authDependenciesModule._
  lazy val logEntryRepo = new LogEntryRepoImpl
  lazy val logEntryVersionRepo = new LogEntryVersionRepoImpl
  lazy val logEntryService = new LogEntryService(logEntryRepo, logEntryVersionRepo, transactor)
  lazy val logEntryEndpoints = new LogEntryEndpoints(securedEndpoint)
  lazy val logEntryController = new LogEntryController(logEntryEndpoints, logEntryService)

  lazy val taskRepo = new TaskRepoImpl
  lazy val taskVersionRepo = new TaskVersionRepoImpl
  lazy val taskVersionService = new TaskVersionService(taskVersionRepo, transactor)
  lazy val linkRepo = new LinkRepoImpl
  lazy val linkVersionRepo = new LinkVersionRepoImpl
  lazy val linkService = new LinkService(linkVersionRepo, transactor)
  lazy val linkEndpoints = new LinkEndpoints(securedEndpoint)
  lazy val linkController = new LinkController(linkEndpoints, linkService)
  lazy val taskService =
    new TaskService(taskRepo, taskVersionRepo, linkRepo, linkVersionRepo, logEntryRepo, logEntryVersionRepo, transactor)
  lazy val taskEndpoints = new TaskEndpoints(securedEndpoint)
  lazy val taskController = new TaskController(taskService, taskVersionService, taskEndpoints, tgClock)

  lazy val projectEndpoints = new ProjectEndpoints(securedEndpoint)
  lazy val projectRepo = new ProjectRepoImpl
  lazy val projectAccountRepo = new ProjectAccountRepoImpl
  lazy val defaultTaskStatusService = new DefaultTaskStatusService(tgClock)
  lazy val projectService =
    new ProjectService(projectRepo, projectAccountRepo, taskStatusRepo, tgClock, defaultTaskStatusService, transactor)
  lazy val projectController = new ProjectController(projectEndpoints, projectService, tgClock)
  lazy val accountEndpoints = new AccountEndpoints(securedEndpoint)
  lazy val accountTypeRepo = new AccountTypeRepo
  val accountServiceCore = new AccountServiceCore(accountRepo, transactor)
  lazy val accountController = new AccountController(accountEndpoints, accountServiceCore)

  lazy val baseEndpoint = BaseEndpoint.baseEndpoint

  lazy val organizationRepo = new OrganizationRepoImpl
  lazy val organizationAccountRepo = new OrganizationAccountRepoImpl
  lazy val organizationService =
    new OrganizationService(organizationRepo, organizationAccountRepo, projectRepo, tgClock, transactor)
  lazy val organizationEndpoints = new OrganizationEndpoints(securedEndpoint)
  lazy val organizationController = new OrganizationController(organizationEndpoints, organizationService, tgClock)

  lazy val taskStatusRepo = new TaskStatusRepoImpl
  lazy val taskStatusService = new TaskStatusService(taskStatusRepo, tgClock, transactor)
  lazy val taskStatusEndpoints = new TaskStatusEndpoints(securedEndpoint)
  lazy val taskStatusController = new TaskStatusController(taskStatusService, taskStatusEndpoints, tgClock)

  lazy val taskDumpDiskOps = new DiskOps
  lazy val taskDumpRepo =
    new TaskDumpRepo(
      projectRepo,
      projectAccountRepo,
      accountRepo,
      taskRepo,
      taskVersionRepo,
      taskStatusRepo,
      linkRepo,
      linkVersionRepo,
      logEntryRepo,
      logEntryVersionRepo
    )
  lazy val taskDumpDiskWriter = new TaskDumpDiskWriter(taskDumpDiskOps, dumpLimit)

  lazy val taskDumpLoadConfigVerification = new TaskDumpLoadConfigVerification()

  lazy val taskDumpConfigRepo = new TaskDumpConfigRepoImpl
  lazy val taskDumpConfigService = new TaskDumpConfigService(taskDumpConfigRepo, tgClock, transactor)
  lazy val taskDumpConfigEndpoints = new TaskDumpConfigEndpoints(securedEndpoint)
  lazy val taskDumpConfigController =
    new TaskDumpConfigController(
      taskDumpConfigEndpoints,
      taskDumpConfigService,
      taskDumpLoadConfigVerification,
      tgClock
    )

  lazy val taskLoadConfigRepo = new TaskLoadConfigRepoImpl
  lazy val taskLoadConfigService = new TaskLoadConfigService(taskLoadConfigRepo, tgClock, transactor)
  lazy val taskLoadConfigEndpoints = new TaskLoadConfigEndpoints(securedEndpoint)
  lazy val taskLoadConfigController =
    new TaskLoadConfigController(
      taskLoadConfigEndpoints,
      taskLoadConfigService,
      taskDumpLoadConfigVerification,
      tgClock
    )

  lazy val projectMappingRepo = new ProjectMappingRepoImpl
  lazy val projectMappingService = new ProjectMappingService(projectMappingRepo, tgClock, transactor)
  lazy val projectMappingEndpoints = new ProjectMappingEndpoints(securedEndpoint)
  lazy val projectMappingController =
    new ProjectMappingController(projectMappingEndpoints, projectMappingService, tgClock)

  lazy val accountMappingRepo = new AccountMappingRepoImpl
  lazy val accountMappingService = new AccountMappingService(accountMappingRepo, transactor)
  lazy val accountMappingEndpoints = new AccountMappingEndpoints(securedEndpoint)
  lazy val accountMappingController =
    new AccountMappingController(accountMappingEndpoints, accountMappingService, tgClock)

  lazy val taskDumpService =
    new TaskDumpService(taskDumpRepo, taskDumpDiskWriter, taskDumpConfigService, tgClock, transactor)
  lazy val taskLoadRepo =
    new TaskLoadRepo(
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
      accountMappingRepo
    )
  lazy val taskLoadService = new TaskLoadService(taskDumpRepo, taskLoadRepo, taskDumpDiskWriter, transactor)
  lazy val taskDumpEndpoints = new TaskDumpEndpoints(securedEndpoint)
  lazy val taskDumpController = new TaskDumpController(taskDumpEndpoints, taskDumpService, taskLoadService)

}
