package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.TgHtmlElement
import com.togger.ui.components.TgBadge
import com.togger.ui.components.TgBadgeColor
import com.togger.task.LinkValidation
import com.togger.ui.organization.TgIcons

/** The links of a task as badges that show the link name and open the link in the browser of the system. */
object TaskLinkBadges {

  /** The address to open, see [[LinkValidation.safeHref]]. */
  def safeHref(url: String): Option[String] = LinkValidation.safeHref(url)

  /** The badge of a link: the name opens the address, the small pencil asks to change the link and the small bin to
    * remove it.
    */
  def badge(link: TaskLinkFormData, onEdit: () => Unit, onRemove: () => Unit): TgHtmlElement = {
    val text = s"🔗 ${if (link.name.trim.nonEmpty) link.name.trim else link.url.trim}"
    val label: HtmlElement = safeHref(link.url).fold[HtmlElement](span(text))(address =>
      a(href(address), target("_blank"), rel("noopener noreferrer"), cls("hover:underline"), text)
    )
    TgBadge(text = "", color = TgBadgeColor.Gray, bordered = true, extraCls = "gap-1.5").element.amend(
      label,
      span(
        cls("inline-flex items-center"),
        button(
          typ("button"),
          cls("rounded-xs p-0.5 text-body hover:text-heading hover:bg-neutral-tertiary"),
          aria.label("Edit link"),
          TgIcons.editIcon("w-3 h-3"),
          onClick.mapToUnit --> (_ => onEdit())
        ),
        button(
          typ("button"),
          cls("rounded-xs p-0.5 text-body hover:text-fg-danger hover:bg-neutral-tertiary"),
          aria.label("Remove link"),
          TgIcons.trashIcon("w-3 h-3"),
          onClick.mapToUnit --> (_ => onRemove())
        )
      )
    )
  }
}
