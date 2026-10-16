package com.togger.ui.timebox.gantt

import com.togger.common.WorkDayCalendar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class GanttResizeTest extends AnyWordSpec with Matchers {

  private val monday = LocalDate.of(2026, 10, 12)
  // ten columns over two weeks; the second Monday is column 5
  private val axis = GanttAxis.of(monday, monday.plusDays(13), WorkDayCalendar.DefaultWorkDays)
  private val bar = GanttBar.Placed(2, 5, clippedLeft = false, clippedRight = false)

  "GanttResize.resize" should {
    "move the start edge" in {
      GanttResize.resize(bar, GanttEdge.Start, -2, axis) shouldBe bar.copy(startCol = 0)
    }

    "move the end edge" in {
      GanttResize.resize(bar, GanttEdge.End, 3, axis) shouldBe bar.copy(endCol = 8)
    }

    "keep the start edge within the axis" in {
      GanttResize.resize(bar, GanttEdge.Start, -9, axis).startCol shouldBe 0
    }

    "keep the end edge within the axis" in {
      GanttResize.resize(bar, GanttEdge.End, 9, axis).endCol shouldBe 9
    }

    "keep at least one column when the start edge passes the end" in {
      GanttResize.resize(bar, GanttEdge.Start, 9, axis) shouldBe bar.copy(startCol = 5)
    }

    "keep at least one column when the end edge passes the start" in {
      GanttResize.resize(bar, GanttEdge.End, -9, axis) shouldBe bar.copy(endCol = 2)
    }
  }

  "GanttResize.newDates" should {
    val start = monday.plusDays(2)
    val due = monday.plusDays(7)

    "change only the start when the start edge moves" in {
      val resized = GanttResize.resize(bar, GanttEdge.Start, -1, axis)
      GanttResize.newDates(start, due, GanttEdge.Start, resized, axis) shouldBe Some((monday.plusDays(1), due))
    }

    "change only the due date when the end edge moves" in {
      val resized = GanttResize.resize(bar, GanttEdge.End, 1, axis)
      GanttResize.newDates(start, due, GanttEdge.End, resized, axis) shouldBe Some((start, monday.plusDays(8)))
    }

    "keep a due date outside the visible days when only the start moves" in {
      val farDue = monday.plusDays(40)
      val clipped = GanttBar.Placed(2, 9, clippedLeft = false, clippedRight = true)
      val resized = GanttResize.resize(clipped, GanttEdge.Start, 1, axis)
      GanttResize.newDates(start, farDue, GanttEdge.Start, resized, axis) shouldBe Some((monday.plusDays(3), farDue))
    }

    "change nothing when the result would end before it starts" in {
      val resized = GanttResize.resize(bar, GanttEdge.End, -1, axis)
      GanttResize.newDates(start, monday.plusDays(1), GanttEdge.End, resized, axis) shouldBe None
    }
  }
}
