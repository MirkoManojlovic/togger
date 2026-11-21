package com.togger.ui.timebox.gantt

import com.togger.dto.LogEntryDto
import com.togger.logentry.LogEntryStatus
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class GanttLogStatusTest extends AnyWordSpec with Matchers {

  private val taskA = UUID.randomUUID()
  private val taskB = UUID.randomUUID()

  /** `at` is when the entry was written; its log date is that day unless given. */
  private def entry(task: UUID, at: String, status: LogEntryStatus, logDate: Option[LocalDate] = None) = {
    val createdAt = Instant.parse(at)
    val day = logDate.getOrElse(LocalDate.parse(at.take(10)))
    LogEntryDto(UUID.randomUUID(), task, UUID.randomUUID(), createdAt, UUID.randomUUID(), "", status, createdAt, day)
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

    "keep the entries of two log dates apart, whenever they were written" in {
      val entries = Seq(
        entry(taskA, "2026-03-02T23:59:59Z", LogEntryStatus.InProgress),
        entry(taskA, "2026-03-03T00:00:00Z", LogEntryStatus.Completed)
      )
      GanttLogStatus.byTaskAndDay(entries).keySet shouldBe
        Set((taskA, LocalDate.of(2026, 3, 2)), (taskA, LocalDate.of(2026, 3, 3)))
    }

    "group by the log date and not by the time the entry was written" in {
      val backdated = entry(taskA, "2026-03-05T09:00:00Z", LogEntryStatus.Completed, Some(LocalDate.of(2026, 3, 2)))
      GanttLogStatus.byTaskAndDay(Seq(backdated)) shouldBe
        Map((taskA, LocalDate.of(2026, 3, 2)) -> LogEntryStatus.Completed)
    }
  }

  "GanttLogStatus.latestByTaskAndDay" should {

    "give the latest entry of a day" in {
      val early = entry(taskA, "2026-03-02T09:00:00Z", LogEntryStatus.InProgress)
      val late = entry(taskA, "2026-03-02T15:00:00Z", LogEntryStatus.Completed)
      GanttLogStatus.latestByTaskAndDay(Seq(late, early)) shouldBe Map((taskA, LocalDate.of(2026, 3, 2)) -> late)
    }
  }
}
