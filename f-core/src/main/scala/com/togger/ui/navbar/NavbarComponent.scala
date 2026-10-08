package com.togger.ui.navbar
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.auth.AuthAccountFe
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.LoginRepo
import com.togger.ui.icons.TgSvgIcons
import com.togger.ui.organization.TgIcons
import com.togger.ui.theme.ThemeStore
import org.scalajs.dom
import org.scalajs.dom.HTMLLIElement

case class MenuLink(
  linkName: String,
  link: Binder[HtmlElement],
  linkClass: String = NavbarComponent.navLinkClass,
  selectedClass: String = NavbarComponent.selectedNavLinkClass,
  onNavigate: () => Unit = () => ()
) {

  def selected: ReactiveHtmlElement[HTMLLIElement] =
    li(linkElement.amend(cls(selectedClass)))

  private def linkElement =
    a(
      linkName,
      link,
      onClick.mapToUnit --> (_ => onNavigate())
    )

  def deselected: ReactiveHtmlElement[HTMLLIElement] =
    li(linkElement.amend(cls(linkClass)))

}

class NavbarComponent(
  router: TgRouter[FrontendPage],
  logout: Logout[FrontendPage],
  loginRepo: LoginRepo,
  themeStore: ThemeStore
) {

  private lazy val themeToggleButton = new ThemeToggleButton(themeStore)

  private val userMenuOpen: Var[Boolean] = Var(false)

  private val closeUserMenu = Observer[Unit](_ => userMenuOpen.set(false))

  private val accountSignal: Signal[Option[AuthAccountFe]] = loginRepo.authenticatedSignal

  private val initialsSignal: Signal[Option[String]] =
    accountSignal.map(_.flatMap { account =>
      val letters =
        account.firstName.flatMap(_.trim.headOption).toSeq ++ account.lastName.flatMap(_.trim.headOption).toSeq
      Option.when(letters.nonEmpty)(letters.map(_.toUpper).mkString)
    })

  private val organizationMenuLink = MenuLink("Organizations", router.navigateTo(ShowAllOrganizationsPage()))

  private val searchProjectsMenuLink = MenuLink("Search Projects", router.navigateTo(SearchProjectsByLabelPage))

  private val settingsMenuLink = MenuLink("Settings", router.navigateTo(ShowAllProjectLabelsPage))

  private val passwordMenuLink =
    MenuLink(
      "Change Password",
      router.navigateTo(ChangePasswordPage),
      linkClass = NavbarComponent.dropdownItemClass,
      selectedClass = NavbarComponent.dropdownItemSelectedClass,
      onNavigate = () => userMenuOpen.set(false)
    )

  private val logoutElement =
    li(
      logout.logoutButton.amend(
        cls(NavbarComponent.dropdownItemClass),
        onClick.mapToUnit --> (_ => userMenuOpen.set(false))
      )
    )

  case class NavbarItems(
    organization: ReactiveHtmlElement[HTMLLIElement],
    searchProjects: ReactiveHtmlElement[HTMLLIElement],
    settings: ReactiveHtmlElement[HTMLLIElement],
    changePassword: ReactiveHtmlElement[HTMLLIElement],
    logout: ReactiveHtmlElement[HTMLLIElement]
  ) {

    def selectItem(page: FrontendPage): NavbarItems = {
      page match {
        case ChangePasswordPage => NavbarItems.changePasswordSelected
        case CreateOrganizationPage | ShowAllOrganizationsPage(_) | EditOrganizationPage(_) =>
          NavbarItems.organizationSelected
        case SearchProjectsByLabelPage =>
          NavbarItems.searchProjectsSelected
        case CreateProjectLabelPage | ShowAllProjectLabelsPage | EditProjectLabelPage(_) =>
          NavbarItems.settingsSelected
        case _ => NavbarItems.allDeselected
      }
    }
  }

  object NavbarItems {
    lazy val allDeselected: NavbarItems =
      NavbarItems(
        organization = organizationMenuLink.deselected,
        searchProjects = searchProjectsMenuLink.deselected,
        settings = settingsMenuLink.deselected,
        changePassword = passwordMenuLink.deselected,
        logout = logoutElement
      )

    lazy val changePasswordSelected: NavbarItems =
      NavbarItems.allDeselected.copy(changePassword = passwordMenuLink.selected)

    lazy val organizationSelected: NavbarItems =
      NavbarItems.allDeselected.copy(organization = organizationMenuLink.selected)

    lazy val searchProjectsSelected: NavbarItems =
      NavbarItems.allDeselected.copy(searchProjects = searchProjectsMenuLink.selected)

    lazy val settingsSelected: NavbarItems =
      NavbarItems.allDeselected.copy(settings = settingsMenuLink.selected)
  }

  val element =
    div(
      navTag(
        cls("fixed top-0 z-50 w-full bg-neutral-primary-soft border-b border-default"),
        div(
          cls("px-3 lg:px-5 lg:pl-3"),
          div(
            cls("flex items-center justify-between h-14"),
            div(
              cls("flex items-center justify-start rtl:justify-end"),
              mobileSidebarButton,
              a(
                cls("flex items-center ms-2 md:me-24"),
                TgSvgIcons.tgIconLogo,
                span(cls("self-center ms-3 text-lg text-heading font-semibold whitespace-nowrap"), "Togger"),
                router.navigateTo(HomePage)
              )
            ),
            div(
              cls("flex items-center"),
              ul(
                cls("flex items-center font-medium"),
                children <-- router.currentPageSignal.map(page =>
                  Seq(
                    NavbarItems.allDeselected.selectItem(page).organization,
                    NavbarItems.allDeselected.selectItem(page).searchProjects,
                    NavbarItems.allDeselected.selectItem(page).settings
                  )
                )
              ),
              div(cls("flex items-center ms-3 gap-2"), themeToggleButton.element, userMenu)
            )
          )
        )
      )
    )

  /** Relocated from the sidebar unchanged, including its commented out drawer attributes: below `sm` the sidebar is
    * translated off screen and nothing toggles it back, exactly as before.
    */
  private lazy val mobileSidebarButton =
    button(
//        data-drawer-target="logo-sidebar"
//        data-drawer-toggle="logo-sidebar"
//          aria-controls="logo-sidebar"
      `type`("button"),
      cls(
        "sm:hidden text-heading bg-transparent box-border border border-transparent hover:bg-neutral-secondary-medium focus:ring-4 focus:ring-neutral-tertiary font-medium leading-5 rounded-base text-sm p-2 focus:outline-none"
      ),
      span(cls("sr-only"), "Open sidebar"),
      svg.svg(
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
    )

  private lazy val userMenu =
    div(
      cls("relative"),
      avatarButton,
      child <-- userMenuOpen.signal.map(isOpen => if (isOpen) userMenuPopover else emptyNode)
    )

  private lazy val avatarButton =
    button(
      `type`("button"),
      cls(NavbarComponent.avatarButtonClass),
      aria.expanded <-- userMenuOpen.signal,
      title("Open user menu"),
      span(cls("sr-only"), "Open user menu"),
      child <-- initialsSignal.map {
        case Some(initials) => span(initials)
        case None           => span(TgIcons.userIcon("w-4 h-4"))
      },
      onClick.stopPropagation.mapToUnit --> (_ => userMenuOpen.update(isOpen => !isOpen))
    )

  private def userMenuPopover =
    div(
      cls(NavbarComponent.dropdownPanelClass),
      onMountBind { ctx =>
        val rootNode = ctx.thisNode.ref

        documentEvents(_.onClick)
          .filter(event => !rootNode.contains(event.target.asInstanceOf[dom.Node]))
          .mapToUnit --> closeUserMenu
      },
      onMountBind { _ =>
        documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> closeUserMenu
      },
      identityHeader,
      ul(
        cls("p-2 text-sm text-body font-medium"),
        children <-- router.currentPageSignal.map { page =>
          val items = NavbarItems.allDeselected.selectItem(page)
          Seq(items.changePassword, items.logout)
        }
      )
    )

  private def identityHeader =
    child <-- accountSignal.map {
      case Some(account) if account.email.nonEmpty || account.firstName.nonEmpty =>
        div(
          cls("px-4 py-3 border-b border-default-medium"),
          p(cls("text-sm font-medium text-heading"), fullName(account)),
          account.email.map(email => p(cls("text-sm text-body truncate"), email))
        )
      case _ => emptyNode
    }

  private def fullName(account: AuthAccountFe): String =
    Seq(account.firstName, account.lastName).flatten.map(_.trim).filter(_.nonEmpty).mkString(" ")

}

object NavbarComponent {
  val navLinkClass: String =
    "block px-3 py-2 text-body rounded-base hover:bg-neutral-tertiary hover:text-fg-brand"
  val selectedNavLinkClass: String =
    "block px-3 py-2 text-fg-brand bg-neutral-tertiary rounded-base"
  val dropdownItemClass: String =
    "inline-flex items-center w-full p-2 text-body rounded-base hover:bg-neutral-tertiary-medium hover:text-heading cursor-pointer"
  val dropdownItemSelectedClass: String =
    "inline-flex items-center w-full p-2 text-fg-brand bg-neutral-tertiary rounded-base"
  val avatarButtonClass: String =
    "flex items-center justify-center w-8 h-8 rounded-full border border-default bg-neutral-secondary-medium " +
      "text-sm font-medium text-heading hover:bg-neutral-tertiary-medium focus:outline-none focus:ring-2 " +
      "focus:ring-brand cursor-pointer"
  val dropdownPanelClass: String =
    "absolute right-0 top-full mt-2 z-50 w-56 bg-neutral-primary-medium border border-default-medium " +
      "rounded-base shadow-lg"
}
