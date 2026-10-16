package com.togger.ui.timebox.gantt

import com.togger.common.WorkDayCalendar
import com.togger.common.suites.AsyncJsWordSpec

import java.time.LocalDate

class GanttBarTest extends AsyncJsWordSpec {

  private val monday = LocalDate.of(2026, 10, 12)
  // two weeks, Monday to Sunday: ten columns, the second Monday is column 5
  private val axis = GanttAxis.of(monday, monday.plusDays(13), WorkDayCalendar.DefaultWorkDays)

  testNameFromClassName should {

    "place a task inside the range" in { _ =>
      GanttBar.of(monday.plusDays(1), monday.plusDays(3), axis) shouldBe
        Some(GanttBar.Placed(1, 3, clippedLeft = false, clippedRight = false))
    }

    "place a single-day task in one column" in { _ =>
      GanttBar.of(monday.plusDays(2), monday.plusDays(2), axis).collect { case p: GanttBar.Placed => p.width } shouldBe
        Some(1)
    }

    "snap a start on a day off forward and a due date on a day off backward" in { _ =>
      GanttBar.of(monday.plusDays(5), monday.plusDays(13), axis) shouldBe
        Some(GanttBar.Placed(5, 9, clippedLeft = false, clippedRight = false))
    }

    "clip a task that starts before the range" in { _ =>
      GanttBar.of(monday.minusDays(4), monday.plusDays(1), axis) shouldBe
        Some(GanttBar.Placed(0, 1, clippedLeft = true, clippedRight = false))
    }

    "clip a task that ends after the range" in { _ =>
      GanttBar.of(monday.plusDays(8), monday.plusDays(30), axis) shouldBe
        Some(GanttBar.Placed(6, 9, clippedLeft = false, clippedRight = true))
    }

    "clip a task that spans the whole range on both sides" in { _ =>
      GanttBar.of(monday.minusDays(7), monday.plusDays(30), axis) shouldBe
        Some(GanttBar.Placed(0, 9, clippedLeft = true, clippedRight = true))
    }

    "flag a task completely before the range" in { _ =>
      GanttBar.of(monday.minusDays(10), monday.minusDays(1), axis) shouldBe Some(GanttBar.Outside(GanttClamp.Before))
    }

    "flag a task completely after the range" in { _ =>
      GanttBar.of(monday.plusDays(20), monday.plusDays(25), axis) shouldBe Some(GanttBar.Outside(GanttClamp.After))
    }

    "give a task that lies only on a weekend inside the range its next work day column" in { _ =>
      GanttBar.of(monday.plusDays(5), monday.plusDays(6), axis) shouldBe
        Some(GanttBar.Placed(5, 5, clippedLeft = false, clippedRight = false))
    }

    "have no bar on an empty axis" in { _ =>
      GanttBar.of(monday, monday.plusDays(1), GanttAxis.empty) shouldBe None
    }

  }

}
