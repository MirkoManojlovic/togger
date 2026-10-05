package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveSvgElement
import com.raquo.laminar.tags.HtmlTag
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.organization.TgIcons
import org.scalajs.dom.{HTMLElement, SVGSVGElement}

/** One crumb. `page = None` renders plain text (a level with no page of its own).
  *
  * @param icon
  *   optional leading icon, as a `className => svg` function (e.g. `Some(TgIcons.homeIcon)`)
  */
case class TgBreadcrumb(
  label: String,
  page: Option[FrontendPage] = None,
  icon: Option[String => ReactiveSvgElement[SVGSVGElement]] = None
)

sealed trait TgBreadcrumbVariant

object TgBreadcrumbVariant {
  case object Default extends TgBreadcrumbVariant
  case object Solid extends TgBreadcrumbVariant
}

sealed trait TgCrumbRole

object TgCrumbRole {
  case object Link extends TgCrumbRole
  case object Text extends TgCrumbRole
  case object Current extends TgCrumbRole
}

/** Flowbite breadcrumb for Laminar.
  *
  * The last item is always the current page, so it is never a link; earlier items are links when they carry a page.
  */
class TgBreadcrumbs(
  items: Seq[TgBreadcrumb],
  router: TgRouter[FrontendPage],
  variant: TgBreadcrumbVariant = TgBreadcrumbVariant.Default
) {

  private val solidCls = "p-3 bg-neutral-secondary-medium border border-default-medium rounded-base"
  private val listCls = "inline-flex items-center space-x-1 md:space-x-2 rtl:space-x-reverse"
  private val linkCls = "inline-flex items-center text-sm font-medium text-body hover:text-fg-brand"
  private val currentCls = "inline-flex items-center text-sm font-medium text-body-subtle"
  private val separatorCls = "w-3.5 h-3.5 rtl:rotate-180 text-body"
  private val iconCls = "w-4 h-4 me-1.5"

  private val navTag = new HtmlTag[HTMLElement]("nav")

  val element: HtmlElement =
    navTag(
      cls("flex"),
      cls.toggle(solidCls) := variant == TgBreadcrumbVariant.Solid,
      aria.label("Breadcrumb"),
      ol(cls(listCls), items.zipWithIndex.map { case (item, index) => crumb(item, index) })
    )

  private def crumb(item: TgBreadcrumb, index: Int): HtmlElement = {
    val crumbRole = TgBreadcrumbs.roleOf(index, items.size, item.page.isDefined)
    val body = crumbBody(item, crumbRole)
    li(
      cls.toggle("inline-flex items-center") := index == 0,
      Option.when(crumbRole == TgCrumbRole.Current)(aria.current("page")),
      if (index == 0) body else div(cls("flex items-center space-x-1.5"), separator, body)
    )
  }

  private def crumbBody(item: TgBreadcrumb, crumbRole: TgCrumbRole): HtmlElement =
    (crumbRole, item.page) match {
      case (TgCrumbRole.Link, Some(page)) => a(cls(linkCls), router.navigateTo(page), crumbContent(item))
      case (TgCrumbRole.Current, _)       => span(cls(currentCls), crumbContent(item))
      case _                              => span(cls(linkCls), crumbContent(item))
    }

  private def crumbContent(item: TgBreadcrumb): Seq[Modifier[HtmlElement]] =
    Seq(item.icon.map(mkIcon => mkIcon(iconCls)), item.label)

  private def separator = TgIcons.chevronRightIcon(separatorCls).amend(aria.hidden(true))

}

object TgBreadcrumbs {

  def roleOf(index: Int, size: Int, hasPage: Boolean): TgCrumbRole =
    if (index == size - 1) TgCrumbRole.Current
    else if (hasPage) TgCrumbRole.Link
    else TgCrumbRole.Text

}
