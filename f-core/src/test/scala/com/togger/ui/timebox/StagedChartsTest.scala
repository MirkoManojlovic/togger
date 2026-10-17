package com.togger.ui.timebox

import com.togger.chart.ChartColor
import com.togger.ui.apexchart.{ApexChartType, ApexSeries}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class StagedChartsTest extends AnyWordSpec with Matchers {

  private def group(title: String, taskCount: Int) =
    GroupSummary(title, taskCount, 0, Seq.empty, Seq.empty, Seq.empty, Seq.empty, Seq.empty)

  "StagedCharts.pieOfTaskCounts" should {
    "make one slice per group sized by its task count" in {
      val options = StagedCharts.pieOfTaskCounts(Seq(group("Milestone 01", 3), group("No milestone", 1)))
      options.chartType shouldBe ApexChartType.Pie
      options.series shouldBe ApexSeries.Slices(Seq(3, 1), Seq("Milestone 01", "No milestone"))
    }

    "give every milestone its own color and the tasks without one gray" in {
      val groups = (1 to 12).map(number => group(s"Milestone $number", 1)) :+ group(StagedGrouping.NoMilestone, 1)
      val colors = StagedCharts.pieOfTaskCounts(groups).colors.get
      colors.take(10).distinct should have size 10
      colors(10) shouldBe colors(0)
      colors.last shouldBe "#9ca3af"
    }

    "color a group by what it groups by, the same in every chart" in {
      val groups = Seq(
        group("🔴 Blocked", 1).copy(chartColor = Some(ChartColor.Red)),
        group("▶️ In progress", 1).copy(chartColor = Some(ChartColor.Blue)),
        group("Custom", 1)
      )
      val options = StagedCharts.pieOfTaskCounts(groups)
      options.colors.get.take(2) shouldBe Seq(ChartColor.Red.dark, ChartColor.Blue.dark)
      options.lightColors.get.take(2) shouldBe Seq(ChartColor.Red.light, ChartColor.Blue.light)
    }

    "have a lighter variant of each color for light mode" in {
      val groups = Seq(group("Milestone 01", 1), group(StagedGrouping.NoMilestone, 1))
      val options = StagedCharts.pieOfTaskCounts(groups)
      options.lightColors.get should have size options.colors.get.size
      options.lightColors.get should not be options.colors.get
    }
  }
}
