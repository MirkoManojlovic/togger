package com.togger.ui.timebox

import com.togger.dto.{FindTaskRespFull, FindTaskStatusResp, SearchAccountDto}
import com.togger.task.TaskPriority
import com.togger.ui.apexchart.ApexSeries
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class TimeboxReviewStatsTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val monday = LocalDate.of(2026, 10, 12)
  private val friday = monday.plusDays(4)
  private val ada = SearchAccountDto(UUID.randomUUID(), "Ada", "Lovelace", "ada@example.com", UUID.randomUUID())
  private val bob = SearchAccountDto(UUID.randomUUID(), "Bob", "Builder", "bob@example.com", UUID.randomUUID())

  private def task(status: String, assignee: Option[SearchAccountDto] = None) = FindTaskRespFull(
    id = UUID.randomUUID(),
    projectId = projectId,
    name = "A task",
    description = "",
    assignedTo = assignee,
    createdBy = ada,
    closedBy = None,
    taskStatus = FindTaskStatusResp(UUID.randomUUID(), projectId, status, ""),
    priority = TaskPriority.Medium,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    latestVersion = UUID.randomUUID(),
    logEntries = Seq.empty,
    links = Seq.empty,
    startDate = monday,
    dueDate = friday
  )

  "TimeboxReviewStats.daysProgress" should {
    "count both the first and the last day" in {
      TimeboxReviewStats.daysProgress(monday, friday, monday) shouldBe DaysProgress(elapsed = 1, total = 5)
    }

    "have nothing left on the last day" in {
      TimeboxReviewStats.daysProgress(monday, friday, friday.plusDays(1)) shouldBe DaysProgress(5, 5)
      TimeboxReviewStats.daysProgress(monday, friday, friday).left shouldBe 0
    }

    "have nothing elapsed before the timebox starts" in {
      TimeboxReviewStats.daysProgress(monday, friday, monday.minusDays(3)) shouldBe DaysProgress(0, 5)
    }
  }

  "TimeboxReviewStats.tasksProgress" should {
    "count the tasks with a status named Done, whatever its case" in {
      val progress = TimeboxReviewStats.tasksProgress(Seq(task("Done"), task(" done "), task("In progress")))
      progress shouldBe TasksProgress(done = 2, total = 3)
      progress.left shouldBe 1
    }

    "have nothing done without tasks" in {
      TimeboxReviewStats.tasksProgress(Seq.empty) shouldBe TasksProgress(0, 0)
    }
  }

  "TimeboxReviewStats.tasksOf" should {
    "keep the tasks assigned to the member" in {
      val mine = task("Done", Some(ada))
      TimeboxReviewStats.tasksOf(ada, Seq(mine, task("Done", Some(bob)), task("Done"))) shouldBe Seq(mine)
    }
  }

  "TimeboxReviewStats.pies" should {
    "make one pie per grouping under its label" in {
      val pies = TimeboxReviewStats.pies(TimeboxReviewStats.OverallGroupings, Seq(task("Done", Some(ada))), Seq.empty)
      pies.map(_.title) shouldBe Seq("Status", "Milestone", "Priority", "Label", "Assignee")
    }

    "size the slices by the tasks in each group" in {
      val tasks = Seq(task("Done"), task("Done"), task("Backlog"))
      val status = TimeboxReviewStats.pies(Seq(StagedGrouping.ByStatus), tasks, Seq.empty).head
      status.options.series shouldBe ApexSeries.Slices(Seq(1, 2), Seq("Backlog", "Done"))
    }

    "not give the members a pie by assignee" in {
      TimeboxReviewStats.MemberGroupings.map(_.label) shouldBe Seq("Status", "Milestone", "Priority", "Label")
    }
  }
}
