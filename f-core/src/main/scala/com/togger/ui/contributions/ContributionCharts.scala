package com.togger.ui.contributions

import com.togger.chart.ChartColor
import com.togger.dto.MemberContributionDto
import com.togger.ui.apexchart.{
  ApexChartOptions,
  ApexChartType,
  ApexLegendPosition,
  ApexNamedSeries,
  ApexSeries,
  ApexStroke
}
import com.togger.ui.timebox.StagedCharts

import java.time.LocalDate

/** The charts of the contributions of the members, as chart options. A member has the same color in every chart that is
  * made of the same list of members, by their position in it.
  */
object ContributionCharts {

  val ChartHeight = 320

  /** Whether there is anything to draw: a chart of only zeros is an empty page, not a chart. */
  def hasData(options: ApexChartOptions): Boolean = options.series match {
    case ApexSeries.Axis(series, _)   => series.exists(_.data.exists(_ != 0))
    case ApexSeries.Slices(values, _) => values.exists(_ != 0)
  }

  private def withColors(options: ApexChartOptions, colors: Seq[ChartColor]): ApexChartOptions =
    options.copy(colors = Some(colors.map(_.dark)), lightColors = Some(colors.map(_.light)))

  private def workEntries(member: MemberContributionDto): Int = member.dailyEntries.map(_.count).sum

  private def columns(
    members: Seq[MemberContributionDto],
    series: Seq[(String, ChartColor, MemberContributionDto => Int)],
    stacked: Boolean
  ): ApexChartOptions =
    withColors(
      ApexChartOptions(
        chartType = ApexChartType.Column,
        height = ChartHeight,
        series = ApexSeries.Axis(
          series.map { case (name, _, valueOf) => ApexNamedSeries(name, members.map(valueOf)) },
          members.map(_.memberName)
        ),
        legendPosition = Some(ApexLegendPosition.Bottom),
        dataLabelsEnabled = !stacked,
        stacked = stacked
      ),
      series.map(_._2)
    )

  /** A column per member, stacked by the status of the log entries, with every status that someone has used. */
  def entriesByStatus(members: Seq[MemberContributionDto]): ApexChartOptions = {
    val statuses = members.flatMap(_.entriesByStatus.map(_.status)).distinct.sortBy(_.value)
    columns(
      members,
      statuses.map(status =>
        (
          status.label,
          status.chartColor,
          (member: MemberContributionDto) => member.entriesByStatus.find(_.status == status).map(_.count).getOrElse(0)
        )
      ),
      stacked = true
    )
  }

  /** A slice per member that worked, as big as the member's share of the worked log entries. */
  def teamShare(members: Seq[MemberContributionDto]): ApexChartOptions = {
    val workers = members.filter(workEntries(_) > 0)
    val colors = workers.indices.map(index => StagedCharts.SliceColors(index % StagedCharts.SliceColors.size))
    ApexChartOptions(
      chartType = ApexChartType.Donut,
      height = ChartHeight,
      series = ApexSeries.Slices(workers.map(workEntries), workers.map(_.memberName)),
      colors = Some(colors.map(_.dark)),
      lightColors = Some(colors.map(_.light)),
      legendPosition = Some(ApexLegendPosition.Bottom)
    )
  }

  /** A line per member that worked, of the log entries on every day that anyone worked; a quiet day is a zero. */
  def dailyTrend(members: Seq[MemberContributionDto], formatDate: LocalDate => String): ApexChartOptions = {
    val workers = members.filter(workEntries(_) > 0)
    val days = workers.flatMap(_.dailyEntries.map(_.date)).distinct.sorted
    val colors = workers.indices.map(index => StagedCharts.SliceColors(index % StagedCharts.SliceColors.size))
    ApexChartOptions(
      chartType = ApexChartType.Area,
      height = ChartHeight,
      series = ApexSeries.Axis(
        workers.map { member =>
          val countsByDay = member.dailyEntries.map(entry => entry.date -> entry.count).toMap
          ApexNamedSeries(member.memberName, days.map(countsByDay.getOrElse(_, 0)))
        },
        days.map(formatDate)
      ),
      colors = Some(colors.map(_.dark)),
      lightColors = Some(colors.map(_.light)),
      stroke = Some(ApexStroke(smooth = true, width = 2)),
      legendPosition = Some(ApexLegendPosition.Bottom),
      dataLabelsEnabled = false
    )
  }

  /** Two columns per member, the tasks assigned to them and the tasks they closed. */
  def tasksAssignedAndClosed(members: Seq[MemberContributionDto]): ApexChartOptions =
    columns(
      members,
      Seq(("Assigned", ChartColor.Blue, _.tasksAssigned), ("Closed", ChartColor.Green, _.tasksClosed)),
      stacked = false
    )

  /** Two columns per member, the days they worked next to the work days of the range. */
  def activeDays(members: Seq[MemberContributionDto]): ApexChartOptions =
    columns(
      members,
      Seq(("Active days", ChartColor.Blue, _.activeDays), ("Work days", ChartColor.Gray, _.workDaysInRange)),
      stacked = false
    )
}
