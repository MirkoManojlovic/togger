package com.togger.ui.components

import com.raquo.laminar.api.L._

/** A stat in a boxed, centered column; the columns of a row share it evenly, so a left out one leaves its space to the
  * others.
  */
object TgStatColumn {

  def apply(stat: HtmlElement): HtmlElement =
    stat.amend(cls("min-w-32 flex-1 items-center rounded-base border border-default px-3 py-2 text-center"))
}
