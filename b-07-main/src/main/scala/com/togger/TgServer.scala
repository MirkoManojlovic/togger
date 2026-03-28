package com.togger
import cats.effect.{IO, Resource}
import com.togger.account.{AccountEndpoints, AccountServiceCore}
import com.togger.auth.{BaseEndpoint, SecuredEndpoint}
import com.togger.controllers.account.AccountController
import com.togger.controllers.logentry.LogEntryController
import com.togger.controllers.organization.{OrganizationController, OrganizationService}
import com.togger.controllers.project.{DefaultTaskStatusService, ProjectController, ProjectService}
import com.togger.controllers.task.{TaskController, TaskStatusController}
import com.togger.dep.AuthDependenciesModule
import com.togger.http.HttpModule
import com.togger.logentry.{LogEntryEndpoints, LogEntryRepoImpl}
import com.togger.organization.{OrganizationAccountRepoImpl, OrganizationEndpoints, OrganizationRepoImpl}
import com.togger.project.{ProjectAccountRepoImpl, ProjectEndpoints, ProjectRepoImpl}
import com.togger.task._
import com.togger.taskStatus.TaskStatusRepoImpl
import doobie.Transactor
import doobie.postgres.implicits._
import org.http4s.server

trait TransactorModule {
  val transactor: Transactor[IO]
}

class TransactorModuleApp(val transactor: Transactor[IO]) extends TransactorModule {}

object TgServer {

  def instance(transactorModule: TransactorModule): Resource[IO, server.Server] = {

    lazy val authDependenciesModule = new AuthDependenciesModule(transactorModule.transactor)
    import authDependenciesModule._

    lazy val dependenciesModule =
      new DependenciesModule(transactorModule.transactor, securedEndpoint, authDependenciesModule)
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
        projectController.findByid,
        projectController.update,
        projectController.save,
        projectController.findByOrganizationIdAndAccountId,
        projectController.findAllMembers,
        accountController.searchByFirstNameLastNameEmail,
        logEntryController.findByTaskId,
        logEntryController.save,
        logEntryController.updateLogEntry,
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
        taskStatusController.delete
      )
    )

  }
}

class DependenciesModule(
  val transactor: Transactor[IO],
  securedEndpoint: SecuredEndpoint,
  authDependenciesModule: AuthDependenciesModule
) {
  import authDependenciesModule._
  lazy val logEntryRepo = new LogEntryRepoImpl
  lazy val logEntryEndpoints = new LogEntryEndpoints(securedEndpoint)
  lazy val logEntryController = new LogEntryController(logEntryEndpoints, logEntryRepo, tgClock, transactor)

  lazy val taskRepo = new TaskRepoImpl
  lazy val linkRepo = new LinkRepoImpl
  lazy val taskService = new TaskService(taskRepo, linkRepo, logEntryRepo, tgClock, transactor)
  lazy val taskEndpoints = new TaskEndpoints(securedEndpoint)
  lazy val taskController = new TaskController(taskService, taskEndpoints, tgClock)

  lazy val projectEndpoints = new ProjectEndpoints(securedEndpoint)
  lazy val projectRepo = new ProjectRepoImpl
  lazy val projectAccountRepo = new ProjectAccountRepoImpl
  lazy val defaultTaskStatusService = new DefaultTaskStatusService(tgClock)
  lazy val projectService =
    new ProjectService(projectRepo, projectAccountRepo, taskStatusRepo, tgClock, defaultTaskStatusService, transactor)
  lazy val projectController = new ProjectController(projectEndpoints, projectService, tgClock)
  lazy val accountEndpoints = new AccountEndpoints(securedEndpoint)
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
}
