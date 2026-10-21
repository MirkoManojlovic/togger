package com.togger

import cats.effect.unsafe.IORuntime
import com.raquo.airstream.core.Signal
import com.raquo.airstream.ownership.OneTimeOwner
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.raquo.waypoint.SplitRender
import com.togger.auth.{BaseEndpoint, SecuredEndpoint}
import com.togger.backendcalls.{ProtectedBackendClient, SecurityLogicJs, UnprotectedBackendClient}
import com.togger.common.{DateRangeValidation, TgClockImpl}
import com.togger.markdown.TgMarkdown
import com.togger.routes.Pages._
import com.togger.routes.{AuthenticationRouteGuard, FrontendPage, TgRouter}
import com.togger.ui.auth._
import com.togger.ui.components.{TgBreadcrumbTrail, TgBreadcrumbs}
import com.togger.ui.localstorage.LocalStorageBrowser
import com.togger.ui.milestone._
import com.togger.ui.contributions.{ContributionsComponent, ContributionsFeService}
import com.togger.ui.timebox._
import com.togger.ui.modal.ModalFactory
import com.togger.ui.navbar.{Logout, NavbarComponent, Sidebar, SidebarCollapseStore}
import com.togger.ui.organization._
import com.togger.ui.project._
import com.togger.ui.projectmapping._
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task._
import com.togger.ui.taskconfig._
import com.togger.ui.taskdumpconfig._
import com.togger.ui.taskdumpstatus._
import com.togger.ui.taskloadconfig._
import com.togger.ui.task.quick.{CreateTaskQuick, QuickCreateHost}
import com.togger.ui.task.bulk.BulkAddTasks
import com.togger.ui.projectlabel._
import com.togger.ui.apexchart.ApexChartDemoComponent
import com.togger.ui.theme.ThemeStore
import com.togger.ui.slacker.SlackerComponent
import com.togger.ui.teamdaily.TeamDailyComponent
import com.togger.ui.toast.{Toast, ToastMessagesService}
import org.scalajs.dom.HTMLDivElement
import sttp.client3.UriContext

import java.util.UUID

class TgSplitRender(router: TgRouter[FrontendPage]) {

  // FIXME
  implicit val runtime = cats.effect.unsafe.implicits.global

  lazy val elementForSpinner = div()
  lazy val spinnerService = SpinnerService.globalSpinner(elementForSpinner)

  val elementForModal = div()
  val elementForQuickCreate = div()
  lazy val quickCreateHost = new QuickCreateHost(
    elementForQuickCreate,
    onClose =>
      new CreateTaskQuick(
        taskService,
        taskStatusService,
        taskLabelService,
        projectService,
        milestoneService,
        organizationService,
        spinnerService,
        tgClock,
        accountService,
        Sidebar.projectIdForPage(router.currentPageSignal.now()),
        toast,
        onClose
      ).element
  )
  val elementForBulkCreate = div()
  lazy val taskProjectDataLoader = new TaskProjectDataLoader(
    taskStatusService,
    taskLabelService,
    projectService,
    milestoneService,
    organizationService,
    accountService
  )
  lazy val bulkCreateHost = new QuickCreateHost(
    elementForBulkCreate,
    onClose =>
      new BulkAddTasks(
        taskService,
        taskProjectDataLoader,
        spinnerService,
        accountService,
        tgClock,
        Sidebar.projectIdForPage(router.currentPageSignal.now()),
        localStorage,
        toast,
        onClose
      ).element
  )
  val elementForTeamDaily = div()
  lazy val teamDailyHost = new QuickCreateHost(
    elementForTeamDaily,
    onClose =>
      new TeamDailyComponent(
        taskService,
        taskProjectDataLoader,
        milestoneService,
        spinnerService,
        accountService,
        tgClock,
        tgDateFormater,
        tgMarkdown,
        Sidebar.projectIdForPage(router.currentPageSignal.now()),
        localStorage,
        toast,
        onClose
      ).element
  )
  val elementForSlacker = div()
  lazy val slackerHost = new QuickCreateHost(
    elementForSlacker,
    onClose =>
      new SlackerComponent(
        taskService,
        taskProjectDataLoader,
        milestoneService,
        spinnerService,
        accountService,
        tgClock,
        tgDateFormater,
        tgMarkdown,
        Sidebar.projectIdForPage(router.currentPageSignal.now()),
        localStorage,
        toast,
        onClose
      ).element
  )
  lazy val modalFactory = new ModalFactory(elementForModal)

