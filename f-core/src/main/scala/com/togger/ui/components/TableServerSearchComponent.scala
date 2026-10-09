package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons

/** A search box with a button: nothing happens while typing, `onSearch` gets the term when the button or Enter is hit. */
case class TableServerSearchComponent(
  initialTerm: String,
  placeholderValue: String,
  onSearch: String => Unit
) {

  private val termVar = Var(initialTerm)

  private val inputCls =
    "block w-full ps-9 pe-3 py-2 bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base focus:ring-brand focus:border-brand shadow-xs placeholder:text-body"

  private val buttonCls =
    "shrink-0 text-white bg-brand hover:bg-brand-strong border border-transparent focus:ring-4 focus:ring-brand-medium focus:outline-none shadow-xs font-medium rounded-base text-sm px-3 py-2 cursor-pointer"

  private def submit(): Unit = onSearch(termVar.now())

  val element: HtmlDiv =
    div(
      cls("flex items-center gap-2 max-w-96"),
      div(
        cls("relative grow"),
        div(
          cls("absolute inset-y-0 start-0 flex items-center ps-3 pointer-events-none"),
          TgIcons.searchIcon(className = "w-4 h-4 text-body")
        ),
        input(
          `type`("text"),
          cls(inputCls),
          placeholder(placeholderValue),
          controlled(value <-- termVar.signal, onInput.mapToValue --> termVar.writer),
          onKeyDown.filter(_.key == "Enter") --> (_ => submit())
        )
      ),
      button(`type`("button"), cls(buttonCls), "Search", onClick.mapToUnit --> (_ => submit()))
    )
}
