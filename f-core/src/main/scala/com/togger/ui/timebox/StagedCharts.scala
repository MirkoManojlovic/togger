package com.togger.ui.timebox

import com.togger.chart.ChartColor
import com.togger.ui.apexchart.{ApexChartOptions, ApexChartType, ApexLegendPosition, ApexSeries}

/** The charts of the staged stats, as chart options. */
object StagedCharts {

  val ChartHeight = 320

  /** The colors of a slice, one for a dark background and a lighter one for a light background. */
  case class SliceColor(dark: String, light: String)

  val SliceColors: Seq[SliceColor] = Seq(
    SliceColor("#1447E6", "#60A5FA"),
    SliceColor("#16a34a", "#4ADE80"),
    SliceColor("#f59e0b", "#FBBF24"),
    SliceColor("#dc2626", "#F87171"),
    SliceColor("#9333ea", "#C084FC"),
    SliceColor("#0891b2", "#22D3EE"),
    SliceColor("#db2777", "#F472B6"),
    SliceColor("#65a30d", "#A3E635"),
    SliceColor("#ea580c", "#FB923C"),
    SliceColor("#4f46e5", "#818CF8")
  )
  private val NoGroupColor = SliceColor("#9ca3af", "#D1D5DB")
  private val NoGroupTitles = Set(StagedGrouping.NoAssignee, StagedGrouping.NoMilestone, StagedGrouping.NoLabel)

  /** A group whose thing has a chart color of its own gets that one, so it looks the same here as in every other chart
    * and badge. The other groups get a color each, round and round; the tasks that are in no group are gray.
    */
  private def sliceColors(slices: Seq[Slice]): Seq[SliceColor] =
    slices.foldLeft((Seq.empty[SliceColor], 0)) { case ((colors, used), slice) =>
      slice.chartColor match {
        case Some(color)                                   => (colors :+ SliceColor(color.dark, color.light), used)
        case None if NoGroupTitles.contains(slice.title) => (colors :+ NoGroupColor, used)
        case None => (colors :+ SliceColors(used % SliceColors.size), used + 1)
      }
    }._1

  case class Slice(title: String, size: Int, chartColor: Option[ChartColor])

  /** One slice per group, as big as the number of tasks in it. */
  def pieOfTaskCounts(groups: Seq[GroupSummary]): ApexChartOptions =
    pie(groups.map(group => Slice(group.title, group.taskCount, group.chartColor)))

  /** One slice per group of tasks, as big as the number of tasks in it. */
  def pieOfGroups(groups: Seq[StagedGroup]): ApexChartOptions =
    pie(groups.map(group => Slice(group.title, group.tasks.size, group.chartColor)))

  def pie(slices: Seq[Slice]): ApexChartOptions = {
    val colors = sliceColors(slices)
    ApexChartOptions(
      chartType = ApexChartType.Pie,
      height = ChartHeight,
      series = ApexSeries.Slices(slices.map(_.size), slices.map(_.title)),
      colors = Some(colors.map(_.dark)),
      lightColors = Some(colors.map(_.light)),
      legendPosition = Some(ApexLegendPosition.Bottom),
      nameInDataLabels = true
    )
  }
}
