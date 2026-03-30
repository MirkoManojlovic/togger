package com.togger.ui.auth

import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.util.FrontendMacros.{SelectAndUpdate, SelectAndUpdateAndError}
import org.scalajs.dom.html
import org.scalajs.dom.html.Paragraph

import scala.collection.immutable.ListMap

case class TgSelect(
  inputSignal: Signal[String],
  inputStateUpdater: Observer[String],
  $errors: EventStream[Seq[String]] = EventStream.fromValue(Seq.empty),
  labelValue: String,
  emptyValue: Option[String] = None,
  valuesAndDisplays: Map[String, String]
) {

  private val $errorEvents = $errors.debounce(1000)
  private val errorState: Var[Seq[String]] = Var(Seq.empty)
  private val inputState = Var("")
  private val mapWithEmptyElement =
    emptyValue
      .map(emptyVal => ListMap("" -> emptyVal) ++ valuesAndDisplays)
      .getOrElse(valuesAndDisplays)

  val htmlElement: ReactiveHtmlElement[html.Div] =
    div(
      $errors --> errorState,
      inputSignal --> inputState,
      div(
        label(labelValue, cls("block mb-2.5 text-sm font-medium text-heading")),
        select(
          cls(
            "block w-full px-3 py-2.5 bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base focus:ring-brand focus:border-brand shadow-xs placeholder:text-body"
          ),
          mapWithEmptyElement.toSeq.map { case (v, d) =>
            option(value := v, d)
          },
          controlled(
            value <-- inputState.signal,
            onChange.mapToValue --> inputStateUpdater
          )
        )
      )
//      cls("has-error") <-- $errorEvents.map(_.nonEmpty),
//      cls("has-success") <-- $errorEvents.map(_.isEmpty),
//      children <-- $errorEvents.map(errorMessageElement)
    )

  def horizontalElement(textSize: ColumnSize = ColumSize.Column6): HtmlDiv =
    div(
      $errors --> errorState,
      inputSignal --> inputState,
      cls("form-group"),
      div(cls(textSize.withSmColumnText), label(cls("form-label"), labelValue)),
      div(
        cls(textSize.withSmColumnInput),
        select(
          cls("form-select"),
          mapWithEmptyElement.toSeq.map { case (v, d) =>
            option(value := v, d.toString)
          },
          controlled(
            value <-- inputState.signal,
            onChange.mapToValue --> inputStateUpdater
          )
        )
      ),
      cls("has-error") <-- $errorEvents.map(_.nonEmpty && inputNotEmpty),
      cls("has-success") <-- $errorEvents.map(_.isEmpty && inputNotEmpty),
      children <-- $errorEvents.map(errorMessageElement)
    )

  private def inputNotEmpty: Boolean =
    inputState.now().isEmpty

  private def errorMessageElement(errorMessages: Seq[String]): Seq[ReactiveHtmlElement[Paragraph]] =
    if (inputNotEmpty)
      errorMessages.map(message => p(cls("form-input-hint"), message))
    else
      Seq.empty
}

object TgSelect {
  implicit class TgSelectSyntax[A](formState: Var[A]) {
    def qcSelectPlain(
      inputSignal: A => String,
      inputStateUpdater: (A, String) => A,
      errorsFn: A => Seq[String] = _ => Seq.empty,
      labelValue: String,
      emptyValue: Option[String] = None,
      valuesAndDisplays: Map[String, String]
    ): TgSelect =
      TgSelect(
        inputSignal = formState.signal.map(inputSignal),
        inputStateUpdater = formState.updater(inputStateUpdater),
        $errors = formState.signal.changes.map(errorsFn),
        labelValue = labelValue,
        emptyValue = emptyValue,
        valuesAndDisplays = valuesAndDisplays
      )

    def tgSelectManualError(
      inputAndUpdater: SelectAndUpdate[A, String],
      errorsFn: A => Seq[String] = _ => Seq.empty,
      labelValue: String,
      emptyValue: Option[String] = None,
      valuesAndDisplays: Map[String, String]
    ): TgSelect =
      TgSelect(
        inputSignal = formState.signal.map(inputAndUpdater.selector),
        inputStateUpdater = formState.updater(inputAndUpdater.updater),
        $errors = formState.signal.changes.map(errorsFn),
        labelValue = labelValue,
        emptyValue = emptyValue,
        valuesAndDisplays = valuesAndDisplays
      )

    def tgSelectAutoError(
      inputAndUpdaterAndError: SelectAndUpdateAndError[A, String],
      labelValue: String,
      emptyValue: Option[String] = None,
      valuesAndDisplays: Map[String, String]
    ): TgSelect =
      TgSelect(
        inputSignal = formState.signal.map(inputAndUpdaterAndError.selector),
        inputStateUpdater = formState.updater(inputAndUpdaterAndError.updater),
        $errors = formState.signal.changes.map(inputAndUpdaterAndError.errorFn),
        labelValue = labelValue,
        emptyValue = emptyValue,
        valuesAndDisplays = valuesAndDisplays
      )

  }

}
