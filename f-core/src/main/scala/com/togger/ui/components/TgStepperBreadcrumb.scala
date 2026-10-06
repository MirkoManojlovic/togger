package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.ui.organization.TgIcons
import org.scalajs.dom.HTMLElement
import com.raquo.laminar.tags.HtmlTag

/** One step. The first word of `label` is always shown, the rest only from the `sm` breakpoint up. */
case class TgStep(label: String)

sealed trait TgStepState

object TgStepState {
  case object Reached extends TgStepState
  case object Upcoming extends TgStepState
}

/** Flowbite breadcrumb stepper for Laminar.
  *
  * Steps up to and including `current` (0-based) are brand coloured. Clicking any step reports its index to `onSelect`;
  * the caller updates `current`.
  */
class TgStepperBreadcrumb(steps: Seq[TgStep], current: Signal[Int], onSelect: Observer[Int]) {

  private val listCls =
    "flex items-center w-full p-3 space-x-2 text-sm font-medium text-center text-body bg-neutral-primary-soft " +
      "border border-default rounded-base shadow-xs sm:p-4 sm:space-x-4 rtl:space-x-reverse"
  private val buttonCls = "flex items-center cursor-pointer hover:text-fg-brand"
  private val badgeCls = "flex items-center justify-center w-5 h-5 me-2 text-xs border rounded-full shrink-0"
  private val restOfLabelCls = "hidden sm:inline-flex ms-2"
  private val separatorCls = "w-5 h-5 ms-2 rtl:rotate-180"

  private val navTag = new HtmlTag[HTMLElement]("nav")

  val element: HtmlElement =
    navTag(
      aria.label("Progress"),
      ol(cls(listCls), steps.zipWithIndex.map { case (step, index) => stepItem(step, index) })
    )

  private def stepItem(step: TgStep, index: Int): HtmlElement = {
    val reached = current.map(TgStepperBreadcrumb.stateOf(index, _) == TgStepState.Reached)
    val (firstWord, rest) = TgStepperBreadcrumb.splitLabel(step.label)
    li(
      aria.current <-- current.map(currentStep => if (currentStep == index) "step" else null),
      button(
        typ("button"),
        cls(buttonCls),
        cls("text-fg-brand") <-- reached,
        onClick.mapTo(index) --> onSelect,
        span(
          cls(badgeCls),
          cls("border-brand") <-- reached,
          cls("border-body") <-- reached.map(!_),
          (index + 1).toString
        ),
        firstWord,
        rest.map(text => span(cls(restOfLabelCls), text)),
        Option.when(index < steps.size - 1)(TgIcons.chevronDoubleRightIcon(separatorCls).amend(aria.hidden(true)))
      )
    )
  }

}

object TgStepperBreadcrumb {

  def stateOf(index: Int, current: Int): TgStepState =
    if (index <= current) TgStepState.Reached else TgStepState.Upcoming

  def splitLabel(label: String): (String, Option[String]) =
    label.split(" ", 2) match {
      case Array(head, tail) => (head, Some(tail))
      case _                 => (label, None)
    }

}