  lazy val securedEndpoint = new SecuredEndpoint(new SecurityLogicJs)
  lazy val baseEndpoint = BaseEndpoint.baseEndpoint

  lazy val localStorage = new LocalStorageBrowser
  lazy val sidebarCollapseStore = new SidebarCollapseStore(localStorage)
  lazy val themeStore = new ThemeStore(localStorage)
  lazy val timeboxStagingStore = new TimeboxStagingStore(localStorage)
  lazy val timeboxFormDraftStore = new TimeboxFormDraftStore(localStorage)
  lazy val tokenStorageKeys = TokenStorageKeys("accessTokenKey", "refreshTokenKey")

  themeStore.isDark.foreach(themeStore.applyToDocument)(new OneTimeOwner(() => ()))
  themeStore.listenToSystemPreference()

  lazy val loginRepo = new LoginRepo(localStorage, tokenStorageKeys)
  lazy val loginService = new LoginService(baseEndpoint, securedEndpoint, loginRepo)
  lazy val authenticationRouteGuard = new AuthenticationRouteGuard(loginRepo, LoginPage, ShowAllOrganizationsPage())
  lazy val navigationSelection =
    NavigationSelection(
      loginRepo,
      organizationProjectsLoader,
      accountService,
      router,
      taskDumpService,
      taskLoadConfigService,
      spinnerService,
      sidebarCollapseStore,
      themeStore,
      () => quickCreateHost.open(),
      () => bulkCreateHost.open(),
      () => teamDailyHost.open(),
      () => slackerHost.open(),
      toast
    )

  lazy val protectedBackendClient = new ProtectedBackendClient(loginRepo, baseUri = uri"http://localhost:8081/")
  lazy val unprotectedBackendClient = new UnprotectedBackendClient(baseUri = uri"http://localhost:8081/")

  lazy val taskService = new TaskFeService(securedEndpoint, protectedBackendClient)
  lazy val taskStatusService = new TaskStatusFeService(securedEndpoint, protectedBackendClient)
  lazy val projectLabelService = new ProjectLabelFeService(securedEndpoint, protectedBackendClient)
  lazy val projectLabelCategoryService = new ProjectLabelCategoryFeService(securedEndpoint, protectedBackendClient)
  lazy val taskLabelService = new TaskLabelFeService(securedEndpoint, protectedBackendClient)
  lazy val taskDumpService = new TaskDumpFeService(securedEndpoint, protectedBackendClient)
  lazy val milestoneService = new MilestoneFeService(securedEndpoint, protectedBackendClient)
  lazy val timeboxService = new TimeboxFeService(securedEndpoint, protectedBackendClient)
  lazy val contributionsService = new ContributionsFeService(securedEndpoint, protectedBackendClient)
  lazy val milestoneDateService = new MilestoneDateFeService(securedEndpoint, protectedBackendClient)
  lazy val dateRangeValidation = new DateRangeValidation

  lazy val accountService = new AccountServiceImpl(loginRepo)
  lazy val changePasswordService = new ChangePasswordService(baseEndpoint, securedEndpoint, protectedBackendClient)

  lazy val tgClock = new TgClockImpl
  lazy val tgDateFormater = new TgDateFormater

  val toastHolder = div(
    zIndex(1),
    overflow.hidden,
    display.flex,
    justifyContent.end,
//    height.rem(5),
    paddingTop.rem(0.5)
  )
  lazy val toastMessageDisplayTimeInMillis = 3000
  lazy val toastMessagesService = new ToastMessagesService(toastMessageDisplayTimeInMillis, new OneTimeOwner(() => ()))
  lazy val toast = new Toast(toastMessagesService, toastHolder)

  lazy val findAccountServiceImpl = new FindAccountServiceImpl(securedEndpoint, protectedBackendClient)
//  lazy val findAccounts = new FindAccountsComponent(findAccountServiceImpl)

  lazy val projectService = new ProjectServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val organizationService = new OrganizationServiceFeImpl(securedEndpoint, protectedBackendClient)
  lazy val organizationProjectsLoader = new OrganizationProjectsLoader(organizationService, projectService)
  lazy val taskDumpConfigService = new TaskDumpConfigFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val taskLoadConfigService = new TaskLoadConfigFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val projectMappingService = new ProjectMappingFeServiceImpl(securedEndpoint, protectedBackendClient)

  lazy val tgMarkdown = new TgMarkdown()

  private def breadcrumbsFor(page: FrontendPage): HtmlElement = {
    val trail = TgBreadcrumbTrail.forPage(page)
    if (trail.isEmpty) div()
    else div(cls("mb-4"), new TgBreadcrumbs(trail, router).element)
  }

