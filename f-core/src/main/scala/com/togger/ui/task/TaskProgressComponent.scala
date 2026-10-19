package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement
import com.togger.markdown.{TaskProgress, TaskProgressParser}
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import org.scalajs.dom.Element

sealed trait TaskProgressVariant

object TaskProgressVariant {

  /** A small ring, filled clockwise from the top, beside a "n of m tasks" label. */
  case object Doughnut extends TaskProgressVariant
}

/** Renders a task's checkbox progress, in whichever shape `variant` asks for.
  *
  * Nothing renders when the description has no task list (`TaskProgress.total == 0`) — that is "no progress to
  * show", not "0%".
  */
case class TaskProgressComponent(
  progressSignal: Signal[TaskProgress],
  variant: TaskProgressVariant = TaskProgressVariant.Doughnut,
  ringSizeCls: String = "w-8 h-8",
  textSizeCls: String = "text-sm"
) {

  val element: HtmlDiv =
    div(
      cls("inline-flex items-center gap-2"),
      children <-- progressSignal.map(renderChildren)
    )

  private def renderChildren(progress: TaskProgress): Seq[ReactiveElement[Element]] =
    if (progress.total == 0) Seq.empty
    else
      variant match {
        case TaskProgressVariant.Doughnut =>
          Seq(
            doughnutRing(progress.percent),
            span(cls(textSizeCls, "text-body whitespace-nowrap"), s"${progress.checked} of ${progress.total} tasks")
          )
      }

  /** `r = 15.9155` makes the circle's circumference exactly 100, so a percent maps straight onto
    * `strokeDashArray` with no extra math. `-rotate-90` on the wrapper starts the fill at 12 o'clock instead of
    * SVG's default 3 o'clock.
    */
  private def doughnutRing(percent: Int) =
    svg.svg(
      svg.className(s"$ringSizeCls -rotate-90"),
      svg.viewBox("0 0 36 36"),
      svg.circle(
        svg.cx("18"),
        svg.cy("18"),
        svg.r("15.9155"),
        svg.fill("none"),
        svg.stroke("currentColor"),
        svg.className("text-blue-200 dark:text-blue-900"),
        svg.strokeWidth("5")
      ),
      svg.circle(
        svg.cx("18"),
        svg.cy("18"),
        svg.r("15.9155"),
        svg.fill("none"),
        svg.stroke("currentColor"),
        svg.className("text-fg-brand"),
        svg.strokeWidth("5"),
        svg.strokeLineCap("round"),
        svg.strokeDashArray(s"$percent 100")
      )
    )
}

object TaskProgressComponent {

  def fromDescription(
    descriptionSignal: Signal[String],
    variant: TaskProgressVariant = TaskProgressVariant.Doughnut,
    ringSizeCls: String = "w-8 h-8",
    textSizeCls: String = "text-sm"
  ): TaskProgressComponent =
    TaskProgressComponent(descriptionSignal.map(TaskProgressParser.parse), variant, ringSizeCls, textSizeCls)
}
