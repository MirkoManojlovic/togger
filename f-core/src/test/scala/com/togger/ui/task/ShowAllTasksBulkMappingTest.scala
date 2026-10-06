package com.togger.ui.task

import com.togger.common.WorkDayCalendar
import com.togger.dto.{FindMilestoneResp, FindTaskLabelResp, FindTaskRespFull, FindTaskStatusResp, SearchAccountDto}
import com.togger.milestone.MilestoneStatus
import com.togger.task.{DefaultTaskLabel, DefaultTaskStatus, TaskPriority}
import com.togger.ui.task.bulk.{BulkEdit, BulkLookups, BulkTaskEditor}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class ShowAllTasksBulkMappingTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val monday = LocalDate.of(2026, 10, 12)

  private val backlog = FindTaskStatusResp(UUID.randomUUID(), projectId, DefaultTaskStatus.Backlog.statusName, "")
  private val done = FindTaskStatusResp(UUID.randomUUID(), projectId, DefaultTaskStatus.Done.statusName, "")
  private val bug = FindTaskLabelResp(UUID.randomUUID(), projectId, DefaultTaskLabel.Bug.labelName, "")
  private val feature = FindTaskLabelResp(UUID.randomUUID(), projectId, DefaultTaskLabel.Feature.labelName, "")
  private val member = SearchAccountDto(UUID.randomUUID(), "Ada", "Lovelace", "ada@example.com", UUID.randomUUID())
  private val milestone = FindMilestoneResp(
    UUID.randomUUID(),
    projectId,
    Instant.EPOCH,
    UUID.randomUUID(),
    "Release 1",
    "",
    MilestoneStatus.InProgress,
    monday,
    monday.plusDays(30),
    None,
    Instant.EPOCH
  )
  private val workDays = WorkDayCalendar.DefaultWorkDays
  private val lookups = BulkLookups(Seq(backlog, done), Seq(bug, feature), Seq(member), Seq(milestone), workDays)

  private val bare = FindTaskRespFull(
    id = UUID.randomUUID(),
    projectId = projectId,
    name = "A task",
    description = "text",
    assignedTo = None,
    createdBy = member,
    closedBy = None,
    taskStatus = backlog,
    priority = TaskPriority.Medium,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    latestVersion = UUID.randomUUID(),
    logEntries = Seq.empty,
    links = Seq.empty,
    startDate = monday,
    dueDate = monday.plusDays(4),
    endDate = Some(monday.plusDays(10))
  )

  private def edit(dto: FindTaskRespFull, change: BulkEdit): FindTaskRespFull = {
    val before = ShowAllTasksBulkMapping.toBulkTask(dto)
    ShowAllTasksBulkMapping.applyTo(dto, BulkTaskEditor(Seq(before), Set(dto.id), change, workDays).head, lookups)
  }

  "ShowAllTasksBulkMapping" should {
    "give back the same task when nothing is edited" in {
      val full = bare.copy(assignedTo = Some(member), milestone = Some(milestone.id), taskLabels = Seq(bug))
      ShowAllTasksBulkMapping.applyTo(full, ShowAllTasksBulkMapping.toBulkTask(full), lookups) shouldBe full
    }

    "map a task without milestone, assignee and labels to empty values" in {
      val task = ShowAllTasksBulkMapping.toBulkTask(bare)
      (task.milestoneId, task.assigneeId, task.labelIds, task.statusId) shouldBe
        (None, None, Set.empty, Some(backlog.id))
    }

    "apply status, assignee, milestone, priority and label edits" in {
      val edited = Seq(
        edit(bare, BulkEdit.SetStatus(done.id)).taskStatus,
        edit(bare, BulkEdit.SetAssignee(Some(member.id))).assignedTo,
        edit(bare, BulkEdit.SetMilestone(Some(milestone.id))).milestone,
        edit(bare, BulkEdit.SetPriority(TaskPriority.High)).priority,
        edit(bare, BulkEdit.SetLabels(Set(bug.id, feature.id))).taskLabels.toSet
      )
      edited shouldBe Seq(done, Some(member), Some(milestone.id), TaskPriority.High, Set(bug, feature))
    }

    "keep the due date when the start moves but the due date is still after it" in {
      val edited = edit(bare, BulkEdit.SetStartDate(monday.plusDays(1)))
      (edited.startDate, edited.dueDate) shouldBe (monday.plusDays(1), bare.dueDate)
    }

    "move the due date along when it would end up before the start" in {
      val edited = edit(bare, BulkEdit.SetStartDate(monday.plusDays(7)))
      edited.dueDate.isBefore(edited.startDate) shouldBe false
    }

    "refuse a due date before the start, and leave the end date alone" in {
      val refused = edit(bare, BulkEdit.SetDueDate(monday.minusDays(3)))
      (refused.dueDate, refused.endDate) shouldBe (bare.dueDate, bare.endDate)
    }

    "keep an unknown status" in {
      val unknown = bare.copy(taskStatus = backlog.copy(id = UUID.randomUUID()))
      ShowAllTasksBulkMapping.applyTo(unknown, ShowAllTasksBulkMapping.toBulkTask(unknown), lookups).taskStatus shouldBe
        unknown.taskStatus
    }

    "send every label of the task in the update request" in {
      val full = bare.copy(assignedTo = Some(member), taskLabels = Seq(bug, feature))
      val request = ShowAllTasksBulkMapping.toUpdateRequest(full, Instant.EPOCH.plusSeconds(5))
      (
        request.task.taskLabelIds.toSet,
        request.task.assignedTo,
        request.task.updatedAt,
        request.linksForUpdate,
        request.linksToCreate
      ) shouldBe (Set(bug.id, feature.id), Some(member.id), Instant.EPOCH.plusSeconds(5), Seq.empty, Seq.empty)
    }
  }
}