  lazy val splitRender: SplitRender[FrontendPage, HtmlElement] =
    SplitRender[FrontendPage, HtmlElement](router.currentPageSignal).collectSignal((signal: Signal[FrontendPage]) =>
      div(
//        cls("pb-[2rem] pl-[2rem] pr-[2rem]"),
        child <-- navigationSelection.navigationSignal,
        elementForSpinner,
        toastHolder,
        elementForModal,
        elementForQuickCreate,
        elementForBulkCreate,
        elementForTeamDaily,
        elementForSlacker,
        div(
          cls <-- loginRepo.authenticatedSignal.combineWith(sidebarCollapseStore.current).map {
            case (accountOpt, collapsed) =>
              if (accountOpt.isDefined) {
                if (collapsed) TgSplitRender.sidebarPaddingCollapsed else TgSplitRender.sidebarPadding
              } else ""
          },
          child <-- signal.map(breadcrumbsFor),
          child <-- signal
            .map(page => authenticationRouteGuard.canVisitPage(page))
            .map {
              case HomePage  => router.route(ShowAllOrganizationsPage()); div()
              case LoginPage => new LoginComponent(router, loginService, spinnerService).element
              case ChangePasswordPage =>
                new ChangePasswordComponent(changePasswordService, accountService, spinnerService).element
              case ApexChartDemoPage => new ApexChartDemoComponent(themeStore.isDark).element
              case CreateTaskPage(projectId: UUID) =>
                new CreateTaskComponent(
                  taskService,
                  taskStatusService,
                  taskLabelService,
                  milestoneService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectService,
                  projectId,
                  toast
                ).element
              case CreateTaskStatusPage(projectId: UUID) =>
                new CreateTaskStatusComponent(
                  taskStatusService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectId,
                  toast
                ).element
              case ShowAllTaskStatusesPage(projectId, listParams) =>
                new ShowAllTaskStatusesComponent(
                  taskStatusService,
                  router,
                  spinnerService,
                  accountService,
                  projectId,
                  listParams
                ).element
              case EditTaskStatusPage(projectId: UUID, taskStatusId: UUID) =>
                new EditTaskStatusComponent(
                  taskStatusService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectId,
                  taskStatusId,
                  toast
                ).element
              case CreateTaskLabelPage(projectId: UUID) =>
                new CreateTaskLabelComponent(
                  taskLabelService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectId,
                  toast
                ).element
              case ShowAllTaskLabelsPage(projectId, listParams) =>
                new ShowAllTaskLabelsComponent(
                  taskLabelService,
                  router,
                  spinnerService,
                  accountService,
                  projectId,
                  listParams
                ).element
              case EditTaskLabelPage(projectId: UUID, taskLabelId: UUID) =>
                new EditTaskLabelComponent(
                  taskLabelService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectId,
                  taskLabelId,
                  toast
                ).element
              case ShowAllTaskConfigsPage(projectId: UUID, listParams) =>
                new ShowAllTaskConfigsComponent(
                  taskDumpConfigService,
                  taskDumpService,
                  taskLoadConfigService,
                  tgDateFormater,
                  router,
                  spinnerService,
                  toast,
                  projectId,
                  listParams
                ).element
              case TaskDumpStatusPage(projectId: UUID) =>
                new TaskDumpStatusComponent(
                  taskDumpService,
                  spinnerService,
                  toast,
                  projectId
                ).element
              case CreateTaskDumpConfigPage(projectId: UUID) =>
                new CreateTaskDumpConfigComponent(
                  taskDumpConfigService,
                  spinnerService,
                  projectId,
                  toast
                ).element
              case EditTaskDumpConfigPage(id: UUID) =>
                new EditTaskDumpConfigComponent(
                  id,
                  taskDumpConfigService,
                  router,
                  spinnerService,
                  toast
                ).element
              case CreateTaskLoadConfigPage(projectId: UUID) =>
                new CreateTaskLoadConfigComponent(
                  taskLoadConfigService,
                  spinnerService,
                  projectId,
                  toast
                ).element
              case EditTaskLoadConfigPage(id: UUID) =>
                new EditTaskLoadConfigComponent(
                  id,
                  taskLoadConfigService,
                  router,
                  spinnerService,
                  toast
                ).element
              case ShowAllTasksPage(projectId: UUID, listParams) =>
                new ShowAllTasksComponent(
                  taskService,
                  taskStatusService,
                  taskLabelService,
                  milestoneService,
                  projectService,
                  router,
                  spinnerService,
                  tgDateFormater,
                  tgClock,
                  projectId,
                  listParams,
                  toast,
                  modalFactory
                ).element
              case EditTaskPage(projectId, taskId) =>
                new EditTaskComponent(
                  taskId,
                  projectId,
                  taskService,
                  taskStatusService,
                  taskLabelService,
                  milestoneService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectService,
                  router,
                  dateRangeValidation,
                  toast,
                  tgMarkdown
                ).element
              case CreateProjectPage(organizationId) =>
                new CreateProjectComponent(
                  findAccountServiceImpl,
                  projectService,
                  projectLabelService,
                  organizationService,
                  accountService,
                  spinnerService,
                  localStorage,
                  organizationId,
                  toast
                ).element
              case ShowProjectsForOrganizationIdTable(organizationId, listParams) =>
                new ShowAllProjectsTableComponent(
                  projectService,
                  organizationService,
                  router,
                  spinnerService,
                  accountService,
                  organizationId,
                  listParams
                ).element

              case ShowProjectsForOrganizationIdStatus(organizationId) =>
                new ShowAllProjectsStatusComponent(
                  projectService,
                  organizationService,
                  taskService,
                  router,
                  spinnerService,
                  accountService,
                  tgDateFormater,
                  tgMarkdown,
                  organizationId
                ).element

              case CreateOrganizationPage =>
                new CreateOrganizationComponent(
                  findAccountServiceImpl,
                  organizationService,
                  accountService,
                  spinnerService,
                  localStorage,
                  toast
                ).element
              case ShowAllOrganizationsPage(listParams) =>
                new ShowAllOrganizationsComponent(
                  organizationService,
                  router,
                  spinnerService,
                  accountService,
                  listParams
                ).element
              case EditOrganizationPage(organizationId) =>
                new EditOrganizationComponent(
                  organizationId,
                  organizationService,
                  findAccountServiceImpl,
                  accountService,
                  router,
                  spinnerService,
                  localStorage,
                  toast
                ).element
              case CreateProjectLabelPage =>
                new CreateProjectLabelComponent(
                  projectLabelService,
                  projectLabelCategoryService,
                  spinnerService,
                  tgClock,
                  accountService,
                  toast
                ).element
              case ShowAllProjectLabelsPage(listParams) =>
                new ShowAllProjectLabelsComponent(
                  projectLabelService,
                  projectLabelCategoryService,
                  router,
                  spinnerService,
                  accountService,
                  listParams
                ).element
              case EditProjectLabelPage(projectLabelId) =>
                new EditProjectLabelComponent(
                  projectLabelService,
                  projectLabelCategoryService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectLabelId,
                  toast
                ).element
              case CreateProjectLabelCategoryPage =>
                new CreateProjectLabelCategoryComponent(
                  projectLabelCategoryService,
                  spinnerService,
                  tgClock,
                  accountService,
                  toast
                ).element
              case ShowAllProjectLabelCategoriesPage(listParams) =>
                new ShowAllProjectLabelCategoriesComponent(
                  projectLabelCategoryService,
                  router,
                  spinnerService,
                  accountService,
                  listParams
                ).element
              case EditProjectLabelCategoryPage(projectLabelCategoryId) =>
                new EditProjectLabelCategoryComponent(
                  projectLabelCategoryService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectLabelCategoryId,
                  toast
                ).element
              case EditProjectPage(projectId) =>
                new EditProjectComponent(
                  findAccountServiceImpl,
                  projectService,
                  projectLabelService,
                  organizationService,
                  accountService,
                  router,
                  spinnerService,
                  localStorage,
                  projectId,
                  toast
                ).element
              case SearchProjectsByLabelPage =>
                new SearchProjectsByLabelComponent(
                  projectService,
                  projectLabelService,
                  spinnerService,
                  router
                ).element
              case CreateProjectMappingPage(projectId: UUID) =>
                new CreateProjectMappingComponent(
                  projectMappingService,
                  router,
                  spinnerService,
                  projectId,
                  toast
                ).element
              case CreateMilestonePage(projectId: UUID) =>
                new CreateMilestoneComponent(
                  milestoneService,
                  milestoneDateService,
                  spinnerService,
                  dateRangeValidation,
                  projectService,
                  projectId,
                  toast
                ).element
              case EditMilestonePage(id: UUID) =>
                new EditMilestoneComponent(
                  milestoneService,
                  milestoneDateService,
                  spinnerService,
                  dateRangeValidation,
                  projectService,
                  id,
                  toast
                ).element
              case ShowMilestonesPage(projectId: UUID, listParams) =>
                new ShowMilestonesComponent(
                  milestoneService,
                  projectService,
                  router,
                  spinnerService,
                  tgDateFormater,
                  projectId,
                  listParams
                ).element
              case ShowMilestoneHistoryPage(projectId: UUID, historyParams) =>
                new MilestoneHistoryComponent(
                  taskService,
                  milestoneService,
                  milestoneDateService,
                  projectService,
                  router,
                  spinnerService,
                  tgDateFormater,
                  tgClock,
                  projectId,
                  historyParams
                ).element
              case ShowTimeboxesPage(projectId: UUID, listParams) =>
                new ShowTimeboxesComponent(
                  timeboxService,
                  projectService,
                  router,
                  spinnerService,
                  tgDateFormater,
                  projectId,
                  listParams,
                  toast,
                  modalFactory
                ).element
              case CreateTimeboxPage(projectId: UUID) =>
                new TimeboxFormComponent(
                  TimeboxFormTarget.Create(projectId),
                  timeboxService,
                  taskService,
                  taskStatusService,
                  taskLabelService,
                  milestoneService,
                  projectService,
                  tgDateFormater,
                  timeboxStagingStore,
                  timeboxFormDraftStore,
                  router,
                  spinnerService,
                  tgClock,
                  themeStore.isDark,
                  toast
                ).element
              case EditTimeboxPage(projectId: UUID, timeboxId: UUID) =>
                new TimeboxFormComponent(
                  TimeboxFormTarget.NewVersion(projectId, timeboxId),
                  timeboxService,
                  taskService,
                  taskStatusService,
                  taskLabelService,
                  milestoneService,
                  projectService,
                  tgDateFormater,
                  timeboxStagingStore,
                  timeboxFormDraftStore,
                  router,
                  spinnerService,
                  tgClock,
                  themeStore.isDark,
                  toast
                ).element
              case TimeboxHistoryPage(projectId: UUID, timeboxId: UUID) =>
                new TimeboxHistoryComponent(
                  timeboxService,
                  router,
                  spinnerService,
                  tgDateFormater,
                  projectId,
                  timeboxId
                ).element
              case TimeboxReviewPage(projectId: UUID, timeboxId: UUID) =>
                new TimeboxReviewComponent(
                  timeboxService,
                  taskService,
                  milestoneService,
                  projectService,
                  tgDateFormater,
                  tgMarkdown,
                  router,
                  spinnerService,
                  tgClock,
                  themeStore.isDark,
                  projectId,
                  timeboxId
                ).element
              case ShowContributionsPage(projectId: UUID, params) =>
                new ContributionsComponent(
                  contributionsService,
                  tgDateFormater,
                  tgClock,
                  router,
                  spinnerService,
                  themeStore.isDark,
                  projectId,
                  params,
                  toast
                ).element
              case NotFoundPage(url) => div(p(s"Oops page $url not found!"))
            }
        )
      )
    )
}

