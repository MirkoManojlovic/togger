package com.togger.dump

import com.togger.logentry.LogEntry
import com.togger.logentry.LogEntryStatus
import com.togger.logentry.LogEntryVersion
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class LogEntryDumpDataDecodingTest extends AnyWordSpec with Matchers {

  private val logEntryId = UUID.randomUUID()
  private val createdAt = Instant.parse("2026-10-12T23:30:00Z")

  private val dump = LogEntryDumpData(
    LogEntry(logEntryId, UUID.randomUUID(), UUID.randomUUID(), createdAt, LocalDate.of(2026, 10, 9)),
    List(
      LogEntryVersion(
        UUID.randomUUID(),
        logEntryId,
        "external",
        LogEntryStatus.InProgress,
        UUID.randomUUID(),
        createdAt,
        createdAt
      )
    )
  )

  "LogEntryDumpData" should {

    "decode what it encodes, with its log date" in {
      decode[LogEntryDumpData](dump.asJson.noSpaces) shouldBe Right(dump)
    }

    "decode a dump written before log entries had a log date, with the UTC day of createdAt" in {
      val withoutLogDate = dump.asJson.hcursor
        .downField("logEntry")
        .downField("logDate")
        .delete
        .top
        .get
        .noSpaces

      decode[LogEntryDumpData](withoutLogDate) shouldBe
        Right(dump.copy(logEntry = dump.logEntry.copy(logDate = LocalDate.of(2026, 10, 12))))
    }
  }
}
