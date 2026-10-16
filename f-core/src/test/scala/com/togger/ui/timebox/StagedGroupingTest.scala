package com.togger.ui.timebox

import com.togger.dto.{FindTaskLabelResp, FindTaskRespFull, FindTaskStatusResp, SearchAccountDto}
import com.togger.task.{DefaultTaskStatus, TaskPriority}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class StagedGroupingTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val day = LocalDate.of(2026, 10, 12)
  private val ada = SearchAccountDto(UUID.randomUUID(), "Ada", "Lovelace", "ada@example.com", UUID.randomUUID())
  private val bob = SearchAccountDto(UUID.randomUUID(), "Bob", "Builder", "bob@example.com", UUID.randomUUID())
  private val backlog = FindTaskStatusResp(UUID.randomUUID(), projectId, DefaultTaskStatus.Backlog.statusName, "")
  private val done = FindTaskStatusResp(UUID.randomUUID(), projectId, DefaultTaskStatus.Done.statusName, "")
  private val bug = FindTaskLabelResp(UUID.randomUUID(), projectId, "bug", "")
  private val feature = FindTaskLabelResp(UUID.randomUUID(), projectId, "feature", "")

  private def task(name: String) = FindTaskRespFull(
    id = UUID.randomUUID(),
    projectId = projectId,
    name = name,
    description = "",
    assignedTo = None,
    createdBy = ada,
    closedBy = None,
    taskStatus = backlog,
    priority = TaskPriority.Medium,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    latestVersion = UUID.randomUUID(),
    logEntries = Seq.empty,
    links = Seq.empty,
    startDate = day,
    dueDate = day
  )

  private def names(groups: Seq[StagedGroup]) = groups.map(g => g.title -> g.tasks.map(_.name))

  "StagedGrouping" should {
    "keep every task in one untitled group when not grouped" in {
      names(StagedGrouping.NotGrouped.group(Seq(task("a"), task("b")), Seq.empty)) shouldBe
        Seq("" -> Seq("a", "b"))
    }

    "group by assignee with the unassigned tasks last" in {
      val tasks = Seq(task("a"), task("b").copy(assignedTo = Some(bob)), task("c").copy(assignedTo = Some(ada)))
      names(StagedGrouping.ByAssignee.group(tasks, Seq.empty)) shouldBe
        Seq("Ada Lovelace" -> Seq("c"), "Bob Builder" -> Seq("b"), "Unassigned" -> Seq("a"))
    }

    "group by priority, most urgent first" in {
      val tasks = Seq(task("a").copy(priority = TaskPriority.Low), task("b").copy(priority = TaskPriority.High))
      StagedGrouping.ByPriority.group(tasks, Seq.empty).map(_.title) shouldBe
        Seq(TaskPriority.High.shortCode, TaskPriority.Low.shortCode)
    }

    "group by status" in {
      val tasks = Seq(task("a"), task("b").copy(taskStatus = done), task("c"))
      names(StagedGrouping.ByStatus.group(tasks, Seq.empty)).toMap shouldBe
        Map(backlog.name -> Seq("a", "c"), done.name -> Seq("b"))
    }

    "put a task under each of its labels and the unlabelled ones last" in {
      val tasks = Seq(task("a").copy(taskLabels = Seq(feature, bug)), task("b").copy(taskLabels = Seq(bug)), task("c"))
      names(StagedGrouping.ByLabel.group(tasks, Seq.empty)) shouldBe
        Seq("bug" -> Seq("a", "b"), "feature" -> Seq("a"), "No label" -> Seq("c"))
    }

    "put tasks without a known milestone under no milestone" in {
      names(StagedGrouping.ByMilestone.group(Seq(task("a").copy(milestone = Some(UUID.randomUUID()))), Seq.empty)) shouldBe
        Seq("No milestone" -> Seq("a"))
    }
  }
}
