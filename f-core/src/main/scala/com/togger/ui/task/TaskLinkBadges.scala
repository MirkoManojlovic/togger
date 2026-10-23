package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.TgHtmlElement
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.organization.TgIcons

/** The links of a task as badges that show the link name and open the link in the browser of the system. */
object TaskLinkBadges {

  private val UnsafeScheme = "(?i)^\\s*(javascript|data|vbscript|file|blob):".r
  private val AnyScheme = "(?i)^[a-z][a-z0-9+.-]*://".r

  /** The address to open: web addresses as they are, a bare one like `example.com/x` as https, nothing for the rest. */
  def safeHref(url: String): Option[String] = {
    val trimmed = url.trim
    val lower = trimmed.toLowerCase
    if (trimmed.isEmpty || UnsafeScheme.findPrefixOf(trimmed).isDefined) None
    else if (lower.startsWith("http://") || lower.startsWith("https://")) Some(trimmed)
    else if (AnyScheme.findPrefixOf(trimmed).isDefined) None
    else Some(s"https://$trimmed")
  }

  /** The badge of a link: the name opens the address, the small pencil asks to change the link. */
  def badge(link: TaskLinkFormData, onEdit: () => Unit): TgHtmlElement = {
    val text = s"🔗 ${if (link.name.trim.nonEmpty) link.name.trim else link.url.trim}"
    val label: HtmlElement = safeHref(link.url).fold[HtmlElement](span(text))(address =>
      a(href(address), target("_blank"), rel("noopener noreferrer"), cls("hover:underline"), text)
    )
    TgBadge(text = "", color = TgBadgeColor.Gray, bordered = true, extraCls = "gap-1.5").element.amend(
      label,
      button(
        typ("button"),
        cls("rounded-xs p-0.5 text-body hover:text-heading hover:bg-neutral-tertiary"),
        aria.label("Edit link"),
        TgIcons.editIcon("w-3 h-3"),
        onClick.mapToUnit --> (_ => onEdit())
      )
    )
  }
}
