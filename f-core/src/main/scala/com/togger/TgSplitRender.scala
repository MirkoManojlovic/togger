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
import com.togger.preview.{PrefixedLocalStorage, PreviewConfig, PreviewModule}
import com.togger.routes.Pages._
import com.togger.routes.{AuthenticationRouteGuard, FrontendPage, TgRouter}
import com.togger.ui.auth._
import com.togger.ui.components.{TgBreadcrumbTrail, TgBreadcrumbs}
import com.togger.ui.localstorage.{LocalStorage, LocalStorageBrowser}
import com.togger.ui.milestone._
import com.togger.ui.contributions.{ContributionsComponent, ContributionsFeService, ContributionsFeServiceImpl, ContributionsFeServicePreview}
import com.togger.ui.timebox._
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.accountmanagement.{AccountManagementFeService, AccountManagementFeServiceImpl, AccountManagementFeServicePreview, EditAccountComponent, ShowAllAccountsComponent}
import com.togger.ui.mcp.McpSettingsComponent
import com.togger.ui.updater.UpdaterComponent
import com.togger.ui.synccheck.{SyncCheckSettingsComponent, SyncCheckSettingsStore, SyncPendingStore}
import com.togger.ui.navbar.{Logout, NavbarComponent, Sidebar, SidebarCollapseStore, SidebarRefresh}
import com.togger.ui.organization._
import com.togger.ui.project._
import com.togger.ui.projectmapping._
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task._
import com.togger.ui.syncconfig._
import com.togger.ui.tasktemplate._
import com.togger.ui.tgaiaction._
import com.togger.ui.tgprompttemplate._
import com.togger.ui.dataexportconfig._
import com.togger.ui.dataexportstatus._
import com.togger.ui.dataimportconfig._
import com.togger.ui.task.quick.{CreateTaskQuick, QuickCreateHost}
import com.togger.ui.task.bulk.BulkAddTasks
import com.togger.ui.projectlabel._
import com.togger.ui.apexchart.ApexChartDemoComponent
import com.togger.ui.theme.ThemeStore
import com.togger.ui.slacker.SlackerComponent
import com.togger.ui.teamdaily.TeamDailyComponent
import com.togger.ui.toast.{Toast, ToastMessagesService}
import org.scalajs.dom
import org.scalajs.dom.HTMLDivElement
import sttp.client3.UriContext

import java.util.UUID

class TgSplitRender(router: TgRouter[FrontendPage], previewConfig: PreviewConfig = PreviewConfig.disabled) {

  // FIXME
  implicit val runtime = cats.effect.unsafe.implicits.global

  lazy val elementForSpinner = div()
  lazy val spinnerService = SpinnerService.globalSpinner(
    elementForSpinner,
    error => toast.error(s"Something went wrong: ${Option(error.getMessage).getOrElse(error.toString)}"),
    waitForPaint = previewConfig.enabled
  )

