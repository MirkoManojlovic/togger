package com.togger.ui.project

import com.togger.dto.{FindTaskRespFull, FindTaskStatusResp, FindTimeboxResp, SearchAccountDto, TimeboxVersionDto}
import com.togger.task.TaskPriority
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class ProjectHomeStatsTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val monday = LocalDate.of(2026, 10, 12)
  private val friday = monday.plusDays(4)
  private val ada = SearchAccountDto(UUID.randomUUID(), "Ada", "Lovelace", "ada@example.com", UUID.randomUUID())

  private def status(name: String) = FindTaskStatusResp(UUID.randomUUID(), projectId, name, "", "")

  private def task(name: String, taskStatus: FindTaskStatusResp) = FindTaskRespFull(
    id = UUID.randomUUID(),
    projectId = projectId,
    name = name,
    description = "",
    assignedTo = None,
    createdBy = ada,
    closedBy = None,
    taskStatus = taskStatus,
    priority = TaskPriority.Medium,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    latestVersion = UUID.randomUUID(),
    logEntries = Seq.empty,
    links = Seq.empty,
    startDate = monday,
    dueDate = friday
  )

  private def timebox(start: LocalDate, end: LocalDate) = {
    val id = UUID.randomUUID()
    FindTimeboxResp(
      id,
      projectId,
      Instant.EPOCH,
      TimeboxVersionDto(UUID.randomUUID(), id, start, end, None, Instant.EPOCH, "", List.empty)
    )
  }

  "ProjectHomeStats.activeTimeboxes" should {
    "keep the timeboxes with today within their dates, the first and last day included" in {
      val current = timebox(monday, friday)
      val past = timebox(monday.minusDays(14), monday.minusDays(1))
      val future = timebox(friday.plusDays(1), friday.plusDays(7))
      ProjectHomeStats.activeTimeboxes(Seq(past, current, future), monday) shouldBe Seq(current)
      ProjectHomeStats.activeTimeboxes(Seq(past, current, future), friday) shouldBe Seq(current)
    }

    "list the timebox that ends first first" in {
      val longer = timebox(monday, friday.plusDays(7))
      val shorter = timebox(monday, friday)
      ProjectHomeStats.activeTimeboxes(Seq(longer, shorter), monday) shouldBe Seq(shorter, longer)
    }
  }

  "ProjectHomeStats.inFlightStatuses" should {
    "keep In progress, Testing, Waiting and Blocked in that order, whatever emoji precedes them" in {
      val blocked = status("🚫 Blocked")
      val inProgress = status("🚧 In progress")
      val statuses = Seq(status("Done"), blocked, status("Todo"), inProgress, status("Backlog"))
      ProjectHomeStats.inFlightStatuses(statuses) shouldBe Seq(inProgress, blocked)
    }
  }

  "ProjectHomeStats.tasksByStatus" should {
    "group the tasks in the order of the statuses and leave out the statuses without tasks" in {
      val inProgress = status("In progress")
      val testing = status("Testing")
      val blocked = status("Blocked")
      val b = task("b", testing)
      val a = task("a", inProgress)
      val c = task("c", inProgress)
      ProjectHomeStats.tasksByStatus(Seq(inProgress, testing, blocked), Seq(c, b, a)) shouldBe
        Seq(inProgress -> Seq(a, c), testing -> Seq(b))
    }
  }
}
