package com.togger.ui.auth

import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.auth.TgInput.{
  errorInputClass,
  errorLabelClass,
  errorMessageClass,
  successInputClass,
  successLabelClass,
  tgInputStyle
}
import com.togger.ui.auth.TgInputType.Text
import com.togger.ui.auth.TgTextArea.tailwindClasses
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.util.FrontendMacros.{SelectAndUpdate, SelectAndUpdateAndError}
import enumeratum._
import org.scalajs.dom.html
import org.scalajs.dom.html.Paragraph
sealed trait TgInputType extends EnumEntry

object TgInputType extends Enum[TgInputType] {
  val values: IndexedSeq[TgInputType] = findValues

  case object Password extends TgInputType
  case object Text extends TgInputType
  case object Date extends TgInputType
  case object Color extends TgInputType
}


case class TgInput(
  inputSignal: Signal[String],
  inputStateUpdater: Observer[String],
  $errors: EventStream[Seq[String]],
  labelValue: String,
  placeholderValue: String,
  tgInputType: TgInputType = Text,
  isRequired: Boolean = false,
  debounceTimeMillis: Int = 1000
) {

  private val $errorEvents = $errors.debounce(debounceTimeMillis)
  private val errorState: Var[Seq[String]] = Var(Seq.empty)
  private val inputState = Var("")
  private val labelDefaultStyle = Var(true)

  val htmlElement: ReactiveHtmlElement[html.Div] =
    div(
      $errors --> errorState,
      inputSignal --> inputState,
      div(
        label(
          labelValue,
          cls <-- labelDefaultStyle.signal.map { setDefault =>
            if (setDefault) TgTextArea.labelClassMap
            else Seq.empty
          },
          cls <-- $errorEvents.map(errors => {
            if (errors.nonEmpty && inputNotEmpty) {
              labelDefaultStyle.set(false)
              Seq(errorLabelClass)
            } else if (errors.isEmpty && inputNotEmpty) {
              labelDefaultStyle.set(false)
              Seq(successLabelClass)
            } else {
              labelDefaultStyle.set(true)
              Seq.empty
            }
          })
        ),
        input(
          cls(tgInputStyle),
          cls(errorInputClass) <-- $errorEvents.map(_.nonEmpty && inputNotEmpty),
          cls(successInputClass) <-- $errorEvents.map(_.isEmpty && inputNotEmpty),
          placeholder(placeholderValue),
          controlled(
            value <-- inputState,
            onInput.mapToValue --> inputStateUpdater
          ),
          `type` := tgInputType.toString,
          required(isRequired)
        )
      ),
      children <-- $errorEvents.map(errorMessageElement)
    )

  def horizontalElement(textSize: ColumnSize = ColumSize.Column6): HtmlDiv =
    div(
      $errors --> errorState,
      inputSignal --> inputState,
      cls("form-group"),
      div(cls(textSize.withSmColumnText), label(cls("form-label"), labelValue)),
      div(
        cls(textSize.withSmColumnInput),
        input(
          cls("form-input"),
          `type` := tgInputType.toString,
          required(isRequired),
          placeholder(placeholderValue),
          controlled(
            value <-- inputState,
            onInput.mapToValue --> inputStateUpdater
          )
        )
      ),
      cls("has-error") <-- $errorEvents.map(_.nonEmpty && inputNotEmpty),
      cls("has-success") <-- $errorEvents.map(_.isEmpty && inputNotEmpty),
      children <-- $errorEvents.map(errorMessageElement)
    )

  private def inputNotEmpty: Boolean =
    inputState.now().nonEmpty

  private def errorMessageElement(errorMessages: Seq[String]): Seq[ReactiveHtmlElement[Paragraph]] =
    if (inputNotEmpty)
      errorMessages.map(message => p(cls(errorMessageClass), message))
    else
      Seq.empty
}

object TgInput {

  val (tgInputStyle, tgInputStyleMap) = tailwindClasses("""
    |shadow-sm bg-gray-50 border border-gray-300 text-gray-900 sm:text-sm rounded-lg focus:ring-primary-500
    |focus:border-primary-500 block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400
    |dark:text-white dark:focus:ring-primary-500 dark:focus:border-primary-500
    |""".stripMargin)

  val (successInputClass, successInputClassMap) = tailwindClasses(
    """bg-success-soft border border-success-subtle text-fg-success-strong text-sm rounded-base focus:ring-success
      |focus:border-success block w-full px-3 py-2.5 shadow-xs placeholder:text-fg-success-strong|
      |""".stripMargin
  )

  val successLabelClass: String = "block mb-2.5 text-sm font-medium text-fg-success-strong"

  val successMessageClass: String = "mt-2.5 text-sm text-fg-success-strong"