  val elementForModal = div()
  val elementForQuickCreate = div()
  lazy val quickCreateHost = new QuickCreateHost(
    elementForQuickCreate,
    onClose =>
      new CreateTaskQuick(
        taskService,
        taskStatusService,
        taskLabelService,
        taskTemplateService,
        projectService,
        milestoneService,
        organizationService,
        spinnerService,
        tgClock,
        accountService,
        Sidebar.projectIdForPage(router.currentPageSignal.now()),
        router,
        modalFactory,
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

  private def confirmPreviewReset(): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(
        "Reset all preview data? Everything you changed in this browser is deleted and the demo data is restored. " +
          "This cannot be undone."
      ),
      title = "Reset preview data",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, reset it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      previewModule.resetAndReseed()
      close()
      dom.window.location.reload()
    }

  lazy val securedEndpoint = new SecuredEndpoint(new SecurityLogicJs)
  lazy val baseEndpoint = BaseEndpoint.baseEndpoint

  lazy val localStorage: LocalStorage =
    if (previewConfig.enabled) new PrefixedLocalStorage(previewConfig.storagePrefix, new LocalStorageBrowser)
    else new LocalStorageBrowser
  lazy val sidebarCollapseStore = new SidebarCollapseStore(localStorage)
  lazy val sidebarRefresh = new SidebarRefresh
  lazy val themeStore = new ThemeStore(localStorage)
  lazy val syncCheckSettingsStore = new SyncCheckSettingsStore(localStorage)
  lazy val syncPendingStore = new SyncPendingStore(dataExportService, syncCheckSettingsStore)
  lazy val timeboxStagingStore = new TimeboxStagingStore(localStorage)
  lazy val timeboxFormDraftStore = new TimeboxFormDraftStore(localStorage)
  lazy val tokenStorageKeys = TokenStorageKeys("accessTokenKey", "refreshTokenKey")

  themeStore.isDark.foreach(themeStore.applyToDocument)(new OneTimeOwner(() => ()))
  themeStore.listenToSystemPreference()

  lazy val loginRepo = new LoginRepo(localStorage, tokenStorageKeys)
  lazy val previewModule = new PreviewModule(localStorage, loginRepo, tgClock)
  if (previewConfig.enabled) previewModule.ensureSeeded()

  lazy val loginService: LoginService =
    if (previewConfig.enabled) new LoginServicePreview(previewModule.accounts, loginRepo)
    else new LoginServiceImpl(baseEndpoint, securedEndpoint, loginRepo)
  lazy val authenticationRouteGuard = new AuthenticationRouteGuard(loginRepo, LoginPage, ShowAllOrganizationsPage())
  lazy val navigationSelection =
    NavigationSelection(
      loginRepo,
      organizationProjectsLoader,
      accountService,
      router,
      dataExportService,
      dataImportConfigService,
      spinnerService,
      modalFactory,
      sidebarCollapseStore,
      sidebarRefresh,
      themeStore,
      syncPendingStore,
      () => quickCreateHost.open(),
      () => bulkCreateHost.open(),
      () => teamDailyHost.open(),
      () => slackerHost.open(),
      toast,
      previewConfig,
      () => confirmPreviewReset()
    )

  lazy val protectedBackendClient = new ProtectedBackendClient(loginRepo, baseUri = uri"http://localhost:8081/")
  lazy val unprotectedBackendClient = new UnprotectedBackendClient(baseUri = uri"http://localhost:8081/")

  lazy val taskService: TaskFeService =
    if (previewConfig.enabled)
      new TaskFeServicePreview(
        previewModule.context,
        previewModule.projects,
        previewModule.taskConfig,
        previewModule.tasks
      )
    else new TaskFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val taskStatusService: TaskStatusFeService =
    if (previewConfig.enabled)
      new TaskStatusFeServicePreview(
        previewModule.context,
        previewModule.projects,
        previewModule.taskConfig,
        previewModule.taskStats
      )
    else new TaskStatusFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val tgPromptTemplateService: TgPromptTemplateFeService =
    if (previewConfig.enabled) new TgPromptTemplateFeServicePreview(previewModule.context, previewModule.ai)
    else new TgPromptTemplateFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val accountManagementService: AccountManagementFeService =
    if (previewConfig.enabled)
      new AccountManagementFeServicePreview(
        previewModule.context,
        previewModule.accounts,
        previewModule.organizations,
        previewModule.projects
      )
    else new AccountManagementFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val tgAiActionService: TgAiActionFeService =
    if (previewConfig.enabled)
      new TgAiActionFeServicePreview(previewModule.context, previewModule.projects, previewModule.ai)
    else new TgAiActionFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val projectLabelService: ProjectLabelFeService =
    if (previewConfig.enabled) new ProjectLabelFeServicePreview(previewModule.context, previewModule.projectLabels)
    else new ProjectLabelFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val projectLabelCategoryService: ProjectLabelCategoryFeService =
    if (previewConfig.enabled)
      new ProjectLabelCategoryFeServicePreview(previewModule.context, previewModule.projectLabels)
    else new ProjectLabelCategoryFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val taskLabelService: TaskLabelFeService =
    if (previewConfig.enabled)
      new TaskLabelFeServicePreview(previewModule.context, previewModule.projects, previewModule.taskConfig)
    else new TaskLabelFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val taskTemplateService: TaskTemplateFeService =
    if (previewConfig.enabled) new TaskTemplateFeServicePreview(previewModule.context, previewModule.taskConfig)
    else new TaskTemplateFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val dataExportService: DataExportFeService =
    if (previewConfig.enabled)
      new DataExportFeServicePreview(previewModule.context, previewModule.projects, previewModule.dataSync)
    else new DataExportFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val milestoneService: MilestoneFeService =
    if (previewConfig.enabled)
      new MilestoneFeServicePreview(previewModule.context, previewModule.projects, previewModule.milestones)
    else new MilestoneFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val timeboxService: TimeboxFeService =
    if (previewConfig.enabled) new TimeboxFeServicePreview(previewModule.context, previewModule.tasks)
    else new TimeboxFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val contributionsService: ContributionsFeService =
    if (previewConfig.enabled)
      new ContributionsFeServicePreview(
        previewModule.context,
        previewModule.projects,
        previewModule.milestones,
        previewModule.taskConfig,
        previewModule.accounts,
        previewModule.tasks
      )
    else new ContributionsFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val milestoneAiSummaryStreamClient: MilestoneAiSummaryStreamClient =
    if (previewConfig.enabled)
      new MilestoneAiSummaryStreamClientPreview(
        previewModule.context,
        previewModule.projects,
        previewModule.milestones,
        previewModule.taskStats
      )
    else new MilestoneAiSummaryStreamClientImpl(loginRepo, "http://localhost:8081/")
  lazy val milestoneSummaryService: MilestoneSummaryFeService =
    if (previewConfig.enabled)
      new MilestoneSummaryFeServicePreview(
        previewModule.context,
        previewModule.projects,
        previewModule.milestones,
        milestoneAiSummaryStreamClient
      )
    else new MilestoneSummaryFeServiceImpl(securedEndpoint, protectedBackendClient, milestoneAiSummaryStreamClient)
  lazy val milestoneDateService: MilestoneDateFeService =
    if (previewConfig.enabled) new MilestoneDateFeServicePreview(previewModule.context, previewModule.milestones)
    else new MilestoneDateFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val dateRangeValidation = new DateRangeValidation

  lazy val accountService = new AccountServiceImpl(loginRepo)
  lazy val changePasswordService: ChangePasswordService =
    if (previewConfig.enabled) new ChangePasswordServicePreview(previewModule.accounts, loginRepo)
    else new ChangePasswordServiceImpl(baseEndpoint, securedEndpoint, protectedBackendClient)
  lazy val changeAccountInfoService: ChangeAccountInfoService =
    if (previewConfig.enabled) new ChangeAccountInfoServicePreview(previewModule.accounts, loginRepo)
    else new ChangeAccountInfoServiceImpl(baseEndpoint, securedEndpoint, protectedBackendClient, loginRepo)

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

  lazy val findAccountServiceImpl: FindAccountService =
    if (previewConfig.enabled) new FindAccountServicePreview(previewModule.context, previewModule.accounts)
    else new FindAccountServiceImpl(securedEndpoint, protectedBackendClient)
//  lazy val findAccounts = new FindAccountsComponent(findAccountServiceImpl)

  lazy val projectService: ProjectService =
    if (previewConfig.enabled)
      new ProjectServicePreview(
        previewModule.context,
        previewModule.accounts,
        previewModule.organizations,
        previewModule.projects,
        previewModule.projectLabels.labelsOf
      )
    else new ProjectServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val organizationService: OrganizationServiceFe =
    if (previewConfig.enabled)
      new OrganizationServiceFePreview(
        previewModule.context,
        previewModule.accounts,
        previewModule.organizations,
        previewModule.projects
      )
    else new OrganizationServiceFeImpl(securedEndpoint, protectedBackendClient)
  lazy val organizationProjectsLoader = new OrganizationProjectsLoader(organizationService, projectService)
  lazy val dataExportConfigService: DataExportConfigFeService =
    if (previewConfig.enabled)
      new DataExportConfigFeServicePreview(previewModule.context, previewModule.projects, previewModule.dataSync)
    else new DataExportConfigFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val dataImportConfigService: DataImportConfigFeService =
    if (previewConfig.enabled)
      new DataImportConfigFeServicePreview(previewModule.context, previewModule.projects, previewModule.dataSync)
    else new DataImportConfigFeServiceImpl(securedEndpoint, protectedBackendClient)
  lazy val projectMappingService: ProjectMappingFeService =
    if (previewConfig.enabled)
      new ProjectMappingFeServicePreview(previewModule.context, previewModule.projects, previewModule.dataSync)
    else new ProjectMappingFeServiceImpl(securedEndpoint, protectedBackendClient)

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
              case LoginPage => new LoginComponent(router, loginService, spinnerService, modalFactory).element
              case ChangePasswordPage =>
                new ChangePasswordComponent(changePasswordService, accountService, spinnerService, modalFactory).element
              case ChangeAccountInfoPage =>
                new ChangeAccountInfoComponent(changeAccountInfoService, accountService, spinnerService, modalFactory).element
              case SyncCheckSettingsPage => new SyncCheckSettingsComponent(syncCheckSettingsStore).element
              case McpSettingsPage => new McpSettingsComponent().element
              case UpdaterSettingsPage => new UpdaterComponent().element
              case ShowAllAccountsPage(listParams) =>
                new ShowAllAccountsComponent(
                  accountManagementService,
                  router,
                  spinnerService,
                  modalFactory,
                  listParams,
                  toast
                ).element
              case EditAccountPage(accountId) =>
                new EditAccountComponent(accountManagementService, router, spinnerService, accountId, toast).element
              case ApexChartDemoPage => new ApexChartDemoComponent(themeStore.isDark).element
              case CreateTaskPage(projectId: UUID) =>
                new CreateTaskComponent(
                  taskService,
                  taskStatusService,
                  taskLabelService,
                  taskTemplateService,
                  milestoneService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectService,
                  projectId,
                  router,
                  toast
                ).element
              case CreateTaskStatusPage(projectId: UUID) =>
                new CreateTaskStatusComponent(
                  taskStatusService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectId,
                  localStorage,
                  toast
                ).element
              case ShowAllTaskStatusesPage(projectId, listParams) =>
                new ShowAllTaskStatusesComponent(
                  taskStatusService,
                  router,
                  spinnerService,
                  accountService,
                  projectId,
                  listParams,
                  toast,
                  modalFactory
                ).element
              case EditTaskStatusPage(projectId: UUID, taskStatusId: UUID) =>
                new EditTaskStatusComponent(
                  taskStatusService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectId,
                  taskStatusId,
                  localStorage,
                  toast
                ).element
              case CreateTaskLabelPage(projectId: UUID) =>
                new CreateTaskLabelComponent(
                  taskLabelService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectId,
                  localStorage,
                  toast
                ).element
              case ShowAllTaskLabelsPage(projectId, listParams) =>
                new ShowAllTaskLabelsComponent(
                  taskLabelService,
                  router,
                  spinnerService,
                  accountService,
                  projectId,
                  listParams,
                  toast
                ).element
              case EditTaskLabelPage(projectId: UUID, taskLabelId: UUID) =>
                new EditTaskLabelComponent(
                  taskLabelService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectId,
                  taskLabelId,
                  localStorage,
                  toast
                ).element
              case CreateTaskTemplatePage(projectId: UUID) =>
                new CreateTaskTemplateComponent(
                  taskTemplateService,
                  taskLabelService,
                  spinnerService,
                  tgClock,
                  accountService,
                  projectId,
                  toast
                ).element
              case ShowAllTaskTemplatesPage(projectId, listParams) =>
                new ShowAllTaskTemplatesComponent(
                  taskTemplateService,
                  router,
                  spinnerService,
                  accountService,
                  tgDateFormater,
                  modalFactory,
                  projectId,
                  listParams,
                  toast
                ).element
              case EditTaskTemplatePage(projectId: UUID, taskTemplateId: UUID) =>
                new EditTaskTemplateComponent(
                  taskTemplateService,
                  taskLabelService,
                  spinnerService,
                  tgMarkdown,
                  tgDateFormater,
                  tgClock,
                  accountService,
                  projectId,
                  taskTemplateId,
                  toast
                ).element
              case SyncConfigPage(projectId: UUID, listParams) =>
                new SyncConfigComponent(
                  dataExportConfigService,
                  dataExportService,
                  dataImportConfigService,
                  tgDateFormater,
                  router,
                  modalFactory,
                  spinnerService,
                  toast,
                  projectId,
                  listParams
                ).element
              case DataExportStatusPage(projectId: UUID) =>
                new DataExportStatusComponent(
                  dataExportService,
                  spinnerService,
                  toast,
                  projectId
                ).element
              case CreateDataExportConfigPage(projectId: UUID) =>
                new CreateDataExportConfigComponent(
                  dataExportConfigService,
                  spinnerService,
                  projectId,
                  toast
                ).element
              case EditDataExportConfigPage(_, id: UUID) =>
                new EditDataExportConfigComponent(
                  id,
                  dataExportConfigService,
                  router,
                  spinnerService,
                  modalFactory,
                  toast
                ).element
              case CreateDataImportConfigPage(projectId: UUID) =>
                new CreateDataImportConfigComponent(
                  dataImportConfigService,
                  spinnerService,
                  projectId,
                  toast
                ).element
              case EditDataImportConfigPage(_, id: UUID) =>
                new EditDataImportConfigComponent(
                  id,
                  dataImportConfigService,
                  router,
                  spinnerService,
                  modalFactory,
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
                  tgMarkdown,
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
                  toast,
                  tgMarkdown,
                  (scope, onClose, onSubmitted) =>
                    new SlackerComponent(
                      taskService,
                      taskProjectDataLoader,
                      milestoneService,
                      spinnerService,
                      accountService,
                      tgClock,
                      tgDateFormater,
                      tgMarkdown,
                      Some(scope.projectId),
                      localStorage,
                      toast,
                      onClose,
                      scope = Some(scope),
                      onSubmitted = onSubmitted
                    ).element,
                  modalFactory
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
                  sidebarRefresh,
                  toast
                ).element
              case ShowProjectsForOrganizationIdTable(organizationId, listParams) =>
                new ShowAllProjectsTableComponent(
                  projectService,
                  organizationService,
                  router,
                  spinnerService,
                  accountService,
                  dataExportService,
                  modalFactory,
                  sidebarRefresh,
                  tgMarkdown,
                  organizationId,
                  listParams,
                  toast
                ).element

              case CreateOrganizationPage =>
                new CreateOrganizationComponent(
                  findAccountServiceImpl,
                  organizationService,
                  accountService,
                  spinnerService,
                  localStorage,
                  sidebarRefresh,
                  toast
                ).element
              case ShowAllOrganizationsPage(listParams) =>
                new ShowAllOrganizationsComponent(
                  organizationService,
                  router,
                  spinnerService,
                  accountService,
                  listParams,
                  toast,
                  tgMarkdown
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
                  sidebarRefresh,
                  modalFactory,
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
              case CreateTgPromptTemplatePage =>
                new CreateTgPromptTemplateComponent(tgPromptTemplateService, spinnerService, toast).element
              case ShowAllTgPromptTemplatesPage(listParams) =>
                new ShowAllTgPromptTemplatesComponent(
                  tgPromptTemplateService,
                  router,
                  spinnerService,
                  tgDateFormater,
                  modalFactory,
                  listParams,
                  toast
                ).element
              case EditTgPromptTemplatePage(tgPromptTemplateId) =>
                new EditTgPromptTemplateComponent(
                  tgPromptTemplateService,
                  spinnerService,
                  tgMarkdown,
                  tgDateFormater,
                  modalFactory,
                  tgPromptTemplateId,
                  toast
                ).element
              case CreateTgAiActionPage =>
                new CreateTgAiActionComponent(tgAiActionService, tgPromptTemplateService, spinnerService, toast).element
              case ShowAllTgAiActionsPage(listParams) =>
                new ShowAllTgAiActionsComponent(
                  tgAiActionService,
                  router,
                  spinnerService,
                  tgDateFormater,
                  modalFactory,
                  listParams,
                  toast
                ).element
              case EditTgAiActionPage(tgAiActionId) =>
                new EditTgAiActionComponent(
                  tgAiActionService,
                  tgPromptTemplateService,
                  router,
                  spinnerService,
                  modalFactory,
                  tgAiActionId,
                  toast
                ).element
              case TgAiActionDefaultsPage =>
                new TgAiActionDefaultsComponent(
                  tgAiActionService,
                  organizationProjectsLoader,
                  accountService,
                  spinnerService,
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
                  listParams,
                  toast
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
                  sidebarRefresh,
                  projectId,
                  toast,
                  modalFactory
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
                  tgClock,
                  projectService,
                  projectId,
                  toast
                ).element
              case EditMilestonePage(_, id: UUID) =>
                new EditMilestoneComponent(
                  milestoneService,
                  milestoneDateService,
                  spinnerService,
                  dateRangeValidation,
                  tgClock,
                  projectService,
                  id,
                  toast
                ).element
              case MilestoneSummaryPage(_, id: UUID) =>
                new MilestoneSummaryComponent(
                  milestoneSummaryService,
                  tgAiActionService,
                  milestoneService,
                  taskService,
                  projectService,
                  router,
                  spinnerService,
                  tgDateFormater,
                  tgMarkdown,
                  tgClock,
                  id,
                  toast,
                  modalFactory
                ).element
              case MilestoneReviewPage(projectId: UUID, milestoneId: UUID) =>
                new MilestoneReviewComponent(
                  milestoneService,
                  milestoneDateService,
                  milestoneSummaryService,
                  taskService,
                  taskStatusService,
                  taskLabelService,
                  projectService,
                  router,
                  spinnerService,
                  tgDateFormater,
                  tgMarkdown,
                  tgClock,
                  projectId,
                  milestoneId,
                  toast,
                  modalFactory
                ).element
              case ProjectHomePage(projectId) =>
                new ProjectHomeComponent(
                  projectService,
                  milestoneService,
                  milestoneSummaryService,
                  timeboxService,
                  taskService,
                  taskStatusService,
                  router,
                  spinnerService,
                  tgDateFormater,
                  tgMarkdown,
                  tgClock,
                  modalFactory,
                  projectId
                ).element
              case ShowMilestonesPage(projectId: UUID, listParams) =>
                new ShowMilestonesComponent(
                  milestoneService,
                  projectService,
                  router,
                  spinnerService,
                  tgDateFormater,
                  tgMarkdown,
                  projectId,
                  listParams,
                  toast
                ).element
              case ShowMilestoneHistoryPage(projectId: UUID, historyParams) =>
                new MilestoneHistoryComponent(
                  taskService,
                  milestoneService,
                  milestoneDateService,
                  projectService,
                  tgMarkdown,
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
                  tgMarkdown,
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
  dataExportService: DataExportFeService,
  dataImportConfigService: DataImportConfigFeService,
  spinnerService: SpinnerService,
  modalFactory: ModalFactory,
  sidebarCollapseStore: SidebarCollapseStore,
  sidebarRefresh: SidebarRefresh,
  themeStore: ThemeStore,
  syncPendingStore: SyncPendingStore,
  onQuickCreate: () => Unit,
  onBulkCreate: () => Unit,
  onTeamDaily: () => Unit,
  onSlacker: () => Unit,
  toast: Toast,
  previewConfig: PreviewConfig,
  onResetPreview: () => Unit
)(implicit IORuntime: IORuntime) {

  lazy val navigationSignal: Signal[ReactiveHtmlElement[HTMLDivElement]] = {
    loginRepo.authenticatedSignal.map(returnedAuthAccount =>
      returnedAuthAccount
        .map(_ =>
          {
            val sidebar = new Sidebar(
              router,
              organizationProjectsLoader,
              accountService,
              dataExportService,
              dataImportConfigService,
              spinnerService,
              modalFactory,
              sidebarCollapseStore,
              sidebarRefresh,
              syncPendingStore,
              onQuickCreate,
              onBulkCreate,
              onTeamDaily,
              onSlacker,
              toast
            )
            div(
              new NavbarComponent(router, Logout(loginRepo, router), loginRepo, themeStore, sidebar.organizations, sidebarCollapseStore.current, sidebar.exportProject, sidebar.importProject, syncPendingStore, previewConfig, onResetPreview).element,
              sidebar.element
            )
          }
        )
        .getOrElse(div())
    )
  }

}
