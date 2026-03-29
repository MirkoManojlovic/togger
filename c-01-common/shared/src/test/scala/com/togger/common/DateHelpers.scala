package com.togger.common

import java.time.{DayOfWeek, Instant, LocalDate, ZoneOffset}

trait DateHelpers {

  val today: LocalDate = Instant.now.atOffset(ZoneOffset.UTC).toLocalDate
  val tomorrow: LocalDate = today.plusDays(1)
  val dayAfterTomorrow: LocalDate = today.plusDays(2)
  val yesterday: LocalDate = today.minusDays(1)

  lazy val monday: LocalDate = DateHelpers.monday
  lazy val tuesday: LocalDate = DateHelpers.tuesday
  lazy val wednesday: LocalDate = DateHelpers.wednesday
  lazy val thursday: LocalDate = DateHelpers.thursday
  lazy val friday: LocalDate = DateHelpers.friday
  lazy val saturday: LocalDate = DateHelpers.saturday
  lazy val sunday: LocalDate = DateHelpers.sunday

  lazy val previousMonday: LocalDate = DateHelpers.previousMonday
  lazy val previousTuesday: LocalDate = DateHelpers.previousTuesday
  lazy val previousWednesday: LocalDate = DateHelpers.previousWednesday
  lazy val previousThursday: LocalDate = DateHelpers.previousThursday
  lazy val previousFriday: LocalDate = DateHelpers.previousFriday
  lazy val previousSaturday: LocalDate = DateHelpers.previousSaturday
  lazy val previousSunday: LocalDate = DateHelpers.previousSunday

  lazy val nextMonday: LocalDate = DateHelpers.nextMonday
  lazy val nextTuesday: LocalDate = DateHelpers.nextTuesday
  lazy val nextWednesday: LocalDate = DateHelpers.nextWednesday
  lazy val nextThursday: LocalDate = DateHelpers.nextThursday
  lazy val nextFriday: LocalDate = DateHelpers.nextFriday
  lazy val nextSaturday: LocalDate = DateHelpers.nextSaturday
  lazy val nextSunday: LocalDate = DateHelpers.nextSunday

}

object DateHelpers {

  /* Instant is used because of the JS error
  Uncaught java.time.zone.ZoneRulesException: Unknown time-zone ID: Europe/Belgrade
   */
  lazy val today: LocalDate = Instant.now.atOffset(ZoneOffset.UTC).toLocalDate

  lazy val monday: LocalDate = today.`with`(DayOfWeek.MONDAY)
  lazy val tuesday: LocalDate = today.`with`(DayOfWeek.TUESDAY)
  lazy val wednesday: LocalDate = today.`with`(DayOfWeek.WEDNESDAY)
  lazy val thursday: LocalDate = today.`with`(DayOfWeek.THURSDAY)
  lazy val friday: LocalDate = today.`with`(DayOfWeek.FRIDAY)
  lazy val saturday: LocalDate = today.`with`(DayOfWeek.SATURDAY)
  lazy val sunday: LocalDate = today.`with`(DayOfWeek.SUNDAY)

  lazy val previousMonday: LocalDate = monday.minusWeeks(1)
  lazy val previousTuesday: LocalDate = tuesday.minusWeeks(1)
  lazy val previousWednesday: LocalDate = wednesday.minusWeeks(1)
  lazy val previousThursday: LocalDate = thursday.minusWeeks(1)
  lazy val previousFriday: LocalDate = friday.minusWeeks(1)
  lazy val previousSaturday: LocalDate = saturday.minusWeeks(1)
  lazy val previousSunday: LocalDate = sunday.minusWeeks(1)

  lazy val nextMonday: LocalDate = monday.plusWeeks(1)
  lazy val nextTuesday: LocalDate = tuesday.plusWeeks(1)
  lazy val nextWednesday: LocalDate = wednesday.plusWeeks(1)
  lazy val nextThursday: LocalDate = thursday.plusWeeks(1)
  lazy val nextFriday: LocalDate = friday.plusWeeks(1)
  lazy val nextSaturday: LocalDate = saturday.plusWeeks(1)
  lazy val nextSunday: LocalDate = sunday.plusWeeks(1)
}