  val errorInputClass =
    """bg-danger-soft border border-danger-subtle text-fg-danger-strong text-sm rounded-base focus:ring-danger 
      |focus:border-danger block w-full px-3 py-2.5 shadow-xs placeholder:text-fg-danger-strong 
      |""".stripMargin

  val errorLabelClass: String = "block mb-2.5 text-sm font-medium text-fg-danger-strong"

  val errorMessageClass: String = "mt-2.5 text-sm text-fg-danger-strong"

  implicit class TgInputSyntax[A](formState: Var[A]) {
    def tgPlain(
      inputSignal: Signal[String],
      inputStateUpdater: Observer[String],
      $errors: EventStream[Seq[String]],
      labelValue: String,
      placeholderValue: String = "",
      tgInputType: TgInputType = Text,
      isRequired: Boolean = true,
      debounceTimeMillis: Int = 1000
    ): TgInput = TgInput(
      inputSignal = inputSignal,
      inputStateUpdater = inputStateUpdater,
      $errors = $errors,
      labelValue = labelValue,
      placeholderValue = placeholderValue,
      tgInputType = tgInputType,
      isRequired = isRequired,
      debounceTimeMillis = debounceTimeMillis
    )

    def tgInputManualError(
      inputAndUpdater: SelectAndUpdate[A, String],
      errorsFn: A => Seq[String],
      tgInputType: TgInputType = Text,
      labelValue: String,
      placeholderValue: String,
      isRequired: Boolean = true,
      debounceTimeMillis: Int = 1000
    ): TgInput =
      TgInput(
        inputSignal = formState.signal.map(inputAndUpdater.selector),
        inputStateUpdater = formState.updater(inputAndUpdater.updater),
        $errors = formState.signal.changes.map(errorsFn),
        labelValue = labelValue,
        placeholderValue = placeholderValue,
        tgInputType = tgInputType,
        isRequired = isRequired,
        debounceTimeMillis = debounceTimeMillis
      )

    def tgInputManualErrorLP(
      inputAndUpdater: SelectAndUpdate[A, String],
      errorsFn: A => Seq[String],
      tgInputType: TgInputType = Text,
      labelValue: String,
      isRequired: Boolean = true,
      debounceTimeMillis: Int = 1000
    ): TgInput =
      TgInput(
        inputSignal = formState.signal.map(inputAndUpdater.selector),
        inputStateUpdater = formState.updater(inputAndUpdater.updater),
        $errors = formState.signal.changes.map(errorsFn),
        labelValue = labelValue,
        placeholderValue = labelValue,
        tgInputType = tgInputType,
        isRequired = isRequired,
        debounceTimeMillis = debounceTimeMillis
      )

    def tgInputManualErrorLPSig(
      inputAndUpdater: SelectAndUpdate[A, String],
      errors: EventStream[Seq[String]],
      tgInputType: TgInputType = Text,
      labelValue: String,
      isRequired: Boolean = true,
      debounceTimeMillis: Int = 1000
    ): TgInput =
      TgInput(
        inputSignal = formState.signal.map(inputAndUpdater.selector),
        inputStateUpdater = formState.updater(inputAndUpdater.updater),
        $errors = errors,
        labelValue = labelValue,
        placeholderValue = labelValue,
        tgInputType = tgInputType,
        isRequired = isRequired,
        debounceTimeMillis = debounceTimeMillis
      )

    def tgInputAutoError(
      inputAndUpdaterAndError: SelectAndUpdateAndError[A, String],
      labelValue: String,
      placeholderValue: String,
      tgInputType: TgInputType = Text,
      isRequired: Boolean = true,
      debounceTimeMillis: Int = 1000
    ): TgInput =
      TgInput(
        inputSignal = formState.signal.map(inputAndUpdaterAndError.selector),
        inputStateUpdater = formState.updater(inputAndUpdaterAndError.updater),
        $errors = formState.signal.changes.map(inputAndUpdaterAndError.errorFn),
        labelValue = labelValue,
        placeholderValue = placeholderValue,
        tgInputType = tgInputType,
        isRequired = isRequired,
        debounceTimeMillis = debounceTimeMillis
      )

    def tgInputAutoErrorLP(
      inputAndUpdaterAndError: SelectAndUpdateAndError[A, String],
      labelValue: String,
      tgInputType: TgInputType = Text,
      isRequired: Boolean = true,
      debounceTimeMillis: Int = 1000
    ): TgInput =
      TgInput(
        inputSignal = formState.signal.map(inputAndUpdaterAndError.selector),
        inputStateUpdater = formState.updater(inputAndUpdaterAndError.updater),
        $errors = formState.signal.changes.map(inputAndUpdaterAndError.errorFn),
        labelValue = labelValue,
        placeholderValue = labelValue,
        tgInputType = tgInputType,
        isRequired = isRequired,
        debounceTimeMillis = debounceTimeMillis
      )

  }

}
