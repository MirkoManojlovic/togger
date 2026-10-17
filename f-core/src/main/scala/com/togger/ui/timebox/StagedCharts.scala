package com.togger.ui.timebox

import com.togger.ui.apexchart.{ApexChartOptions, ApexChartType, ApexLegendPosition, ApexSeries}

/** The charts of the staged stats, as chart options. */
object StagedCharts {

  val ChartHeight = 320

  /** The colors of a slice, one for a dark background and a lighter one for a light background. */
  private case class SliceColor(dark: String, light: String)

  private val SliceColors = Seq(
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

  /** The groups get a color each, round and round; the tasks that are in no group are gray. */
  private def sliceColors(titles: Seq[String]): Seq[SliceColor] =
    titles.foldLeft((Seq.empty[SliceColor], 0)) { case ((colors, used), title) =>
      if (NoGroupTitles.contains(title)) (colors :+ NoGroupColor, used)
      else (colors :+ SliceColors(used % SliceColors.size), used + 1)
    }._1

  /** One slice per group, as big as the number of tasks in it. */
  def pieOfTaskCounts(groups: Seq[GroupSummary]): ApexChartOptions =
    pie(groups.map(group => group.title -> group.taskCount))

  /** One slice per group of tasks, as big as the number of tasks in it. */
  def pieOfGroups(groups: Seq[StagedGroup]): ApexChartOptions =
    pie(groups.map(group => group.title -> group.tasks.size))

  private def pie(slices: Seq[(String, Int)]): ApexChartOptions = {
    val colors = sliceColors(slices.map(_._1))
    ApexChartOptions(
      chartType = ApexChartType.Pie,
      height = ChartHeight,
      series = ApexSeries.Slices(slices.map(_._2), slices.map(_._1)),
      colors = Some(colors.map(_.dark)),
      lightColors = Some(colors.map(_.light)),
      legendPosition = Some(ApexLegendPosition.Bottom),
      nameInDataLabels = true
    )
  }
}
