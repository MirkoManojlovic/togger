package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.{HtmlDiv, TgHtmlElement}

case class TgTooltip(trigger: TgHtmlElement, text: String) {
  private val visibleVar = Var(false)

  private val visibilityCls: Signal[String] =
    visibleVar.signal.map(visible => if (visible) "opacity-100 visible" else "opacity-0 invisible")

  val element: HtmlDiv =
    div(
      cls("relative"),
      trigger.amend(
        onMouseEnter --> (_ => visibleVar.set(true)),
        onMouseLeave --> (_ => visibleVar.set(false))
      ),
      div(
        role("tooltip"),
        cls(
          "absolute z-10 bottom-full left-1/2 -translate-x-1/2 mb-2 px-3 py-2 text-sm font-medium " +
            "text-white bg-dark rounded-base shadow-xs whitespace-nowrap transition-opacity duration-300 " +
            "pointer-events-none"
        ),
        cls <-- visibilityCls,
        text,
        div(cls("absolute top-full left-1/2 -translate-x-1/2 -mt-1 w-2 h-2 rotate-45 bg-dark"))
      )
    )
}
