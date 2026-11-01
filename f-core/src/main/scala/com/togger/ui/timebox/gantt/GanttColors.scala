package com.togger.ui.timebox.gantt

import com.raquo.laminar.api.L._

/** The tasks of a bar's step: how many there are and how many of them are done. */
case class GanttBarProgress(total: Int, done: Int) {
  def fraction: Double = if (total <= 0) 0.0 else (done.toDouble / total).min(1.0)
}

/** How the bar of a Gantt chart row is coloured. `index` is the place of the row, `progress` the tasks of the row. */
trait GanttColors {
  def bar(
    layout: GanttChartLayout,
    tooltip: String,
    index: Int,
    progress: GanttBarProgress,
    placed: GanttBar.Placed
  ): HtmlElement
}

/** A solid bar, each row in a different colour of the palette. */
object DiffColors extends GanttColors {
  override def bar(
    layout: GanttChartLayout,
    tooltip: String,
    index: Int,
    progress: GanttBarProgress,
    placed: GanttBar.Placed
  ): HtmlElement =
    layout.barElement(tooltip, GanttAssigneeColors.Palette(index % GanttAssigneeColors.Palette.size), placed)
}

/** The colour of the row as DiffColors has it, filled to the share of the tasks that are done: with no task done the
  * bar is the colour with more opacity, with all of them done it is solid. In the middle it shows the number of tasks
  * and the number of those done, in dark text on a bright colour and in light text on a dark one.
  */
object Percentages extends GanttColors {

  /** Written out in full so Tailwind finds the classes. */
  private val BrightColors: Set[String] = Set(
    "bg-amber-600",
    "bg-lime-600",
    "bg-yellow-600",
    "bg-violet-400",
    "bg-sky-500",
    "bg-cyan-600",
    "bg-pink-500",
    "bg-stone-500",
    "bg-orange-600"
  )

  def textClass(color: String): String = if (BrightColors.contains(color)) "text-gray-900" else "text-white"

  override def bar(
    layout: GanttChartLayout,
    tooltip: String,
    index: Int,
    progress: GanttBarProgress,
    placed: GanttBar.Placed
  ): HtmlElement = {
    val color = GanttAssigneeColors.Palette(index % GanttAssigneeColors.Palette.size)
    val percent = math.round(progress.fraction * 100)
    div(
      cls(s"relative z-10 mx-1 my-1 flex h-7 items-center justify-center overflow-hidden text-xs ${layout.barRounding(placed)}"),
      title := s"$tooltip (${progress.done} of ${progress.total} tasks done)",
      div(cls(s"absolute inset-0 opacity-30 $color")),
      div(cls(s"absolute inset-y-0 left-0 $color"), styleAttr := s"width: $percent%"),
      span(
        cls(s"relative z-10 rounded px-1 font-medium $color ${textClass(color)}"),
        s"${progress.total} / ${progress.done}"
      )
    )
  }
}
