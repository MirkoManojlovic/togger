package com.togger.ui.logentry

import com.raquo.laminar.api.L._

/** The row of date, status and member badges shared by every log entry view, so they line up the same way. A badge
  * never breaks its text; those that do not fit the row go on the lines below.
  */
object LogEntryBadgeRow {

  val rowClasses: String = "flex flex-wrap items-center gap-2 [&>*]:whitespace-nowrap"

  def apply(badges: Modifier[HtmlElement]*): HtmlElement =
    div(cls(rowClasses), badges)
}
