package com.togger.common

import java.time._
import java.time.temporal.{ChronoUnit, TemporalAdjuster, TemporalAdjusters}
import scala.util.Try

object TgJavaTimeSyntax {

  implicit class TgInstantOps(instant: Instant) {

    def plusHours(days: Long): Instant =
      instant.plus(days, ChronoUnit.HOURS)

    def plusDays(days: Long): Instant =
      instant.plus(days, ChronoUnit.DAYS)

    def plusWeeks(weeks: Long): Instant =
      instant.atOffset(ZoneOffset.UTC).plusWeeks(weeks).toInstant.truncatedTo(ChronoUnit.MICROS)

    def minusWeeks(weeks: Long): Instant =
      instant.atOffset(ZoneOffset.UTC).minusWeeks(weeks).toInstant.truncatedTo(ChronoUnit.MICROS)

    def plusMonths(months: Long): Instant =
      instant.atOffset(ZoneOffset.UTC).plusMonths(months).toInstant.truncatedTo(ChronoUnit.MICROS)

    def minusMonths(months: Long): Instant =
      instant.atOffset(ZoneOffset.UTC).minusMonths(months).toInstant.truncatedTo(ChronoUnit.MICROS)

    def plusYears(years: Long): Instant =
      instant.atOffset(ZoneOffset.UTC).plusYears(years).toInstant.truncatedTo(ChronoUnit.MICROS)

    def minusYears(years: Long): Instant =
      instant.atOffset(ZoneOffset.UTC).minusYears(years).toInstant.truncatedTo(ChronoUnit.MICROS)

    def toLocalDate(zoneOffset: ZoneOffset = ZoneOffset.UTC): LocalDate =
      instant.atOffset(zoneOffset).toLocalDate

    // This was changed because ScalaJs doesn't support LocalTime.ofInstant(instant, zoneOffset)
    def toLocalTime(zoneOffset: ZoneOffset = ZoneOffset.UTC): LocalTime =
      instant.atOffset(zoneOffset).toLocalTime

    def adjust(adjuster: TemporalAdjuster, zoneOffset: ZoneOffset = ZoneOffset.UTC): Instant =
      instant.atOffset(zoneOffset).`with`(adjuster).toInstant.truncatedTo(ChronoUnit.MICROS)

    def getHour(zoneOffset: ZoneOffset = ZoneOffset.UTC): Int =
      instant.atOffset(zoneOffset).getHour

    def getMinute(zoneOffset: ZoneOffset = ZoneOffset.UTC): Int =
      instant.atOffset(zoneOffset).getMinute

    def getDayOfWeek(zoneOffset: ZoneOffset = ZoneOffset.UTC): DayOfWeek =
      instant.atOffset(zoneOffset).getDayOfWeek
  }

  implicit class TgLocalDateTimeOps(localDateTime: LocalDateTime) {

    def toInstantAtUTC: Instant =
      localDateTime.atOffset(ZoneOffset.UTC).toInstant.truncatedTo(ChronoUnit.MICROS)

  }

  implicit class TgLocalDateOps(localDate: LocalDate) {

    def firstDayOfMonth: LocalDate =
      localDate.`with`(TemporalAdjusters.firstDayOfMonth())

    def lastDayOfMonth: LocalDate =
      localDate.`with`(TemporalAdjusters.lastDayOfMonth())

    def firstDayOfNextMonth: LocalDate =
      localDate.`with`(TemporalAdjusters.firstDayOfNextMonth())

    def tgDatesUntil(endExclusive: LocalDate): Seq[LocalDate] = {
      val period = ChronoUnit.DAYS.between(localDate, endExclusive)
      (0 until period.toInt).map(localDate.plusDays(_))
    }

  }
}

object TgLocalDate {

  def fromQueryParam(dateAsString: String, tgClock: TgClock): LocalDate =
    Try(LocalDate.parse(dateAsString)).getOrElse(tgClock.currentDate())

}
