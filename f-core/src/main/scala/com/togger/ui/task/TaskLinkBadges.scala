package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.TgHtmlElement
import com.togger.ui.components.{TgBadge, TgBadgeAction, TgBadgeColor}

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

  def badge(link: TaskLinkFormData): TgHtmlElement = {
    val text = if (link.name.trim.nonEmpty) link.name.trim else link.url.trim
    val action = safeHref(link.url).map(TgBadgeAction.Link(_))
    val element = TgBadge(text = text, color = TgBadgeColor.Gray, bordered = true, action = action).element
    action.fold(element)(_ => element.amend(target("_blank"), rel("noopener noreferrer")))
  }
}
