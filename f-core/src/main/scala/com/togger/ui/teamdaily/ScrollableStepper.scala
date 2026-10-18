package com.togger.ui.teamdaily

import com.raquo.laminar.api.L._
import com.togger.ui.organization.TgIcons
import org.scalajs.dom

/** Where a step sits in the scrolled content of its row. */
case class StepSpan(left: Double, right: Double)

object StepperScroll {

  private val Tolerance = 1.0

  def canScrollLeft(scrollLeft: Double): Boolean = scrollLeft > Tolerance

  def canScrollRight(scrollLeft: Double, viewWidth: Double, contentWidth: Double): Boolean =
    scrollLeft + viewWidth < contentWidth - Tolerance

  /** Scrolls so that the next `count` steps hidden on the right come into view, the last of them ending at the edge. */
  def targetRight(steps: Seq[StepSpan], scrollLeft: Double, viewWidth: Double, count: Int): Double = {
    val firstHidden = steps.indexWhere(_.right > scrollLeft + viewWidth + Tolerance)
    if (firstHidden < 0) return scrollLeft
    val last = steps(math.min(firstHidden + count - 1, steps.size - 1))
    math.max(last.right - viewWidth, 0.0)
  }

  /** Scrolls so that the previous `count` steps hidden on the left come into view, the first of them at the edge. */
  def targetLeft(steps: Seq[StepSpan], scrollLeft: Double, count: Int): Double = {
    val lastHidden = steps.lastIndexWhere(_.left < scrollLeft - Tolerance)
    if (lastHidden < 0) return scrollLeft
    steps(math.max(lastHidden - count + 1, 0)).left
  }
}

/** A stepper row with an arrow on each side, for scrolling it without a mouse when it is wider than the space it has.
  *
  * @param stepsPerClick
  *   how many steps one click on an arrow moves the row by
  * @param currentIndex
  *   the step brought to the middle of the row when it is shown
  */
class ScrollableStepper(stepper: HtmlElement, stepsPerClick: Int, currentIndex: Int) {

  private case class Overflow(scrollable: Boolean, left: Boolean, right: Boolean)

  private val overflow = Var(Overflow(scrollable = false, left = false, right = false))

  private def row: dom.HTMLElement = stepper.ref

  private def steps: Seq[dom.HTMLElement] = {
    val items = row.querySelectorAll("li")
    (0 until items.length).map(items.item(_).asInstanceOf[dom.HTMLElement])
  }

  private def spans: Seq[StepSpan] = steps.map(step => StepSpan(step.offsetLeft, step.offsetLeft + step.offsetWidth))

  private def refresh(): Unit =
    overflow.set(
      Overflow(
        scrollable = row.scrollWidth > row.clientWidth,
        left = StepperScroll.canScrollLeft(row.scrollLeft),
        right = StepperScroll.canScrollRight(row.scrollLeft, row.clientWidth, row.scrollWidth)
      )
    )

  private def centreCurrent(): Unit =
    steps.lift(currentIndex).foreach { step =>
      row.scrollLeft = step.offsetLeft - (row.clientWidth - step.offsetWidth) / 2.0
    }

  private def scrollTo(left: Double): Unit =
    row.asInstanceOf[scalajs.js.Dynamic].scrollTo(scalajs.js.Dynamic.literal(left = left, behavior = "smooth"))

  private val arrowCls =
    "shrink-0 inline-flex items-center justify-center w-8 h-8 text-body bg-neutral-secondary-medium border " +
      "border-default-medium hover:bg-neutral-tertiary-medium rounded-full cursor-pointer focus:ring-4 " +
      "focus:ring-brand-medium focus:outline-none disabled:opacity-40 disabled:cursor-not-allowed"

  private def arrow(labelText: String, icon: SvgElement, enabled: Signal[Boolean], onPress: () => Unit): HtmlElement =
    button(
      `type`("button"),
      cls(arrowCls),
      cls("invisible") <-- overflow.signal.map(!_.scrollable),
      aria.label(labelText),
      title(labelText),
      disabled <-- enabled.map(!_),
      icon,
      onClick.mapToUnit --> (_ => onPress())
    )

  val element: HtmlElement =
    div(
      cls("flex items-center gap-2"),
      arrow(
        "Scroll steps left",
        TgIcons.chevronLeftIcon("w-4 h-4"),
        overflow.signal.map(_.left).distinct,
        () => scrollTo(StepperScroll.targetLeft(spans, row.scrollLeft, stepsPerClick))
      ),
      stepper.amend(
        cls("relative min-w-0 flex-1"),
        onScroll --> (_ => refresh()),
        windowEvents(_.onResize) --> (_ => refresh()),
        // after the first layout, so the steps have their widths
        onMountCallback { _ =>
          dom.window.setTimeout(
            () => {
              centreCurrent()
              refresh()
            },
            0
          )
        }
      ),
      arrow(
        "Scroll steps right",
        TgIcons.chevronRightIcon("w-4 h-4"),
        overflow.signal.map(_.right).distinct,
        () => scrollTo(StepperScroll.targetRight(spans, row.scrollLeft, row.clientWidth, stepsPerClick))
      )
    )
}
