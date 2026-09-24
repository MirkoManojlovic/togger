package com.togger.ui.emoji

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.ui.auth.TgHtmlTypes.{HtmlButton, HtmlDiv}
import com.togger.ui.auth.{TgInput, TgTextArea}
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.organization.TgIcons
import com.togger.util.FrontendMacros.SelectAndUpdate
import org.scalajs.dom.html.Paragraph

/** Form-facing wrapper around [[TgEmojiPicker]]: a labelled trigger button that opens the picker in a popover and
  * writes the chosen native emoji into a plain `String` form field. Shaped like `TgInput` so it drops into the existing
  * forms with the same label/error styling.
  */
case class TgEmojiField(
  valueSignal: Signal[String],
  valueUpdater: Observer[String],
  $errors: EventStream[Seq[String]],
  labelValue: String,
  localStorage: LocalStorage,
  isRequired: Boolean = true,
  config: TgEmojiPickerConfig = TgEmojiPickerConfig(),
  debounceTimeMillis: Int = 1000
) {

  private val isOpenVar: Var[Boolean] = Var(false)
  private val valueState: Var[String] = Var("")
  private val $errorEvents = $errors.debounce(debounceTimeMillis)
  private val errorsSignal: Signal[Seq[String]] = $errorEvents.startWith(Seq.empty)

  private val selectObserver = Observer[SelectedEmoji] { selected =>
    valueUpdater.onNext(selected.native)
    isOpenVar.set(false)
  }

  private val closeObserver = Observer[Unit](_ => isOpenVar.set(false))

  val htmlElement: HtmlDiv =
    div(
      valueSignal --> valueState,
      label(labelValue, cls <-- labelClassSignal),
      div(
        cls("relative w-fit"),
        triggerButton,
        validationInput,
        child <-- isOpenVar.signal.map(isOpen => if (isOpen) pickerPopover else emptyNode)
      ),
      children <-- $errorEvents.map(errorMessageElement)
    )

  private lazy val triggerButton: HtmlButton =
    button(
      `type`("button"),
      cls(TgEmojiField.triggerCls),
      aria.label := labelValue,
      title <-- emojiNameSignal.map(_.getOrElse(labelValue)),
      onClick.stopPropagation.mapToUnit --> (_ => isOpenVar.update(isOpen => !isOpen)),
      child <-- valueState.signal.map {
        case ""    => span(cls("text-sm text-body"), "Pick an icon")
        case value => span(styleAttr := "font-size: 24px; line-height: 1", value)
      },
      TgIcons.chevronDownIcon(className = "w-4 h-4 text-body")
    )

  /** Keeps the browser's native "please fill in this field" validation that the plain text input used to give us. It
    * sits invisibly over the trigger and never takes pointer or tab focus, but stays focusable enough for the
    * validation bubble to anchor to it.
    */
  private lazy val validationInput =
    input(
      cls("pointer-events-none absolute inset-0 h-full w-full opacity-0"),
      `type`("text"),
      tabIndex := -1,
      required(isRequired),
      controlled(
        value <-- valueState.signal,
        onInput.mapToValue --> valueUpdater
      )
    )

  private def pickerPopover: HtmlDiv =
    div(
      cls("absolute z-20 mt-1"),
      TgEmojiPicker(
        onEmojiSelect = selectObserver,
        localStorage = localStorage,
        config = config,
        onClickOutside = closeObserver
      ).element
    )

  private lazy val emojiNameSignal: Signal[Option[String]] =
    valueState.signal.combineWith(EmojiDataLoader.state).map {
      case (currentValue, EmojiDataState.Loaded(dataSet)) if currentValue.nonEmpty =>
        dataSet.findByNative(currentValue).map(_.name)
      case _ => None
    }

  private lazy val labelClassSignal: Signal[String] =
    errorsSignal.combineWith(valueState.signal).map {
      case (_, currentValue) if currentValue.isEmpty => TgTextArea.labelClass
      case (errors, _) if errors.nonEmpty            => TgInput.errorLabelClass
      case _                                         => TgInput.successLabelClass
    }

  private def errorMessageElement(errorMessages: Seq[String]): Seq[ReactiveHtmlElement[Paragraph]] =
    if (valueState.now().nonEmpty) errorMessages.map(message => p(cls(TgInput.errorMessageClass), message))
    else Seq.empty

}

object TgEmojiField {

  private val triggerCls =
    "flex items-center gap-2 rounded-base border border-default-medium bg-neutral-secondary-medium px-3 py-2 " +
      "shadow-xs cursor-pointer hover:bg-neutral-tertiary-medium focus:outline-none focus:ring-2 focus:ring-brand"

  implicit class TgEmojiFieldSyntax[A](formState: Var[A]) {

    def tgEmojiPicker(
      inputAndUpdater: SelectAndUpdate[A, String],
      errors: EventStream[Seq[String]],
      labelValue: String,
      localStorage: LocalStorage,
      isRequired: Boolean = true,
      config: TgEmojiPickerConfig = TgEmojiPickerConfig()
    ): TgEmojiField =
      TgEmojiField(
        valueSignal = formState.signal.map(inputAndUpdater.selector),
        valueUpdater = formState.updater(inputAndUpdater.updater),
        $errors = errors,
        labelValue = labelValue,
        localStorage = localStorage,
        isRequired = isRequired,
        config = config
      )

  }

}
