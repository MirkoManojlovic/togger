package com.togger.ui.auth

import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.raquo.laminar.api.L._

case class TgProgress(currentValue: Int, maxValue: Int) {

  private val progressNormalized = ((currentValue / maxValue.toDouble) * 100).round

  val htmlElement: HtmlDiv =
    div(
      cls("bar"),
      marginTop("0.25rem"),
      marginBottom("0.25rem"),
      div(
        cls("bar-item"),
        role("progressbar"),
        width(s"$progressNormalized%"),
        aria.valueNow(currentValue),
        aria.valueMin(0),
        aria.valueMax(100),
        s"$progressNormalized%",
        fontSize.inherit
      )
    )

  def withBackgroundColor(color: String): HtmlDiv =
    div(
      cls("bar"),
      marginTop("0.25rem"),
      marginBottom("0.25rem"),
      div(
        cls("bar-item"),
        role("progressbar"),
        width(s"$progressNormalized%"),
        aria.valueNow(currentValue),
        aria.valueMin(0),
        aria.valueMax(100),
        s"$progressNormalized%",
        fontSize.inherit,
        backgroundColor(color)
      )
    )

}