object TgSplitRender {
  lazy val sidebarPadding = "p-4 sm:ml-64 mt-14"
  lazy val sidebarPaddingCollapsed = "p-4 sm:ml-16 mt-14"
}

case class NavigationSelection(
  loginRepo: LoginRepo,
  organizationProjectsLoader: OrganizationProjectsLoader,
  accountService: AccountService,
  router: TgRouter[FrontendPage],
  taskDumpService: TaskDumpFeService,
  taskLoadConfigService: TaskLoadConfigFeService,
  spinnerService: SpinnerService,
  sidebarCollapseStore: SidebarCollapseStore,
  themeStore: ThemeStore,
  onQuickCreate: () => Unit,
  onBulkCreate: () => Unit,
  onTeamDaily: () => Unit,
  onSlacker: () => Unit,
  toast: Toast
)(implicit IORuntime: IORuntime) {

  lazy val navigationSignal: Signal[ReactiveHtmlElement[HTMLDivElement]] = {
    loginRepo.authenticatedSignal.map(returnedAuthAccount =>
      returnedAuthAccount
        .map(_ =>
          div(
            new NavbarComponent(router, Logout(loginRepo, router), loginRepo, themeStore).element,
            new Sidebar(
              router,
              organizationProjectsLoader,
              accountService,
              taskDumpService,
              taskLoadConfigService,
              spinnerService,
              sidebarCollapseStore,
              onQuickCreate,
              onBulkCreate,
              onTeamDaily,
              onSlacker,
              toast
            ).element
          )
        )
        .getOrElse(div())
    )
  }

}
