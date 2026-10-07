package com.togger.timebox

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.temporal.ChronoUnit
import java.time.{DayOfWeek, LocalDate}

class TimeboxDefaultsTest extends AnyWordSpec with Matchers {

  private val monday = LocalDate.of(2026, 10, 5)

  "TimeboxDefaults.defaults" should {

    "offer five presets in order" in {
      TimeboxDefaults.defaults(monday).map(_.label) shouldBe
        List("One week", "Two weeks", "One month", "Two months", "Three months")
    }

    "start today when today is Monday" in {
      TimeboxDefaults.defaults(monday).map(_.startDate).distinct shouldBe List(monday)
    }

    "start on the next Monday from any other weekday" in {
      (1 to 6).foreach { offset =>
        TimeboxDefaults.defaults(monday.plusDays(offset.toLong)).map(_.startDate).distinct shouldBe
          List(monday.plusWeeks(1))
      }
    }

    "always start on a Monday and end on a Sunday" in {
      (0 to 400).foreach { offset =>
        TimeboxDefaults.defaults(monday.plusDays(offset.toLong)).foreach { default =>
          default.startDate.getDayOfWeek shouldBe DayOfWeek.MONDAY
          default.endDate.getDayOfWeek shouldBe DayOfWeek.SUNDAY
        }
      }
    }

    "span 1, 2, 4, 8 and 12 weeks" in {
      TimeboxDefaults.defaults(monday).map(d => ChronoUnit.DAYS.between(d.startDate, d.endDate) + 1) shouldBe
        List(7L, 14L, 28L, 56L, 84L)
    }

    "cross the year boundary" in {
      val default = TimeboxDefaults.defaults(LocalDate.of(2026, 12, 28)).head
      default.startDate shouldBe LocalDate.of(2026, 12, 28)
      default.endDate shouldBe LocalDate.of(2027, 1, 3)
    }
  }
}
