package com.togger.common

import java.time.{Duration, Instant, LocalTime, ZoneOffset}

trait TimeHelpers {

  lazy val nowAsInstant: Instant = Instant.now()
  lazy val now: LocalTime = nowAsInstant.atOffset(ZoneOffset.UTC).toLocalTime

  lazy val midnight: LocalTime = TimeHelpers.midnight
  lazy val at1: LocalTime = TimeHelpers.at1
  lazy val at2: LocalTime = TimeHelpers.at2
  lazy val at3: LocalTime = TimeHelpers.at3
  lazy val at4: LocalTime = TimeHelpers.at4
  lazy val at5: LocalTime = TimeHelpers.at5
  lazy val at6: LocalTime = TimeHelpers.at6
  lazy val at7: LocalTime = TimeHelpers.at7
  lazy val at8: LocalTime = TimeHelpers.at8
  lazy val at9: LocalTime = TimeHelpers.at9
  lazy val at10: LocalTime = TimeHelpers.at10
  lazy val at11: LocalTime = TimeHelpers.at11
  lazy val at12: LocalTime = TimeHelpers.at12
  lazy val at13: LocalTime = TimeHelpers.at13
  lazy val at14: LocalTime = TimeHelpers.at14
  lazy val at15: LocalTime = TimeHelpers.at15
  lazy val at16: LocalTime = TimeHelpers.at16
  lazy val at17: LocalTime = TimeHelpers.at17
  lazy val at18: LocalTime = TimeHelpers.at18
  lazy val at19: LocalTime = TimeHelpers.at19
  lazy val at20: LocalTime = TimeHelpers.at20
  lazy val at21: LocalTime = TimeHelpers.at21
  lazy val at22: LocalTime = TimeHelpers.at22
  lazy val at23: LocalTime = TimeHelpers.at23

  lazy val duration60min: Duration = TimeHelpers.duration60min
  lazy val duration30min: Duration = TimeHelpers.duration30min
  lazy val duration20min: Duration = TimeHelpers.duration20min
  lazy val duration15min: Duration = TimeHelpers.duration15min
  lazy val duration10min: Duration = TimeHelpers.duration10min

  lazy val timetableLimitOneMonth: Short = 1
  lazy val timetableLimitTwoMonths: Short = 1

}

object TimeHelpers {
  lazy val now: LocalTime = Instant.now.atOffset(ZoneOffset.UTC).toLocalTime

  lazy val midnight: LocalTime = LocalTime.of(0, 0)
  lazy val at1: LocalTime = LocalTime.of(1, 0)
  lazy val at2: LocalTime = LocalTime.of(2, 0)
  lazy val at3: LocalTime = LocalTime.of(3, 0)
  lazy val at4: LocalTime = LocalTime.of(4, 0)
  lazy val at5: LocalTime = LocalTime.of(5, 0)
  lazy val at6: LocalTime = LocalTime.of(6, 0)
  lazy val at7: LocalTime = LocalTime.of(7, 0)
  lazy val at8: LocalTime = LocalTime.of(8, 0)
  lazy val at9: LocalTime = LocalTime.of(9, 0)
  lazy val at10: LocalTime = LocalTime.of(10, 0)
  lazy val at11: LocalTime = LocalTime.of(11, 0)
  lazy val at12: LocalTime = LocalTime.of(12, 0)
  lazy val at13: LocalTime = LocalTime.of(13, 0)
  lazy val at14: LocalTime = LocalTime.of(14, 0)
  lazy val at15: LocalTime = LocalTime.of(15, 0)
  lazy val at16: LocalTime = LocalTime.of(16, 0)
  lazy val at17: LocalTime = LocalTime.of(17, 0)
  lazy val at18: LocalTime = LocalTime.of(18, 0)
  lazy val at19: LocalTime = LocalTime.of(19, 0)
  lazy val at20: LocalTime = LocalTime.of(20, 0)
  lazy val at21: LocalTime = LocalTime.of(21, 0)
  lazy val at22: LocalTime = LocalTime.of(22, 0)
  lazy val at23: LocalTime = LocalTime.of(23, 0)

  lazy val duration60min: Duration = Duration.ofMinutes(60)
  lazy val duration30min: Duration = Duration.ofMinutes(30)
  lazy val duration20min: Duration = Duration.ofMinutes(20)
  lazy val duration15min: Duration = Duration.ofMinutes(15)
  lazy val duration10min: Duration = Duration.ofMinutes(10)
}
