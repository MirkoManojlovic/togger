package com.togger.common

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZoneOffset}
import java.time.temporal.ChronoUnit

trait TgClock {
  def currentInstant: Instant
  def currentOffsetDateTime(zoneOffset: ZoneOffset = ZoneOffset.UTC): OffsetDateTime
  def currentYear(zoneOffset: ZoneOffset = ZoneOffset.UTC): Int
  def currentDate(zoneOffset: ZoneOffset = ZoneOffset.UTC): LocalDate

  def instantFrom(date: LocalDate, time: LocalTime, zoneOffset: ZoneOffset): Instant =
    LocalDateTime.of(date, time).toInstant(zoneOffset).truncatedTo(ChronoUnit.MICROS)
}

class TgClockImpl extends TgClock {
  override def currentInstant: Instant = Instant.now.truncatedTo(ChronoUnit.MICROS)

  override def currentOffsetDateTime(zoneOffset: ZoneOffset = ZoneOffset.UTC): OffsetDateTime =
    currentInstant.atOffset(zoneOffset)

  override def currentYear(zoneOffset: ZoneOffset = ZoneOffset.UTC): Int =
    currentInstant.atOffset(zoneOffset).getYear

  override def currentDate(zoneOffset: ZoneOffset = ZoneOffset.UTC): LocalDate =
    currentInstant.atOffset(zoneOffset).toLocalDate
}