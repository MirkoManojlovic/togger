package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons
import com.togger.util.FrontendMacros.SelectAndUpdate
import org.scalajs.dom

case class TgSelectDropdownOption(value: String, render: () => HtmlElement)

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

  private def optionRow(option: TgSelectDropdownOption, currentValue: String) =
    li(
      button(
        `type`("button"),
        cls(
          "inline-flex items-center justify-between w-full p-2 text-sm font-medium text-heading hover:bg-neutral-tertiary-medium hover:text-heading rounded-md cursor-pointer"
        ),
        onClick.mapToUnit --> (_ => select(option.value)),
        span(option.render()),
        Option.when(option.value == currentValue)(TgIcons.checkIcon("w-4 h-4 ms-1.5"))
      )
    )

  private def panel(currentValue: String) =
    div(
      cls(
        "absolute z-10 mt-1 w-full bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg"
      ),
      onMountBind { ctx =>
        val rootNode = ctx.thisNode.ref
        documentEvents(_.onClick)
          .filter(event => !rootNode.contains(event.target.asInstanceOf[dom.Node]))
          .mapToUnit --> close
      },
      onMountBind { _ =>
        documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> close
      },
      ul(
        cls("p-2 text-sm text-body font-medium max-h-64 overflow-y-auto"),
        children <-- inputSignal.map(currentValue => options.map(optionRow(_, currentValue)))
      )
    )

  val htmlElement: HtmlDiv =
    div(
      label(labelValue, cls("block mb-2.5 text-sm font-medium text-heading")),
      div(
        cls("relative"),
        button(
          `type`("button"),
          cls(
            "inline-flex items-center justify-between w-full px-3 py-2.5 bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base focus:ring-brand focus:border-brand shadow-xs cursor-pointer"
          ),
          onClick.stopPropagation.mapToUnit --> (_ => isOpenVar.update(isOpened => !isOpened)),
          child <-- inputSignal.map(triggerContent),
          TgIcons.chevronDownIcon("w-4 h-4 ms-1.5")
        ),
        child <-- isOpenVar.signal.combineWith(inputSignal).map {
          case (true, currentValue) => panel(currentValue)
          case (false, _)           => emptyNode
        }
      )
    )
}

object TgSelectDropdown {
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
