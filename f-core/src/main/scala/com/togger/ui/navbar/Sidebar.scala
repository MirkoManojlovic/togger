package com.togger.ui.navbar
import cats.effect.unsafe.IORuntime
import com.togger.dto.organization.FindOrganizationsAndProjects
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.AccountService
import com.togger.ui.navbar.Sidebar.sideBarLinkStyle
import com.togger.ui.organization.{OrganizationServiceFe, TgIcons}
import com.togger.ui.task.{DumpComponent, LoadComponent}
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLLIElement

case class SidebarLink(
  linkName: String,
  link: Binder[HtmlElement],
  isSelected: Boolean = false
) {

  def selected: ReactiveHtmlElement[HTMLLIElement] =
    li(linkElement.amend(cls(Sidebar.sidebarSelectedLink)))

  private def linkElement =
    a(
      linkName,
      link
    )

  private def linkElementProject =
    a(
      cls("inline-flex items-center w-100"),
      TgIcons.briefcaseIcon("w-4 h-4 me-1.5 -ms-0.5"),
      linkName,
      link
    )

  private def linkElementOrg =
    a(
      cls("inline-flex items-center w-100"),
      TgIcons.userGroupIcon("w-4 h-4 me-1.5 -ms-0.5"),
      linkName,
      link
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
  dumpComponent: DumpComponent,
  loadComponent: LoadComponent
)(implicit IORuntime: IORuntime) {

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

  private val taskMenuLink = SidebarLink("Tasks", router.navigateTo(ShowAllTasksPage(-1L)))
  private val projectMenuLink = SidebarLink("Projects", router.navigateTo(CreateProjectPage(0)))
  private val organizationMenuLink = SidebarLink("Organizations", router.navigateTo(ShowAllOrganizationsPage))
  private val passwordMenuLink = SidebarLink("Change Password", router.navigateTo(ChangePasswordPage))
  private val logoutElement = li(logout.logoutButton.amend(cls(Sidebar.sideBarLinkStyle)))

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
        cls("fixed top-0 left-0 z-40 w-64 h-full transition-transform -translate-x-full sm:translate-x-0"),
//        aria-label="Sidebar"

        div(
          cls("h-full px-3 py-4 overflow-y-auto bg-neutral-primary-soft border-e border-default"),
          a(
            cls("flex items-center ps-2.5 mb-5"),
            img(src("/tg-icon-1.svg"), cls("h-7"), cls("h-6 me-3"), alt("Togger Logo")),
            span(
              cls("self-center text-lg text-heading font-semibold whitespace-nowrap"),
              "Togger"
            ),
            router.navigateTo(HomePage)
          ),
          ul(
            cls("space-y-2 font-medium"),
            children <-- router.currentPageSignal.map(page =>
              SidebarItems.allDeselected.selectItem(page).asHtmlElementList
            ),
            children <-- organizationState.signal.map { organizations =>
              organizations.sortBy(_.name).flatMap { organization =>
                SidebarLink(
                  organization.name,
                  router.navigateTo(ShowProjectsForOrganizationIdStatus(organization.id))
                ).deselectedOrg.amend(cls("border-b-1 border-gray-200")) +:
                  organization.projects
                    .sortBy(_.name)
                    .map(project =>
                      SidebarLink(project.name, router.navigateTo(ShowAllTasksPage(project.id))).deselectedProject
                    )
              }
            },
            li(dumpComponent.asLink.amend(cls(Sidebar.sideBarLinkStyle))),
            li(loadComponent.asLink.amend(cls(Sidebar.sideBarLinkStyle)))

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
