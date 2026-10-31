package com.togger.common

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.DayOfWeek
import java.time.LocalDate

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

  "WorkDayCalendar.splitEvenly" should {
    // Monday to Friday of the next week: 10 work days.
    val twoWeeksEnd = monday.plusDays(11)

    "divide the work days into consecutive ranges of the same size" in {
      WorkDayCalendar.splitEvenly(monday, twoWeeksEnd, 2, monFri) shouldBe
        List(monday -> monday.plusDays(4), monday.plusDays(7) -> twoWeeksEnd)
    }

    "give the remainder to the later ranges and leave no work day out" in {
      val ranges = WorkDayCalendar.splitEvenly(monday, twoWeeksEnd, 3, monFri)
      ranges.map { case (from, to) => WorkDayCalendar.countWorkDays(from, to, monFri) } shouldBe List(3, 3, 4)
      ranges.head._1 shouldBe monday
      ranges.last._2 shouldBe twoWeeksEnd
    }

    "start and end on work days when the period starts and ends on a weekend" in {
      val ranges = WorkDayCalendar.splitEvenly(monday.minusDays(2), twoWeeksEnd.plusDays(2), 1, monFri)
      ranges shouldBe List(monday -> twoWeeksEnd)
    }

    "let ranges share a day when there are more ranges than work days" in {
      val ranges = WorkDayCalendar.splitEvenly(monday, monday.plusDays(1), 4, monFri)
      ranges should have size 4
      ranges.foreach { case (from, to) =>
        from.isBefore(monday) shouldBe false
        to.isAfter(monday.plusDays(1)) shouldBe false
        to.isBefore(from) shouldBe false
      }
      ranges.map(_._1) shouldBe ranges.map(_._1).sorted
    }

    "divide the calendar days when the period has no work day" in {
      val saturday = monday.plusDays(5)
      WorkDayCalendar.splitEvenly(saturday, saturday.plusDays(1), 2, monFri) shouldBe
        List(saturday -> saturday, saturday.plusDays(1) -> saturday.plusDays(1))
    }

    "give nothing for no steps or an end before the start" in {
      WorkDayCalendar.splitEvenly(monday, twoWeeksEnd, 0, monFri) shouldBe Nil
      WorkDayCalendar.splitEvenly(monday, monday.minusDays(1), 2, monFri) shouldBe Nil
    }
  }
}
