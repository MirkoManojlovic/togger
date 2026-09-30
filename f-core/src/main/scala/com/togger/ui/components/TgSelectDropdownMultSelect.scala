package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons
import com.togger.util.FrontendMacros.SelectAndUpdate
import org.scalajs.dom.HTMLButtonElement

case class TgSelectDropdownMultSelect(
  inputSignal: Signal[Set[String]],
  inputStateUpdater: Observer[Set[String]],
  labelValue: String,
  options: Seq[TgSelectDropdownOption],
  placeholder: String = "Select options"
) {

  private val isOpenVar = Var(false)
  private val currentVar = Var(Set.empty[String])

  private val close = Observer[Unit](_ => isOpenVar.set(false))

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
          "w-full text-left p-2 text-sm font-medium hover:bg-neutral-tertiary-medium hover:text-heading rounded cursor-pointer"
        ),
        onClickMod,
        actionLabel
      )
    )

  private def panel: HtmlElement =
    div(
      cls(TgSelectDropdown.panelCls),
      TgSelectDropdown.closeOnOutsideClickOrEscape(close),
      ul(
        cls("p-2 text-sm text-body font-medium max-h-96 overflow-y-auto"),
        actionRow("Select all", onClick.mapToUnit --> (_ => inputStateUpdater.onNext(options.map(_.value).toSet))),
        actionRow("Deselect all", onClick.mapToUnit --> (_ => inputStateUpdater.onNext(Set.empty))),
        li(cls("border-t border-default my-1")),
        options.map(option =>
          TgSelectDropdown.optionRow(option, inputSignal.map(_.contains(option.value)), () => toggle(option.value))
        )
      )
    )

  val htmlElement: HtmlDiv =
    div(
      inputSignal --> currentVar.writer,
      label(labelValue, cls(TgSelectDropdown.labelCls)),
      div(
        cls("relative"),
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

object TgSelectDropdownMultSelect {
  implicit class TgSelectDropdownMultSelectSyntax[A](formState: Var[A]) {
    def tgSelectDropdownMultSelectManualError(
      inputAndUpdater: SelectAndUpdate[A, Set[String]],
      labelValue: String,
      options: Seq[TgSelectDropdownOption],
      placeholder: String = "Select options"
    ): TgSelectDropdownMultSelect =
      TgSelectDropdownMultSelect(
        inputSignal = formState.signal.map(inputAndUpdater.selector),
        inputStateUpdater = formState.updater(inputAndUpdater.updater),
        labelValue = labelValue,
        options = options,
        placeholder = placeholder
      )
  }
}
