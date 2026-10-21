package com.togger.ui.contributions

import com.togger.chart.ChartColor
import com.togger.dto.MemberContributionDto
import com.togger.ui.apexchart.{ApexChartOptions, ApexChartType, ApexLegendPosition, ApexNamedSeries, ApexSeries}
import com.togger.ui.timebox.StagedCharts

import java.time.LocalDate

/** The charts of the contributions of the members, as chart options. A member has the same color in every chart that is
  * made of the same list of members, by their position in it.
  */
object ContributionCharts {

  val ChartHeight = 320

  /** For a chart that has a whole row to itself. */
  val LargeChartHeight = 480

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

  /** A slice per status that someone has used, as big as the number of log entries with that status. */
  def entriesByStatus(members: Seq[MemberContributionDto]): ApexChartOptions = {
    val countsByStatus = members.flatMap(_.entriesByStatus).groupMapReduce(_.status)(_.count)(_ + _)
    val statuses = countsByStatus.keys.toSeq.sortBy(_.value)
    ApexChartOptions(
      chartType = ApexChartType.Pie,
      height = ChartHeight,
      series = ApexSeries.Slices(statuses.map(countsByStatus), statuses.map(_.label)),
      colors = Some(statuses.map(_.chartColor.dark)),
      lightColors = Some(statuses.map(_.chartColor.light)),
      legendPosition = Some(ApexLegendPosition.Bottom),
      nameInDataLabels = true
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

  /** A column per day that anyone worked, stacked by the status of the log entries of all the members; a day without
    * a status is a zero.
    */
  def dailyTrend(members: Seq[MemberContributionDto], formatDate: LocalDate => String): ApexChartOptions = {
    val entries = members.flatMap(_.dailyEntries)
    val days = entries.map(_.date).distinct.sorted
    val statuses = entries.map(_.status).distinct.sortBy(_.value)
    val countsByDayAndStatus = entries.groupMapReduce(entry => (entry.date, entry.status))(_.count)(_ + _)
    withColors(
      ApexChartOptions(
        chartType = ApexChartType.Column,
        height = LargeChartHeight,
        series = ApexSeries.Axis(
          statuses.map(status =>
            ApexNamedSeries(status.label, days.map(day => countsByDayAndStatus.getOrElse((day, status), 0)))
          ),
          days.map(formatDate)
        ),
        legendPosition = Some(ApexLegendPosition.Bottom),
        dataLabelsEnabled = false,
        stacked = true
      ),
      statuses.map(_.chartColor)
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
