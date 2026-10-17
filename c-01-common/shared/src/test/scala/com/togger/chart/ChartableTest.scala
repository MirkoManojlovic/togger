package com.togger.chart

import com.togger.logentry.LogEntryStatus
import com.togger.milestone.MilestoneStatus
import com.togger.task.{DefaultTaskLabel, DefaultTaskStatus, TaskPriority}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ChartableTest extends AnyWordSpec with Matchers {

  "ChartColor" should {
    "have a #RRGGBB hex for light and dark backgrounds" in {
      ChartColor.values.foreach { color =>
        color.light should fullyMatch regex "#[0-9A-F]{6}"
        color.dark should fullyMatch regex "#[0-9A-F]{6}"
      }
    }
  }

  "Blocked" should {
    "be the same color wherever it is drawn" in {
      TaskStatusChartable.of(DefaultTaskStatus.Blocked.statusName).chartColor shouldBe ChartColor.Red
      LogEntryStatus.Blocked.chartColor shouldBe ChartColor.Red
    }
  }

  "In progress" should {
    "be the same color wherever it is drawn" in {
      val colors = Seq(
        TaskStatusChartable.of(DefaultTaskStatus.InProgress.statusName).chartColor,
        LogEntryStatus.InProgress.chartColor,
        MilestoneStatus.InProgress.chartColor
      )
      colors.distinct shouldBe Seq(ChartColor.Blue)
    }
  }

  "Every chartable" should {
    "have a unique key within its kind" in {
      val kinds: Seq[Seq[Chartable]] = Seq(
        TaskStatusChartable.all,
        TaskLabelChartable.all,
        LogEntryStatus.values,
        MilestoneStatus.values,
        TaskPriority.values
      )
      kinds.foreach(kind => kind.map(_.key).distinct should have size kind.size.toLong)
    }
  }

  "TaskStatusChartable.of" should {
    "find the chartable of every default status by name" in {
      DefaultTaskStatus.values.foreach { status =>
        TaskStatusChartable.of(status.statusName).label shouldBe status.statusName
      }
      TaskStatusChartable.all should have size DefaultTaskStatus.values.size.toLong
    }

    "make a renamed or custom status gray" in {
      val custom = TaskStatusChartable.of("Needs review")
      custom.chartColor shouldBe ChartColor.Gray
      custom.label shouldBe "Needs review"
    }
  }

  "TaskLabelChartable.of" should {
    "find the chartable of every default label by name" in {
      DefaultTaskLabel.values.foreach { label =>
        TaskLabelChartable.of(label.labelName).label shouldBe label.labelName
      }
      TaskLabelChartable.all should have size DefaultTaskLabel.values.size.toLong
    }

    "make an unknown label gray" in {
      TaskLabelChartable.of("custom").chartColor shouldBe ChartColor.Gray
    }
  }

}
