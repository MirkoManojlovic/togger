package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons
import com.togger.util.FrontendMacros.SelectAndUpdate
import org.scalajs.dom

case class DropdownMeta(label: String, description: String)

case class TgSelectDropdownOption(value: String, meta: DropdownMeta, render: () => HtmlElement)

case class TgSelectDropdown(
  inputSignal: Signal[String],
  inputStateUpdater: Observer[String],
  labelValue: String,
  options: Seq[TgSelectDropdownOption],
  placeholder: String = "Select an option"
) {

  private val isOpenVar = Var(false)

  private val close = Observer[Unit](_ => isOpenVar.set(false))

  private def select(value: String): Unit = {
    inputStateUpdater.onNext(value)
    isOpenVar.set(false)
  }

  private def triggerContent(currentValue: String): HtmlElement =
    options.find(_.value == currentValue) match {
      case Some(option) => option.render()
      case None         => span(cls("text-body"), placeholder)
    }

  private def panel: HtmlElement =
    div(
      cls(TgSelectDropdown.panelCls),
      TgSelectDropdown.closeOnOutsideClickOrEscape(close),
      ul(
        cls("p-2 text-sm text-body font-medium max-h-96 overflow-y-auto"),
        options.map(option =>
          TgSelectDropdown.optionRow(option, inputSignal.map(_ == option.value), () => select(option.value))
        )
      )
    )

  val htmlElement: HtmlDiv =
    div(
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

object TgSelectDropdown {

  private[components] val triggerButtonCls =
    "inline-flex items-center justify-between w-full px-3 py-2.5 bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base focus:ring-brand focus:border-brand shadow-xs cursor-pointer"
  private[components] val panelCls =
    "absolute z-10 mt-1 w-full bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg"
  private[components] val labelCls = "block mb-2.5 text-sm font-medium text-heading"

  private[components] def closeOnOutsideClickOrEscape(close: Observer[Unit]): Seq[Modifier[HtmlElement]] = Seq(
    onMountBind { ctx =>
      val rootNode = ctx.thisNode.ref
      documentEvents(_.onClick)
        .filter(event => !rootNode.contains(event.target.asInstanceOf[dom.Node]))
        .mapToUnit --> close
    },
    onMountBind { _ =>
      documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> close
    }
  )

  private[components] def optionRow(
    option: TgSelectDropdownOption,
    isSelected: Signal[Boolean],
    onSelect: () => Unit
  ): HtmlElement =
    li(
      button(
        `type`("button"),
        cls(
          "flex flex-col items-start w-full p-2 text-sm font-medium text-heading hover:bg-neutral-tertiary-medium hover:text-heading rounded-md cursor-pointer"
        ),
        aria.label(option.meta.label),
        onClick.mapToUnit --> (_ => onSelect()),
        div(
          cls("inline-flex items-center w-full gap-1.5"),
          div(
            cls("w-4 h-4 shrink-0"),
            child.maybe <-- isSelected.map(selected => Option.when(selected)(TgIcons.checkIcon("w-4 h-4")))
          ),
          span(option.render())
        ),
        Option.when(option.meta.description.nonEmpty)(
          p(cls("w-full text-left text-xs font-normal text-body mt-0.5"), option.meta.description)
        )
      )
    )

  implicit class TgSelectDropdownSyntax[A](formState: Var[A]) {
    def tgSelectDropdownManualError(
      inputAndUpdater: SelectAndUpdate[A, String],
      labelValue: String,
      options: Seq[TgSelectDropdownOption],
      placeholder: String = "Select an option"
    ): TgSelectDropdown =
      TgSelectDropdown(
        inputSignal = formState.signal.map(inputAndUpdater.selector),
        inputStateUpdater = formState.updater(inputAndUpdater.updater),
        labelValue = labelValue,
        options = options,
        placeholder = placeholder
      )
  }
}
