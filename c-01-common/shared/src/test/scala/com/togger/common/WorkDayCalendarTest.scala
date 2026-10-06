package com.togger.common

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{DayOfWeek, LocalDate}

class WorkDayCalendarTest extends AnyWordSpec with Matchers {

  private val monFri = WorkDayCalendar.DefaultWorkDays
  private val monday = LocalDate.of(2026, 10, 5)

  "WorkDayCalendar.nextWeekday" should {
    "be strictly after the given day when it already is that weekday" in {
      WorkDayCalendar.nextWeekday(monday, DayOfWeek.MONDAY) shouldBe monday.plusDays(7)
    }

    "find the coming Monday from any other weekday" in {
      WorkDayCalendar.nextWeekday(monday.plusDays(1), DayOfWeek.MONDAY) shouldBe monday.plusDays(7)
      WorkDayCalendar.nextWeekday(monday.minusDays(1), DayOfWeek.MONDAY) shouldBe monday
      WorkDayCalendar.nextWeekday(monday.plusDays(6), DayOfWeek.MONDAY) shouldBe monday.plusDays(7)
    }

    "roll over month and year ends" in {
      WorkDayCalendar.nextWeekday(LocalDate.of(2026, 12, 30), DayOfWeek.MONDAY) shouldBe LocalDate.of(2027, 1, 4)
    }
  }

  "WorkDayCalendar.firstWorkDayFrom" should {
    "keep a work day" in {
      WorkDayCalendar.firstWorkDayFrom(monday, monFri) shouldBe monday
    }

    "skip a weekend" in {
      WorkDayCalendar.firstWorkDayFrom(monday.minusDays(2), monFri) shouldBe monday
    }

    "return the day itself when there are no work days" in {
      WorkDayCalendar.firstWorkDayFrom(monday.minusDays(2), Set.empty) shouldBe monday.minusDays(2)
    }
  }

  "WorkDayCalendar.lastDayOfWorkDays" should {
    "end on Friday for 5 work days from a Monday" in {
      WorkDayCalendar.lastDayOfWorkDays(monday, 5, monFri) shouldBe monday.plusDays(4)
    }

    "skip the weekend when the work days run past Friday" in {
      WorkDayCalendar.lastDayOfWorkDays(monday.plusDays(2), 5, monFri) shouldBe monday.plusDays(8)
    }

    "end on the start day for 1 work day" in {
      WorkDayCalendar.lastDayOfWorkDays(monday, 1, monFri) shouldBe monday
    }

    "start counting on the next work day when the start is not one" in {
      WorkDayCalendar.lastDayOfWorkDays(monday.minusDays(1), 5, monFri) shouldBe monday.plusDays(4)
    }

    "roll over a year end" in {
      WorkDayCalendar.lastDayOfWorkDays(LocalDate.of(2026, 12, 28), 5, monFri) shouldBe LocalDate.of(2027, 1, 1)
    }

    "follow a custom work day set" in {
      val satSun = Set(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
      WorkDayCalendar.lastDayOfWorkDays(monday.minusDays(2), 3, satSun) shouldBe monday.plusDays(5)
    }
  }

  "WorkDayCalendar.countWorkDays" should {
    "count both ends" in {
      WorkDayCalendar.countWorkDays(monday, monday.plusDays(4), monFri) shouldBe 5
    }

    "skip weekends" in {
      WorkDayCalendar.countWorkDays(monday, monday.plusDays(7), monFri) shouldBe 6
    }

    "be zero when the end is before the start" in {
      WorkDayCalendar.countWorkDays(monday, monday.minusDays(1), monFri) shouldBe 0
    }
  }
}
