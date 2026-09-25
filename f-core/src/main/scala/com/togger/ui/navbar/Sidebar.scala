package com.togger.ui.navbar
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.dto.TaskLoadResp
import com.togger.dto.organization.FindOrganizationsAndProjects
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.AccountService
import com.togger.ui.icons.TgSvgIcons
import com.togger.ui.navbar.Sidebar.sideBarLinkStyle
import com.togger.ui.organization.{OrganizationServiceFe, TgIcons}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskDumpFeService
import com.togger.ui.taskloadconfig.TaskLoadConfigFeService
import com.togger.ui.toast.Toast
import org.scalajs.dom.HTMLLIElement

import java.util.UUID

case class SidebarLink(
  linkName: String,
  link: Binder[HtmlElement],
  collapsed: Signal[Boolean],
  isSelected: Boolean = false,
  onDumpClick: Option[() => Unit] = None,
  onConfigClick: Option[() => Unit] = None,
  onLoadClick: Option[() => Unit] = None,
  icon: String = ""
) {

  private def initials: String = linkName.trim.headOption.map(_.toUpper.toString).getOrElse("")

  private def glyph: String = if (icon.nonEmpty) icon else initials

  def selected: ReactiveHtmlElement[HTMLLIElement] =
    li(linkElement.amend(cls(Sidebar.sidebarSelectedLink)))

  private def linkElement =
    a(
      cls("flex items-center"),
      title(linkName),
      span(
        cls("w-full text-center"),
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq.empty else Seq("hidden")),
        initials
      ),
      span(
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("hidden") else Seq.empty),
        linkName
      ),
      link
    )

  private def linkElementProject =
    div(
      cls("flex items-center justify-between"),
      a(
        cls("inline-flex items-center"),
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("justify-center", "w-full") else Seq.empty),
        title(linkName),
        span(cls("me-1.5 -ms-0.5"), child.text <-- collapsed.map(isCollapsed => if (isCollapsed) glyph else icon)),
        span(
          cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("hidden") else Seq.empty),
          linkName
        ),
        link
      ),
      div(
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("hidden") else Seq.empty),
        onDumpClick.map(dumpAction =>
          button(
            `type`("button"),
            TgIcons.downloadIcon("w-3.5 h-3.5"),
            cls(
              "inline-flex items-center justify-center w-5 h-5 text-body bg-transparent border border-default hover:bg-neutral-tertiary hover:text-fg-brand focus:ring-2 focus:ring-neutral-tertiary rounded-base focus:outline-none cursor-pointer"
            ),
            onClick.mapToUnit --> (_ => dumpAction())
          )
        ),
        onLoadClick.map(loadAction =>
          button(
            `type`("button"),
            TgIcons.uploadIcon("w-3.5 h-3.5"),
            cls(
              "inline-flex items-center justify-center w-5 h-5 text-body bg-transparent border border-default hover:bg-neutral-tertiary hover:text-fg-brand focus:ring-2 focus:ring-neutral-tertiary rounded-base focus:outline-none cursor-pointer ml-1"
            ),
            onClick.mapToUnit --> (_ => loadAction())
          )
        ),
        onConfigClick.map(configAction =>
          button(
            `type`("button"),
            TgIcons.editIcon("w-3.5 h-3.5"),
            cls(
              "inline-flex items-center justify-center w-5 h-5 text-body bg-transparent border border-default hover:bg-neutral-tertiary hover:text-fg-brand focus:ring-2 focus:ring-neutral-tertiary rounded-base focus:outline-none cursor-pointer ml-1"
            ),
            onClick.mapToUnit --> (_ => configAction())
          )
        )
      )
    )

  private def linkElementOrg =
    div(
      cls("flex items-center"),
      a(
        cls("inline-flex items-center"),
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("justify-center", "w-full") else Seq.empty),
        title(linkName),
        span(cls("me-1.5 -ms-0.5"), child.text <-- collapsed.map(isCollapsed => if (isCollapsed) glyph else icon)),
        span(
          cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("hidden") else Seq.empty),
          linkName
        ),
        link
      )
    )

  def deselected: ReactiveHtmlElement[HTMLLIElement] =
    li(linkElement.amend(cls(Sidebar.sideBarLinkStyle)))

  def deselectedProject: ReactiveHtmlElement[HTMLLIElement] =
    li(
      cls("pl-2"),
      linkElementProject.amend(cls(Sidebar.sideBarLinkStyle))
    )

  def deselectedOrg: ReactiveHtmlElement[HTMLLIElement] =
    li(
      linkElementOrg.amend(cls(Sidebar.sideBarLinkStyle))
    )

}

