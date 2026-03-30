package com.togger.ui.auth

import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.auth.TgTextArea.{labelClass, tgTextAreaStyle}
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.util.FrontendMacros.SelectAndUpdate
import org.scalajs.dom.html
import org.scalajs.dom.html.Paragraph

case class TgTextArea(
  inputSignal: Signal[String],
  inputStateUpdater: Observer[String],
  $errors: EventStream[Seq[String]],
  labelValue: String,
  placeholderValue: String,
  numberOfRows: Int = 10,
  numberOfColumns: Int = 50,
  isRequired: Boolean = false,
  debounceTimeMillis: Int = 1000
) {

  private val $errorEvents = $errors.debounce(debounceTimeMillis)
  private val errorState: Var[Seq[String]] = Var(Seq.empty)
  private val inputState = Var("")

  val htmlElement: ReactiveHtmlElement[html.Div] =
    div(
      $errors --> errorState,
      inputSignal --> inputState,
      p(
        label(labelValue, cls(labelClass)),
        textArea(
          cls(tgTextAreaStyle),
          placeholder(placeholderValue),
          rows(numberOfRows),
          cols(numberOfColumns),
          controlled(
            value <-- inputState,
            onInput.mapToValue --> inputStateUpdater
          ),
          required(isRequired)
        )
      ),
      cls("has-error") <-- $errorEvents.map(_.nonEmpty && inputNotEmpty),
      cls("has-success") <-- $errorEvents.map(_.isEmpty && inputNotEmpty),
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
        textArea(
          cls("form-input"),
          rows(numberOfRows),
          cols(numberOfColumns),
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
      errorMessages.map(message => p(cls("form-input-hint"), message))
    else
      Seq.empty

}

object TgTextArea {

  val tgTextAreaStyle: String =
    """block mr-4 p-2.5 w-full text-sm text-gray-900 bg-white rounded-lg border border-gray-300
      |focus:ring-primary-500 focus:border-primary-500 dark:bg-gray-800 dark:border-gray-600
      |dark:placeholder-gray-400 dark:text-white dark:focus:ring-primary-500
      |dark:focus:border-primary-500""".stripMargin

  def classAsSeqTuple(style: String): Seq[(String, Boolean)] =
    style.split(" ").map(_ -> true).toSeq

  def tailwindClasses(style: String): (String, Seq[(String, Boolean)]) =
    style -> classAsSeqTuple(style)

  val (labelClass, labelClassMap) = tailwindClasses("block mb-2 text-sm font-medium text-gray-900 dark:text-white")

  implicit class TgTextAreaSyntax[A](formState: Var[A]) {

    def tgTextAreaManualErrorSig(
      inputAndUpdater: SelectAndUpdate[A, String],
      errors: EventStream[Seq[String]],
      labelValue: String,
      isRequired: Boolean = true,
      numberOfRows: Int = 10,
      numberOfColumns: Int = 50,
      debounceTimeMillis: Int = 1000
    ): TgTextArea =
      TgTextArea(
        inputSignal = formState.signal.map(inputAndUpdater.selector),
        inputStateUpdater = formState.updater(inputAndUpdater.updater),
        $errors = errors,
        labelValue = labelValue,
        placeholderValue = labelValue,
        numberOfRows = numberOfRows,
        numberOfColumns = numberOfColumns,
        isRequired = isRequired,
        debounceTimeMillis = debounceTimeMillis
      )

  }

}
