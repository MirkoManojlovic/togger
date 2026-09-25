package com.togger.ui.navbar

import com.togger.routes.Pages.LoginPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.LoginRepo
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.{HTMLAnchorElement, HTMLLIElement, MouseEvent}
import scalacss.StyleSheet

case class Logout[PAGE](
  loginRepo: LoginRepo,
  router: TgRouter[FrontendPage]
) {

  def logoutButton: ReactiveHtmlElement[HTMLAnchorElement] =
    a("Log out", onClick --> logout, href("#"))

  def logoutButtonWithLabel(label: Signal[HtmlElement]): ReactiveHtmlElement[HTMLAnchorElement] =
    a(child <-- label, onClick --> logout, href("#"))

  private def logout: MouseEvent => Unit = { _ =>
    loginRepo.delete()
    router.route(LoginPage)
  }

}

import com.togger.ui.TgCss.CssSettings._

object LogoutStyle extends StyleSheet.Inline {
  import dsl._

  val logoutButtonStyle: StyleA = style(
    addClassName("menu-item"),
    &.hover(
      cursor.pointer
    )
  )

}