class Sidebar(
  router: TgRouter[FrontendPage],
  logout: Logout[FrontendPage],
  organizationServiceFe: OrganizationServiceFe,
  accountService: AccountService,
  taskDumpService: TaskDumpFeService,
  taskLoadConfigService: TaskLoadConfigFeService,
  spinnerService: SpinnerService,
  sidebarCollapseStore: SidebarCollapseStore,
  val toast: Toast
)(implicit IORuntime: IORuntime)
    extends ToastMessages {

  private val collapsed: Signal[Boolean] = sidebarCollapseStore.current

  private def dumpProject(projectId: UUID): Unit =
    spinnerService
      .runWithSpinner(taskDumpService.dumpTasks(projectId))
      .toastMessageDto(dto =>
        s"Tasks dumped to ${dto.results.count(_.success)}/${dto.results.size} configured path(s)"
      )(_ => IO.unit)
      .unsafeRunAndForget()

  private def loadAllForProject(projectId: UUID): Unit = {
    val program: IO[Unit] = taskLoadConfigService.findByProjectId(projectId).flatMap {
      _.fold(
        error => IO(toast.error(error)),
        success => {
          val paths = success.body.get.map(_.dumpPath).distinct.toList
          if (paths.isEmpty) IO(toast.error("No load paths configured for this project"))
          else
            paths
              .traverse(path => taskDumpService.loadTasks(projectId, path).map(_.map(_.body.get)))
              .map { (outcomes: List[Either[com.togger.common.TgJsonFailure, TaskLoadResp]]) =>
                val successes = outcomes.collect { case Right(dto) => dto }
                outcomes.collect { case Left(failure) => failure }.foreach(toast.error)
                val totalTasks = successes.map(_.taskCount).sum
                val failedCount = outcomes.count(_.isLeft)
                if (failedCount == 0) toast.success(s"Loaded from ${successes.size} path(s), $totalTasks task(s) total")
                else toast.error(s"Loaded from ${successes.size}/${outcomes.size} path(s); $failedCount failed")
              }
        }
      )
    }
    spinnerService.runWithSpinner(program).unsafeRunAndForget()
  }

  private val organizationState: Var[Seq[FindOrganizationsAndProjects]] = Var(Seq.empty)

  organizationServiceFe
    .findOrgAndProjectsByAccountId(accountService.get.get.id)
    .map(
      _.fold(
        error => println(error),
        success => {
          val dto = success.body.get
          organizationState.set(dto)

        }
      )
    )
    .unsafeRunAndForget()

  private val taskMenuLink =
    SidebarLink("Tasks", router.navigateTo(ShowAllTasksPage(UUID.randomUUID())), collapsed)
  private val projectMenuLink =
    SidebarLink("Projects", router.navigateTo(CreateProjectPage(UUID.randomUUID())), collapsed)
  private val organizationMenuLink =
    SidebarLink("Organizations", router.navigateTo(ShowAllOrganizationsPage), collapsed)
  private val passwordMenuLink =
    SidebarLink("Change Password", router.navigateTo(ChangePasswordPage), collapsed)
  private val logoutElement = li(
    logout
      .logoutButtonWithLabel(collapsed.map(isCollapsed => if (isCollapsed) "L" else "Log out"))
      .amend(cls(Sidebar.sideBarLinkStyle), cls("flex items-center"), title("Log out"))
  )

  case class SidebarItems(
    task: ReactiveHtmlElement[HTMLLIElement],
    project: ReactiveHtmlElement[HTMLLIElement],
    organization: ReactiveHtmlElement[HTMLLIElement],
    changePassword: ReactiveHtmlElement[HTMLLIElement],
    logout: ReactiveHtmlElement[HTMLLIElement]
  ) {

    lazy val asHtmlElementList: Seq[ReactiveHtmlElement[HTMLLIElement]] =
      Seq(
        organization,
        //        project,
        //        task,
        changePassword,
        logout
      )

    def selectItem(page: FrontendPage): SidebarItems = {
      page match {
        case ChangePasswordPage => SidebarItems.changePasswordSelected
        case CreateTaskPage(_) | ShowAllTasksPage(_) | EditTaskPage(_, _) | HomePage | CreateTaskStatusPage(_) =>
          SidebarItems.taskSelected
        case ShowProjectsForOrganizationIdTable(_) | CreateProjectPage(_) => SidebarItems.projectSelected
        case CreateOrganizationPage | ShowAllOrganizationsPage | EditOrganizationPage(_) =>
          SidebarItems.organizationSelected
        case _ => SidebarItems.taskSelected
      }
    }
  }

  object SidebarItems {
    lazy val allDeselected: SidebarItems =
      SidebarItems(
        task = taskMenuLink.deselected,
        project = projectMenuLink.deselected,
        organization = organizationMenuLink.deselected,
        changePassword = passwordMenuLink.deselected,
        logout = logoutElement
      )

    lazy val changePasswordSelected: SidebarItems =
      SidebarItems.allDeselected.copy(changePassword = passwordMenuLink.selected)

    lazy val taskSelected: SidebarItems =
      SidebarItems.allDeselected.copy(task = taskMenuLink.selected)

    lazy val projectSelected: SidebarItems =
      SidebarItems.allDeselected.copy(project = projectMenuLink.selected)

    lazy val organizationSelected: SidebarItems =
      SidebarItems.allDeselected.copy(organization = organizationMenuLink.selected)
  }

  val element = {
    div(
      "Sidebar",
      button(
//        data-drawer-target="logo-sidebar"
//        data-drawer-toggle="logo-sidebar"
//          aria-controls="logo-sidebar"
        `type`("button"),
        cls(
          "text-heading bg-transparent box-border border border-transparent hover:bg-neutral-secondary-medium focus:ring-4 focus:ring-neutral-tertiary font-medium leading-5 rounded-base ms-3 mt-3 text-sm p-2 focus:outline-none inline-flex sm:hidden"
        ),
        svg.svg(
//          onClick.mapToUnit --> (_ => mobileNavShow.update(value => !value)),
//          aria-hidden="true"
//          xmlns="http://www.w3.org/2000/svg"
          svg.className("w-6 h-6"),
          svg.width("24"),
          svg.height("24"),
          svg.fill("none"),
          svg.viewBox("0 0 24 24"),
          svg.path(
            svg.stroke("currentColor"),
            svg.strokeLineCap("round"),
            svg.strokeWidth("2"),
            svg.d("M5 7h14M5 12h14M5 17h10")
          )
        )
      ), // btn end

      asideTag(
        idAttr("logo-sidebar"),
        cls("fixed top-0 left-0 z-40 h-full transition-[width] duration-200 -translate-x-full sm:translate-x-0"),
        cls <-- collapsed.map(isCollapsed => if (isCollapsed) "w-16" else "w-64"),
//        aria-label="Sidebar"

        div(
          cls("h-full px-3 py-4 overflow-y-auto bg-neutral-primary-soft border-e border-default"),
          div(
            cls("flex items-center justify-between mb-5"),
            cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("flex-col", "gap-2") else Seq.empty),
            a(
              cls("flex items-center ps-2.5"),
              cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("ps-0") else Seq.empty),
              div(
                cls("flex items-center me-3"),
                cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("me-0") else Seq.empty),
                TgSvgIcons.tgIconLogo
              ),
              span(
                cls("self-center text-lg text-heading font-semibold whitespace-nowrap"),
                cls <-- collapsed.map(isCollapsed => if (isCollapsed) Seq("hidden") else Seq.empty),
                "Togger"
              ),
              router.navigateTo(HomePage)
            ),
            button(
              `type`("button"),
              title <-- collapsed.map(isCollapsed => if (isCollapsed) "Expand sidebar" else "Collapse sidebar"),
              cls(
                "inline-flex items-center justify-center w-6 h-6 text-body bg-transparent hover:bg-neutral-tertiary hover:text-fg-brand rounded-base focus:outline-none cursor-pointer"
              ),
              child <-- collapsed.map(isCollapsed =>
                if (isCollapsed) TgIcons.chevronRightIcon("w-4 h-4") else TgIcons.chevronLeftIcon("w-4 h-4")
              ),
              onClick.mapToUnit --> (_ => sidebarCollapseStore.toggle())
            )
          ),
          ul(
            cls("space-y-2 font-medium"),
            children <-- router.currentPageSignal.map(page =>
              SidebarItems.allDeselected.selectItem(page).asHtmlElementList
            ),
            children <-- organizationState.signal.map { organizations =>
              organizations.sortBy(_.name).flatMap { organization =>
                SidebarLink(
                  linkName = organization.name,
                  link = router.navigateTo(ShowProjectsForOrganizationIdStatus(organization.id)),
                  collapsed = collapsed,
                  icon = organization.icon
                ).deselectedOrg.amend(cls("border-b-1 border-gray-200")) +:
                  organization.projects
                    .sortBy(_.name)
                    .map(project =>
                      SidebarLink(
                        linkName = project.name,
                        link = router.navigateTo(ShowAllTasksPage(project.id)),
                        collapsed = collapsed,
                        onDumpClick = Some(() => dumpProject(project.id)),
                        onConfigClick = Some(() => router.route(ShowAllTaskConfigsPage(project.id))),
                        onLoadClick = Some(() => loadAllForProject(project.id)),
                        icon = project.icon
                      ).deselectedProject
                    )
              }
            }
//            li(
//              a(
//                cls(sideBarLinkStyle),
//                span(cls("ms-3"), "Dashboard"),
//                router.navigateTo(HomePage)
//              )
//            ),
//            createSidebarLink("Change Password", router.navigateTo(ChangePasswordPage))
          )
        )
      )
    )
  }

  def createSidebarLink(
    linkName: String,
    link: Binder[HtmlElement],
    isSelected: Boolean = true
  ) = {
    li(
      a(
        cls(sideBarLinkStyle),
        span(cls("ms-3"), linkName),
        link
      )
    )
  }
}

object Sidebar {
  val sideBarLinkStyle =
    "flex items-center px-2 py-1.5 text-body rounded-base hover:bg-neutral-tertiary hover:text-fg-brand group"

  val sidebarSelectedLink =
    "flex items-center px-2 py-1.5 text-body rounded-base bg-neutral-tertiary text-fg-brand group"
}
