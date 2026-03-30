package com.togger.ui.navbar
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLLIElement

case class MenuLink(
  linkName: String,
  link: Binder[HtmlElement],
  isSelected: Boolean = false
) {

  def selected: ReactiveHtmlElement[HTMLLIElement] =
    li(linkElement.amend(cls(NavbarComponent.selectedNavLinkClass)))

  private def linkElement =
    a(
      linkName,
      link
    )

  def deselected: ReactiveHtmlElement[HTMLLIElement] =
    li(linkElement.amend(cls(NavbarComponent.navLinkClass)))

}

class NavbarComponent(router: TgRouter[FrontendPage], logout: Logout[FrontendPage]) {

  private val mobileNavShow: Var[Boolean] = Var(false)

  private val taskMenuLink = MenuLink("Tasks", router.navigateTo(ShowAllTasksPage(-1L)))
  private val projectMenuLink = MenuLink("Projects", router.navigateTo(CreateProjectPage(0)))
  private val organizationMenuLink = MenuLink("Organizations", router.navigateTo(ShowAllOrganizationsPage))
  private val passwordMenuLink = MenuLink("Change Password", router.navigateTo(ChangePasswordPage))
  private val logoutElement = li(logout.logoutButton.amend(cls(NavbarComponent.navLinkClass)))

  case class NavbarItems(
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

    def selectItem(page: FrontendPage): NavbarItems = {
      page match {
        case ChangePasswordPage => NavbarItems.changePasswordSelected
        case CreateTaskPage(_) | ShowAllTasksPage(_) | EditTaskPage(_, _) | HomePage | CreateTaskStatusPage(_) =>
          NavbarItems.taskSelected
        case ShowProjectsForOrganizationIdTable(_) | CreateProjectPage(_) => NavbarItems.projectSelected
        case CreateOrganizationPage | ShowAllOrganizationsPage | EditOrganizationPage(_) =>
          NavbarItems.organizationSelected
        case _ => NavbarItems.taskSelected
      }
    }
  }

  object NavbarItems {
    lazy val allDeselected: NavbarItems =
      NavbarItems(
        task = taskMenuLink.deselected,
        project = projectMenuLink.deselected,
        organization = organizationMenuLink.deselected,
        changePassword = passwordMenuLink.deselected,
        logout = logoutElement
      )

    lazy val changePasswordSelected: NavbarItems =
      NavbarItems.allDeselected.copy(changePassword = passwordMenuLink.selected)

    lazy val taskSelected: NavbarItems =
      NavbarItems.allDeselected.copy(task = taskMenuLink.selected)

    lazy val projectSelected: NavbarItems =
      NavbarItems.allDeselected.copy(project = projectMenuLink.selected)

    lazy val organizationSelected: NavbarItems =
      NavbarItems.allDeselected.copy(organization = organizationMenuLink.selected)
  }

  val element =
    div(
//      cls("mb-[5rem]"),
      navTag(
//        cls("bg-neutral-primary fixed w-full z-20 top-0 start-0 border-b border-default"),
        cls("bg-neutral-primary w-full z-20 border-b border-default"),
        div(
          cls("max-w-screen-xl flex flex-wrap items-center justify-between mx-auto p-4"),
          a(
            cls("block py-2 px-3 text-heading flex items-center space-x-3 rtl:space-x-reverse"),
            img(src("/tg-icon-1.svg"), cls("h-7")),
//            img(src("https://flowbite.com/docs/images/logo.svg"), cls("h-7")),
            span(cls("self-center text-xl text-heading font-semibold whitespace-nowrap"), "Togger"),
            router.navigateTo(HomePage)
          ),
          button(
            cls(
              "inline-flex items-center p-2 w-10 h-10 justify-center text-sm text-body rounded-base md:hidden hover:bg-neutral-secondary-soft hover:text-heading focus:outline-none focus:ring-2 focus:ring-neutral-tertiary"
            ),
            span(cls("sr-only"), "Open main menu"),
            svg.svg(
              onClick.mapToUnit --> (_ => mobileNavShow.update(value => !value)),
              svg.className("w-6 h-6"),
              svg.width("24"),
              svg.height("24"),
              svg.fill("none"),
              svg.viewBox("0 0 24 24"),
              svg.path(
                svg.stroke("currentColor"),
                svg.strokeLineCap("round"),
                svg.strokeWidth("2"),
                svg.d("M5 7h14M5 12h14M5 17h14")
              )
            )
          ),
          div(
            idAttr("navbar-default"),
            cls <-- mobileNavShow.signal.map(shouldShow => if (shouldShow) Seq.empty else Seq("hidden")),
            cls("w-full md:block md:w-auto"),
            ul(
              cls(
                "font-medium flex flex-col p-4 md:p-0 mt-4 border border-default rounded-base bg-neutral-secondary-soft md:flex-row md:space-x-8 rtl:space-x-reverse md:mt-0 md:border-0 md:bg-neutral-primary"
              ),
              children <-- router.currentPageSignal.map(page =>
                NavbarItems.allDeselected.selectItem(page).asHtmlElementList
              )
            )
          )
        )
      )
    )

}

object NavbarComponent {
  val navLinkClass: String =
    "block py-2 px-3 text-heading rounded hover:bg-neutral-tertiary md:hover:bg-transparent md:border-0 md:hover:text-fg-brand md:p-0 md:dark:hover:bg-transparent"
  val selectedNavLinkClass: String =
    "block py-2 px-3 text-white bg-brand rounded md:bg-transparent md:text-fg-brand md:p-0"
}
