package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.auth.TgInput
import com.togger.ui.organization.TgIcons

case class TableSearchComponent(
  inputSignal: Signal[String],
  inputStateUpdater: Observer[String],
  placeholderValue: String,
  debounceMs: Int = 300
) {

  private val inputCls =
    "block w-full max-w-96 ps-9 pe-3 py-2 bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base focus:ring-brand focus:border-brand shadow-xs placeholder:text-body"

  val searchInput = TgInput(
    inputSignal = inputSignal,
    inputStateUpdater = inputStateUpdater,
    $errors = EventStream.empty,
    labelValue = "",
    placeholderValue = placeholderValue,
    debounceTimeMillis = debounceMs
  )

  val element: HtmlDiv =
    div(
      cls("relative"),
      div(
        cls("absolute inset-y-0 start-0 flex items-center ps-3 pointer-events-none"),
        TgIcons.searchIcon(className = "w-4 h-4 text-body")
      ),
      searchInput.rawHtmlElement.amend(cls(inputCls))
    )
}
