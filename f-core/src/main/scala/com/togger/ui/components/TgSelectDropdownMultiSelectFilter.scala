package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons
import com.togger.util.FrontendMacros.SelectAndUpdate
import org.scalajs.dom.HTMLButtonElement

case class TgSelectDropdownMultiSelectFilter(
  inputSignal: Signal[Set[String]],
  inputStateUpdater: Observer[Set[String]],
  labelValue: String,
  options: Seq[TgSelectDropdownOption],
  placeholder: String = "Select options"
) {

  private val isOpenVar = Var(false)
  private val currentVar = Var(Set.empty[String])
  private val queryVar = Var("")

  private val close = Observer[Unit](_ => isOpenVar.set(false))

  private val filteredOptionsSignal: Signal[Seq[TgSelectDropdownOption]] =
    queryVar.signal.map(query => options.filter(_.meta.label.toLowerCase.contains(query.trim.toLowerCase)))

  private val searchInputCls =
    "block w-full ps-9 pe-3 py-2 bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base focus:ring-brand focus:border-brand shadow-xs placeholder:text-body"

  private def toggle(value: String): Unit = {
    val current = currentVar.now()
    inputStateUpdater.onNext(if (current.contains(value)) current - value else current + value)
  }

  private def triggerContent(selected: Set[String]): HtmlElement =
    options.filter(option => selected.contains(option.value)) match {
      case Nil    => span(cls("text-body"), placeholder)
      case chosen => div(cls("flex flex-wrap gap-1"), chosen.map(option => span(option.render())))
    }

  private def actionRow(actionLabel: String, onClickMod: Modifier[ReactiveHtmlElement[HTMLButtonElement]]) =
    li(
      button(
        `type`("button"),
        cls(
          "w-full text-left p-2 text-sm font-medium text-heading hover:bg-neutral-tertiary-medium rounded cursor-pointer"
        ),
        onClickMod,
        actionLabel
      )
    )

  private def searchRow: HtmlElement =
    li(
      cls("p-1"),
      div(
        cls("relative"),
        div(
          cls("absolute inset-y-0 start-0 flex items-center ps-3 pointer-events-none"),
          TgIcons.searchIcon(className = "w-4 h-4 text-body")
        ),
        input(
          cls(searchInputCls),
          `type`("text"),
          aria.label := "Filter options",
          com.raquo.laminar.api.L.placeholder("Filter options"),
          controlled(
            value <-- queryVar.signal,
            onInput.mapToValue --> queryVar.writer
          ),
          onMountCallback(ctx => ctx.thisNode.ref.focus())
        )
      )
    )

  private def panel: HtmlElement =
    div(
      cls(TgSelectDropdown.panelCls),
      TgSelectDropdown.closeOnOutsideClickOrEscape(close),
      ul(
        cls("p-2 text-sm text-body font-medium max-h-96 overflow-y-auto"),
        searchRow,
        li(cls("border-t border-default my-1")),
        children <-- filteredOptionsSignal.map { filtered =>
          val filteredValues = filtered.map(_.value).toSet
          Seq(
            actionRow(
              "Select all",
              onClick.mapToUnit --> (_ => inputStateUpdater.onNext(currentVar.now() ++ filteredValues))
            ),
            actionRow(
              "Deselect all",
              onClick.mapToUnit --> (_ => inputStateUpdater.onNext(currentVar.now() -- filteredValues))
            ),
            li(cls("border-t border-default my-1"))
          ) ++ (
            if (filtered.isEmpty) Seq(li(cls("p-2 text-center text-body"), "No matching options"))
            else
              filtered.map(option =>
                TgSelectDropdown.optionRow(option, inputSignal.map(_.contains(option.value)), () => toggle(option.value))
              )
          )
        }
      )
    )

  val htmlElement: HtmlDiv =
    div(
      inputSignal --> currentVar.writer,
      isOpenVar.signal --> Observer[Boolean](isOpen => if (!isOpen) queryVar.set("")),
      label(labelValue, cls(TgSelectDropdown.labelCls)),
      div(
        cls("relative"),
        // The panel is absolutely positioned, so reserve its height below the trigger to let the page scroll down.
        cls.toggle("pb-96") <-- isOpenVar.signal,
        button(
          `type`("button"),
          cls(TgSelectDropdown.triggerButtonCls),
          onClick.stopPropagation.mapToUnit --> (_ => isOpenVar.update(isOpened => !isOpened)),
          child <-- inputSignal.map(triggerContent),
          TgIcons.chevronDownIcon("w-4 h-4 ms-1.5")
        ),
        child <-- isOpenVar.signal.map {
          case true  => panel
          case false => emptyNode
        }
      )
    )
}

object TgSelectDropdownMultiSelectFilter {
  implicit class TgSelectDropdownMultiSelectFilterSyntax[A](formState: Var[A]) {
    def tgSelectDropdownMultiSelectFilterManualError(
      inputAndUpdater: SelectAndUpdate[A, Set[String]],
      labelValue: String,
      options: Seq[TgSelectDropdownOption],
      placeholder: String = "Select options"
    ): TgSelectDropdownMultiSelectFilter =
      TgSelectDropdownMultiSelectFilter(
        inputSignal = formState.signal.map(inputAndUpdater.selector),
        inputStateUpdater = formState.updater(inputAndUpdater.updater),
        labelValue = labelValue,
        options = options,
        placeholder = placeholder
      )
  }
}
