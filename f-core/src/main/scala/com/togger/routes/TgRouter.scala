package com.togger.routes

import com.togger.routes.Pages.NotFoundPage
import com.raquo.airstream.state.StrictSignal
import com.raquo.laminar.api.L._
import com.raquo.waypoint.{Route, Router}
import org.scalajs.dom

trait TgRouter[PAGE <: FrontendPage] {

  def route(page: PAGE): Unit

  def forcePage(page: PAGE): Unit

  val currentPageSignal: StrictSignal[PAGE]

  def absoluteUrlForPage(page: PAGE): String

  // This is used for a tags
  def navigateTo(page: PAGE): Binder[HtmlElement]

}

class TgRouterImpl(
  appRoutes: Seq[Route[_ <: FrontendPage, _]],
  origin: String,
  initialUrl: String
) extends TgRouter[FrontendPage] {
  val pageSerializer = new PageSerializer

  val router = new Router[FrontendPage](
    routes = appRoutes.toList,
    getPageTitle = _.toString,
    serializePage = pageSerializer.serialize,
    deserializePage = pageSerializer.deSerialize,
    routeFallback = url => NotFoundPage(url)
  )(
    popStateEvents = windowEvents(_.onPopState),
    owner = unsafeWindowOwner,
    origin = origin,
    initialUrl = initialUrl
  )

  override def route(page: FrontendPage): Unit =
    router.pushState(page)

  override def forcePage(page: FrontendPage): Unit =
    router.forcePage(page)

  override val currentPageSignal: StrictSignal[FrontendPage] =
    router.currentPageSignal

  override def absoluteUrlForPage(page: FrontendPage): String =
    router.absoluteUrlForPage(page)

  override def navigateTo(page: FrontendPage): Binder[HtmlElement] = Binder { el =>
    val isLinkElement = el.ref.isInstanceOf[dom.html.Anchor]

    if (isLinkElement) {
      el.amend(href(absoluteUrlForPage(page)))
    }

    // If element is a link and user is holding a modifier while clicking:
    //  - Do nothing, browser will open the URL in new tab / window / etc. depending on the modifier key
    // Otherwise:
    //  - Perform regular pushState transition
    (onClick
      .filter(ev => !(isLinkElement && (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey)))
      .preventDefault
      --> (_ => route(page))).bind(el)
  }
}
