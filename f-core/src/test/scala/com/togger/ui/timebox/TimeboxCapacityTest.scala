package com.togger.ui.timebox

import com.togger.common.WorkDayCalendar
import com.togger.dto.{FindTaskRespFull, FindTaskStatusResp, SearchAccountDto}
import com.togger.task.{DefaultTaskStatus, TaskPriority}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class TimeboxCapacityTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val workDays = WorkDayCalendar.DefaultWorkDays
  private val monday = LocalDate.of(2026, 10, 12)
  private val friday = monday.plusDays(4)
  private val week = (monday, friday)
  private val ada = SearchAccountDto(UUID.randomUUID(), "Ada", "Lovelace", "ada@example.com", UUID.randomUUID())
  private val bob = SearchAccountDto(UUID.randomUUID(), "Bob", "Builder", "bob@example.com", UUID.randomUUID())
  private val backlog = FindTaskStatusResp(UUID.randomUUID(), projectId, DefaultTaskStatus.Backlog.statusName, "")

  /** A task of `days` work days, starting on the Monday of the timebox week. */
  private def task(days: Int, assignee: Option[SearchAccountDto] = None) = FindTaskRespFull(
    id = UUID.randomUUID(),
    projectId = projectId,
    name = "A task",
    description = "",
    assignedTo = assignee,
    createdBy = ada,
    closedBy = None,
    taskStatus = backlog,
    priority = TaskPriority.Medium,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    latestVersion = UUID.randomUUID(),
    logEntries = Seq.empty,
    links = Seq.empty,
    startDate = monday,
    dueDate = WorkDayCalendar.lastDayOfWorkDays(monday, days, workDays)
  )

  private def capacity(tasks: Seq[FindTaskRespFull], members: Seq[SearchAccountDto]) =
    TimeboxCapacity.of(tasks, members, workDays, week)

  "TimeboxCapacity" should {
    "give one person the work days of the timebox" in {
      val result = capacity(Seq.empty, Seq(ada))
      (result.timeboxWorkDays, result.total.capacityDays, result.people.map(_.capacityDays)) shouldBe (5, 5, Seq(5))
    }

    "give the team the work days of the timebox times the people" in {
      capacity(Seq.empty, Seq(ada, bob)).total.capacityDays shouldBe 10
    }

    "not count weekends" in {
      TimeboxCapacity.of(Seq.empty, Seq(ada), workDays, (monday, monday.plusDays(13))).timeboxWorkDays shouldBe 10
    }

    "fit one 5 day task for one person" in {
      capacity(Seq(task(5, Some(ada))), Seq(ada)).isOverCapacity shouldBe false
    }

    "fit a 2 day and a 3 day task for one person" in {
      capacity(Seq(task(2, Some(ada)), task(3, Some(ada))), Seq(ada)).isOverCapacity shouldBe false
    }

    "fit five 1 day tasks for one person" in {
      capacity(Seq.fill(5)(task(1, Some(ada))), Seq(ada)).isOverCapacity shouldBe false
    }

    "not fit more than the timebox has for one person" in {
      capacity(Seq(task(5, Some(ada)), task(1, Some(ada))), Seq(ada)).isOverCapacity shouldBe true
    }

    "fit two 5 day tasks for two people" in {
      val result = capacity(Seq(task(5, Some(ada)), task(5, Some(bob))), Seq(ada, bob))
      (result.total.usedDays, result.isOverCapacity) shouldBe (10, false)
    }

    "fit 3, 2, 4 and 1 day tasks for two people" in {
      val tasks = Seq(task(3, Some(ada)), task(2, Some(ada)), task(4, Some(bob)), task(1, Some(bob)))
      capacity(tasks, Seq(ada, bob)).isOverCapacity shouldBe false
    }

    "fit ten 1 day tasks for two people" in {
      val tasks = Seq.fill(5)(task(1, Some(ada))) ++ Seq.fill(5)(task(1, Some(bob)))
      capacity(tasks, Seq(ada, bob)).isOverCapacity shouldBe false
    }

    "be over when one person has more than the timebox, though the team still has room" in {
      val result = capacity(Seq(task(5, Some(ada)), task(1, Some(ada))), Seq(ada, bob))
      (result.total.isOver, result.people.map(_.isOver), result.isOverCapacity) shouldBe
        (false, Seq(true, false), true)
    }

    "count tasks without an assignee in the total only" in {
      val result = capacity(Seq(task(3), task(2, Some(ada))), Seq(ada, bob))
      (result.total.usedDays, result.unassignedDays, result.people.map(_.usedDays)) shouldBe (5, 3, Seq(2, 0))
    }

    "list the people by name" in {
      capacity(Seq.empty, Seq(bob, ada)).people.map(_.name) shouldBe Seq("Ada Lovelace", "Bob Builder")
    }
  }

  "CapacityLevel.of" should {
    "be green up to and including 100%" in {
      CapacityLevel.of(69, 100) shouldBe CapacityLevel.Green
      CapacityLevel.of(99, 100) shouldBe CapacityLevel.Green
      CapacityLevel.of(100, 100) shouldBe CapacityLevel.Green
      CapacityLevel.of(0, 5) shouldBe CapacityLevel.Green
    }

    "be red over 100%" in {
      CapacityLevel.of(101, 100) shouldBe CapacityLevel.Red
      CapacityLevel.of(6, 5) shouldBe CapacityLevel.Red
    }

    "be red for work with no capacity at all" in {
      CapacityLevel.of(1, 0) shouldBe CapacityLevel.Red
      CapacityLevel.of(0, 0) shouldBe CapacityLevel.Green
    }
  }

  "CapacityLine.percent" should {
    "go past 100 when over" in {
      CapacityLine("Ada", 6, 5).percent shouldBe 120
    }
  }
}
