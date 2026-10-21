package com.togger.ui.contributions

import com.togger.chart.ChartColor
import com.togger.dto.{DailyCountDto, MemberContributionDto, StatusCountDto}
import com.togger.logentry.LogEntryStatus
import com.togger.ui.apexchart.{ApexChartType, ApexNamedSeries, ApexSeries}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate
import java.util.UUID

class ContributionChartsTest extends AnyWordSpec with Matchers {

  private def day(dayOfMonth: Int): LocalDate = LocalDate.of(2026, 10, dayOfMonth)

  private def member(name: String): MemberContributionDto =
    MemberContributionDto(UUID.randomUUID(), name, List.empty, 0, 0, 0, 0, List.empty)

  private val ann = member("Ann Adams").copy(
    entriesByStatus = List(StatusCountDto(LogEntryStatus.InProgress, 1), StatusCountDto(LogEntryStatus.Completed, 2)),
    tasksAssigned = 3,
    tasksClosed = 2,
    activeDays = 2,
    workDaysInRange = 5,
    dailyEntries = List(DailyCountDto(day(13), 2), DailyCountDto(day(15), 1))
  )
  private val bob = member("Bob Brown").copy(
    entriesByStatus = List(StatusCountDto(LogEntryStatus.Blocked, 4)),
    tasksAssigned = 1,
    activeDays = 1,
    workDaysInRange = 5,
    dailyEntries = List(DailyCountDto(day(14), 4))
  )
  private val idle = member("Cy Clark")
  private val everyone = Seq(ann, bob, idle)

  private def axisOf(series: ApexSeries): ApexSeries.Axis = series match {
    case axis: ApexSeries.Axis => axis
    case other                 => fail(s"Expected axis series but got $other")
  }

  "ContributionCharts.entriesByStatus" should {

    "make a pie slice per status in use, summed over the members, in the order of the status values" in {
      val options = ContributionCharts.entriesByStatus(everyone :+ ann)
      options.chartType shouldBe ApexChartType.Pie
      options.series shouldBe ApexSeries.Slices(Seq(2, 4, 4), Seq("In progress", "Blocked", "Completed"))
    }

    "color a status by its chart color, in both themes" in {
      val options = ContributionCharts.entriesByStatus(everyone)
      options.colors shouldBe Some(Seq(ChartColor.Blue.dark, ChartColor.Red.dark, ChartColor.Green.dark))
      options.lightColors shouldBe Some(Seq(ChartColor.Blue.light, ChartColor.Red.light, ChartColor.Green.light))
    }

    "have no slices for no members" in {
      ContributionCharts.entriesByStatus(Seq.empty).series shouldBe ApexSeries.Slices(Seq.empty, Seq.empty)
    }
  }

  "ContributionCharts.teamShare" should {

    "make a donut slice per member that worked, sized by the worked entries" in {
      val options = ContributionCharts.teamShare(everyone)
      options.chartType shouldBe ApexChartType.Donut
      options.series shouldBe ApexSeries.Slices(Seq(3, 4), Seq("Ann Adams", "Bob Brown"))
      options.colors.get should have size 2
      options.lightColors.get should have size 2
    }

    "leave days off out of the share" in {
      val onVacation = member("Dee Dunn").copy(entriesByStatus = List(StatusCountDto(LogEntryStatus.Vacation, 3)))
      ContributionCharts.teamShare(Seq(onVacation)).series shouldBe ApexSeries.Slices(Seq.empty, Seq.empty)
    }
  }

  "ContributionCharts.dailyTrend" should {

    "make a line per member that worked over every day anyone worked, with zero for a quiet day" in {
      val options = ContributionCharts.dailyTrend(everyone, _.toString)
      options.chartType shouldBe ApexChartType.Area
      axisOf(options.series) shouldBe ApexSeries.Axis(
        Seq(ApexNamedSeries("Ann Adams", Seq(2, 0, 1)), ApexNamedSeries("Bob Brown", Seq(0, 4, 0))),
        Seq("2026-10-13", "2026-10-14", "2026-10-15")
      )
    }

    "write the days with the given format" in {
      val options = ContributionCharts.dailyTrend(Seq(bob), date => s"${date.getDayOfMonth}.${date.getMonthValue}.")
      axisOf(options.series).categories shouldBe Seq("14.10.")
    }
  }

  "ContributionCharts.tasksAssignedAndClosed" should {
    "put the assigned and the closed tasks side by side" in {
      val options = ContributionCharts.tasksAssignedAndClosed(everyone)
      options.stacked shouldBe false
      axisOf(options.series).series shouldBe Seq(
        ApexNamedSeries("Assigned", Seq(3, 1, 0)),
        ApexNamedSeries("Closed", Seq(2, 0, 0))
      )
    }
  }

  "ContributionCharts.activeDays" should {
    "put the active days next to the work days of the range" in {
      axisOf(ContributionCharts.activeDays(everyone).series).series shouldBe Seq(
        ApexNamedSeries("Active days", Seq(2, 1, 0)),
        ApexNamedSeries("Work days", Seq(5, 5, 0))
      )
    }
  }
}
