package com.togger.ui.project

import com.togger.dto.{FindTaskLabelResp, FindTaskRespFull, FindTaskStatusResp, FindTimeboxResp, SearchAccountDto, TimeboxVersionDto}
import com.togger.common.WorkDayCalendar
import com.togger.task.TaskPriority
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class ProjectHomeStatsTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val monday = LocalDate.of(2026, 10, 12)
  private val friday = monday.plusDays(4)
  private val today = monday
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

  "ProjectHomeStats.byColumn" should {
    "put the tasks being worked on in the column of their status by name and leave out the others" in {
      val blocked = status("Blocked")
      val inProgress = status("In progress")
      val done = status("Done")
      val columns = ProjectHomeStats.byColumn(
        Seq(task("b", blocked), task("z", inProgress), task("a", inProgress), task("c", done))
      )
      columns.map(_.map(_.name)) shouldBe Seq(Seq("a", "z"), Seq.empty, Seq.empty, Seq("b"))
    }
  }

  "ProjectHomeStats.inFlightOf" should {
    "keep only the tasks of the member and drop the groups left empty" in {
      val inProgress = status("In progress")
      val testing = status("Testing")
      val mine = task("mine", inProgress).copy(assignedTo = Some(ada))
      val groups = Seq(inProgress -> Seq(mine, task("other", inProgress)), testing -> Seq(task("t", testing)))
      ProjectHomeStats.inFlightOf(ada, groups) shouldBe Seq(inProgress -> Seq(mine))
    }
  }

  "ProjectHomeStats.labelBadges" should {
    "list the labels of a task by name" in {
      def label(name: String) = FindTaskLabelResp(UUID.randomUUID(), projectId, name, "", "")
      val labelled = task("t", status("Testing")).copy(taskLabels = Seq(label("b"), label("a")))
      ProjectHomeStats.labelBadges(labelled).map(_.text) shouldBe Seq("a", "b")
    }
  }

  "ProjectHomeStats.taskBadges" should {
    "give a task without milestone, assignee and labels its priority, due date and days left" in {
      ProjectHomeStats.taskBadges(task("t", status("Testing")), Seq.empty, showAssignee = true, today, WorkDayCalendar.DefaultWorkDays, _.toString).map(_.text) shouldBe
        Seq(TaskPriority.Medium.shortCode, friday.toString, "5 days left")
    }

    "add the assignee only when asked to" in {
      val assigned = task("t", status("Testing")).copy(assignedTo = Some(ada))
      ProjectHomeStats.taskBadges(assigned, Seq.empty, showAssignee = true, today, WorkDayCalendar.DefaultWorkDays, _.toString).map(_.text) should contain(ada.fullName)
      ProjectHomeStats.taskBadges(assigned, Seq.empty, showAssignee = false, today, WorkDayCalendar.DefaultWorkDays, _.toString).map(_.text) should not contain ada.fullName
    }
  }

  "ProjectHomeStats.memberRows" should {
    "put the tasks of each member in the column of their status, the unassigned last" in {
      val inProgress = status("In progress")
      val blocked = status("Blocked")
      val bob = ada.copy(id = UUID.randomUUID(), firstName = "Bob", lastName = "Baker")
      val adas = task("a", inProgress).copy(assignedTo = Some(ada))
      val bobs = task("b", blocked).copy(assignedTo = Some(bob))
      val nobodys = task("n", inProgress)
      val rows = ProjectHomeStats.memberRows(Seq(nobodys, adas, bobs))
      rows.map(_._1) shouldBe Seq(Some(ada), Some(bob), None)
      rows.map(_._2) shouldBe Seq(
        Seq(Seq(adas), Seq.empty, Seq.empty, Seq.empty),
        Seq(Seq.empty, Seq.empty, Seq.empty, Seq(bobs)),
        Seq(Seq(nobodys), Seq.empty, Seq.empty, Seq.empty)
      )
    }
  }
}
