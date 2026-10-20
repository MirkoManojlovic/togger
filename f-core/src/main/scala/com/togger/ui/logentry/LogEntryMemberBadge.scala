package com.togger.ui.logentry

import com.raquo.laminar.api.L._
import com.togger.auth.AuthAccountFe
import com.togger.ui.components.{TgBadge, TgBadgeColor}

/** The badge after a log entry's status badge, with the first and last name of the member who made the entry. */
object LogEntryMemberBadge {

  def apply(name: String): Option[HtmlElement] =
    Option(name.trim)
      .filter(_.nonEmpty)
      .map(fullName => TgBadge(text = fullName, bordered = true, color = TgBadgeColor.Indigo).element)

  def nameOf(account: AuthAccountFe): String =
    (account.firstName.toSeq ++ account.lastName).mkString(" ")
}
