package com.togger.ui.timebox

import com.togger.ui.apexchart.{ApexChartOptions, ApexChartType, ApexLegendPosition, ApexSeries}

/** The charts of the staged stats, as chart options. */
object StagedCharts {

  val ChartHeight = 320

  private val SliceColors = Seq(
    "#1447E6", "#16a34a", "#f59e0b", "#dc2626", "#9333ea", "#0891b2", "#db2777", "#65a30d", "#ea580c", "#4f46e5"
  )
  private val NoGroupColor = "#9ca3af"
  private val NoGroupTitles = Set(StagedGrouping.NoAssignee, StagedGrouping.NoMilestone, StagedGrouping.NoLabel)

  /** The groups get a color each, round and round; the tasks that are in no group are gray. */
  private def sliceColors(groups: Seq[GroupSummary]): Seq[String] =
    groups.foldLeft((Seq.empty[String], 0)) { case ((colors, used), group) =>
      if (NoGroupTitles.contains(group.title)) (colors :+ NoGroupColor, used)
      else (colors :+ SliceColors(used % SliceColors.size), used + 1)
    }._1

  /** One slice per group, as big as the number of tasks in it. */
  def pieOfTaskCounts(groups: Seq[GroupSummary]): ApexChartOptions =
    ApexChartOptions(
      chartType = ApexChartType.Pie,
      height = ChartHeight,
      series = ApexSeries.Slices(groups.map(_.taskCount), groups.map(_.title)),
      colors = Some(sliceColors(groups)),
      legendPosition = Some(ApexLegendPosition.Bottom),
      nameInDataLabels = true
    )
}
