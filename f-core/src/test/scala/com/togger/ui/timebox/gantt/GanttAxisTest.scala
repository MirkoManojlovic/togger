package com.togger.ui.timebox.gantt

import com.togger.common.WorkDayCalendar
import com.togger.common.suites.AsyncJsWordSpec

import java.time.{DayOfWeek, LocalDate}

class GanttAxisTest extends AsyncJsWordSpec {

  private val monday = LocalDate.of(2026, 10, 12)
  private val workDays = WorkDayCalendar.DefaultWorkDays

  testNameFromClassName should {

    "leave weekends out" in { _ =>
      val axis = GanttAxis.of(monday, monday.plusDays(13), workDays)
      (axis.size, axis.days.map(_.getDayOfWeek).toSet) shouldBe ((10, workDays))
    }

    "follow custom work days" in { _ =>
      val sundayToThursday = Set(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
      val axis = GanttAxis.of(monday, monday.plusDays(6), sundayToThursday)
      axis.days.map(_.getDayOfWeek) shouldBe Vector(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.SUNDAY
      )
    }

    "be empty for a reversed range" in { _ =>
      GanttAxis.of(monday.plusDays(3), monday, workDays).isEmpty shouldBe true
    }

    "be empty when the range holds only days off" in { _ =>
      GanttAxis.of(monday.plusDays(5), monday.plusDays(6), workDays).isEmpty shouldBe true
    }

    "start and end on days off" in { _ =>
      val axis = GanttAxis.of(monday.plusDays(5), monday.plusDays(8), workDays)
      axis.days shouldBe Vector(monday.plusDays(7), monday.plusDays(8))
    }

    "find the column of a work day" in { _ =>
      val axis = GanttAxis.of(monday, monday.plusDays(13), workDays)
      (axis.indexOf(monday.plusDays(7)), axis.dateAt(5)) shouldBe ((Some(5), Some(monday.plusDays(7))))
    }

    "have no column for a day off" in { _ =>
      GanttAxis.of(monday, monday.plusDays(13), workDays).indexOf(monday.plusDays(5)) shouldBe None
    }

    "snap a day off forward or backward" in { _ =>
      val axis = GanttAxis.of(monday, monday.plusDays(13), workDays)
      val saturday = monday.plusDays(5)
      (axis.firstIndexOnOrAfter(saturday), axis.lastIndexOnOrBefore(saturday)) shouldBe ((Some(5), Some(4)))
    }

    "clamp dates outside the range" in { _ =>
      val axis = GanttAxis.of(monday, monday.plusDays(13), workDays)
      (axis.clampIndex(monday.minusDays(3)), axis.clampIndex(monday.plusDays(30))) shouldBe
        ((Some(ClampedColumn(0, GanttClamp.Before)), Some(ClampedColumn(9, GanttClamp.After))))
    }

    "clamp a day off to the closest column, the earlier one on a tie" in { _ =>
      val axis = GanttAxis.of(monday, monday.plusDays(13), workDays)
      (axis.clampIndex(monday.plusDays(5)), axis.clampIndex(monday.plusDays(6))) shouldBe
        ((Some(ClampedColumn(4, GanttClamp.Inside)), Some(ClampedColumn(5, GanttClamp.Inside))))
    }

    "not clamp on an empty axis" in { _ =>
      GanttAxis.empty.clampIndex(monday) shouldBe None
    }

  }

}
