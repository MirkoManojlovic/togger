package com.togger.ui.timebox.gantt

import com.togger.dto.LogEntryDto
import com.togger.logentry.LogEntryStatus
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class GanttLogStatusTest extends AnyWordSpec with Matchers {

  private val taskA = UUID.randomUUID()
  private val taskB = UUID.randomUUID()

  private def entry(task: UUID, at: String, status: LogEntryStatus) = {
    val createdAt = Instant.parse(at)
    LogEntryDto(UUID.randomUUID(), task, UUID.randomUUID(), createdAt, UUID.randomUUID(), "", "", status, createdAt)
  }

  "GanttLogStatus.byTaskAndDay" should {

    "have nothing without entries" in {
      GanttLogStatus.byTaskAndDay(Seq.empty) shouldBe empty
    }

    "keep the latest entry of a day" in {
      val entries = Seq(
        entry(taskA, "2026-03-02T15:00:00Z", LogEntryStatus.Completed),
        entry(taskA, "2026-03-02T09:00:00Z", LogEntryStatus.InProgress)
      )
      GanttLogStatus.byTaskAndDay(entries) shouldBe Map((taskA, LocalDate.of(2026, 3, 2)) -> LogEntryStatus.Completed)
    }

    "keep the days and the tasks apart" in {
      val entries = Seq(
        entry(taskA, "2026-03-02T09:00:00Z", LogEntryStatus.InProgress),
        entry(taskA, "2026-03-03T09:00:00Z", LogEntryStatus.Blocked),
        entry(taskB, "2026-03-02T09:00:00Z", LogEntryStatus.Paused)
      )
      GanttLogStatus.byTaskAndDay(entries) shouldBe Map(
        (taskA, LocalDate.of(2026, 3, 2)) -> LogEntryStatus.InProgress,
        (taskA, LocalDate.of(2026, 3, 3)) -> LogEntryStatus.Blocked,
        (taskB, LocalDate.of(2026, 3, 2)) -> LogEntryStatus.Paused
      )
    }

    "use the UTC day around midnight" in {
      val entries = Seq(
        entry(taskA, "2026-03-02T23:59:59Z", LogEntryStatus.InProgress),
        entry(taskA, "2026-03-03T00:00:00Z", LogEntryStatus.Completed)
      )
      GanttLogStatus.byTaskAndDay(entries).keySet shouldBe
        Set((taskA, LocalDate.of(2026, 3, 2)), (taskA, LocalDate.of(2026, 3, 3)))
    }
  }
}
