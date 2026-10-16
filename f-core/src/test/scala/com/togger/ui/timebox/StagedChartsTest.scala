package com.togger.ui.timebox

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
  }
}
