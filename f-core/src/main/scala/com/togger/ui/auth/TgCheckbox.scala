package com.togger.ui.auth

import com.togger.ui.auth.TgCheckboxType.{RegularCheckbox, Switch}
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.raquo.laminar.api.L._
import enumeratum.{EnumEntry, _}

sealed trait TgCheckboxType extends EnumEntry

object TgCheckboxType extends Enum[TgCheckboxType] {
  val values: IndexedSeq[TgCheckboxType] = findValues

  case object Switch extends TgCheckboxType
  case object RegularCheckbox extends TgCheckboxType

}

case class TgCheckbox(
  inputSignal: Signal[Boolean],
  inputStateUpdater: Observer[Boolean],
  labelValue: String,
  tgCheckboxType: TgCheckboxType = RegularCheckbox,
  isRequired: Boolean = false
) {

  val htmlElement: HtmlDiv =
    div(
      cls(
        "inline-flex items-center w-full hover:bg-neutral-tertiary-medium hover:text-heading rounded-md cursor-pointer"
      ),
      label(
        cls("inline-flex items-center w-full cursor-pointer p-2"),
        input(
          `type` := "checkbox",
          checked <-- inputSignal,
          onClick.mapToChecked --> inputStateUpdater,
          cls(
            "w-4 h-4 border border-default-strong rounded-xs bg-neutral-secondary-strong focus:ring-2 focus:ring-brand-soft"
          )
        ),
        span(cls("w-full ms-2 text-sm font-medium text-heading"), labelValue)
      )
    )

  def horizontalElement(textSize: ColumnSize = ColumSize.Column6): HtmlDiv =
    div(
      cls("form-group"),
      div(
        cls(s"${textSize.withSmColumnInput} col-ml-auto"),
        label(
          cls(checkBoxClass(tgCheckboxType)),
          input(
            `type` := "checkbox",
            checked <-- inputSignal,
            onClick.mapToChecked --> inputStateUpdater
          ),
          i(cls("form-icon")),
          labelValue
        )
      )
    )

  private def checkBoxClass(tgCheckboxType: TgCheckboxType): String = {
    tgCheckboxType match {
      case Switch          => "form-switch"
      case RegularCheckbox => "form-checkbox"
    }
  }

}

object TgCheckbox {
  def checkBox(
    inputSignal: Signal[Boolean],
    inputStateUpdater: Observer[Boolean],
    labelValue: String,
    isRequired: Boolean = false
  ): TgCheckbox =
    TgCheckbox(inputSignal, inputStateUpdater, labelValue, RegularCheckbox, isRequired)

  def switch(
    inputSignal: Signal[Boolean],
    inputStateUpdater: Observer[Boolean],
    labelValue: String,
    isRequired: Boolean = false
  ): TgCheckbox =
    TgCheckbox(inputSignal, inputStateUpdater, labelValue, Switch, isRequired)

  implicit class TgCheckboxSyntax[A](formState: Var[A]) {
    def tgCheckBox(
      inputSignal: Signal[Boolean],
      inputStateUpdater: Observer[Boolean],
      labelValue: String,
      isRequired: Boolean = false
    ): TgCheckbox =
      TgCheckbox(inputSignal, inputStateUpdater, labelValue, RegularCheckbox, isRequired)

    def tgSwitch(
      inputSignal: Signal[Boolean],
      inputStateUpdater: Observer[Boolean],
      labelValue: String,
      isRequired: Boolean = false
    ): TgCheckbox =
      TgCheckbox(inputSignal, inputStateUpdater, labelValue, Switch, isRequired)

//    def tgCheckBoxCombined(
//      inputAndUpdater: SelectAndUpdate[A, Boolean],
//      labelValue: String,
//      isRequired: Boolean = false
//    ): TgCheckbox =
//      TgCheckbox(
//        inputSignal = formState.signal.map(inputAndUpdater.selector),
//        inputStateUpdater = formState.updater(inputAndUpdater.updater),
//        labelValue = labelValue,
//        tgCheckboxType = RegularCheckbox,
//        isRequired = isRequired
//      )

//    def tgSwitchAuto(
//      inputAndUpdater: SelectAndUpdate[A, Boolean],
//      labelValue: String,
//      isRequired: Boolean = false
//    ): TgCheckbox =
//      TgCheckbox(
//        inputSignal = formState.signal.map(inputAndUpdater.selector),
//        inputStateUpdater = formState.updater(inputAndUpdater.updater),
//        labelValue = labelValue,
//        tgCheckboxType = Switch,
//        isRequired = isRequired
//      )

  }
}
