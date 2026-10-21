package com.togger.ui.contributions

import com.togger.dto.{
  MemberContributionDto,
  StatusCountDto,
  TaskAttributesDto,
  TaskLabelCountDto,
  TaskPriorityCountDto,
  TaskStatusCountDto
}
import com.togger.logentry.LogEntryStatus
import com.togger.task.{DefaultTaskLabel, DefaultTaskStatus, TaskPriority}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class MemberStatCountsTest extends AnyWordSpec with Matchers {

  private def member(entries: List[StatusCountDto], tasks: TaskAttributesDto) =
    MemberContributionDto(UUID.randomUUID(), "Ann Adams", entries, 0, 0, 0, 0, List.empty, tasks)

  private val busy = member(
    List(StatusCountDto(LogEntryStatus.InProgress, 3), StatusCountDto(LogEntryStatus.Vacation, 1)),
    TaskAttributesDto(
      byStatus = List(TaskStatusCountDto(DefaultTaskStatus.Done.statusName, 2), TaskStatusCountDto("Custom", 1)),
      byPriority = List(TaskPriorityCountDto(TaskPriority.Critical, 1)),
      byLabel = List(TaskLabelCountDto(Some(DefaultTaskLabel.Bug.labelName), 2), TaskLabelCountDto(None, 1))
    )
  )

  "MemberStatCounts.groupsOf" should {

    "list the log entries first, with the number of entries in every status" in {
      val groups = MemberStatCounts.groupsOf(busy)
      groups.map(_.title) shouldBe
        Seq("Log entries by status", "Tasks by status", "Tasks by priority", "Tasks by label")
      groups.head.counts shouldBe Seq(StatCount("In progress", 3), StatCount("Vacation", 1))
    }

    "list the tasks by status, priority and label" in {
      val groups = MemberStatCounts.groupsOf(busy)
      groups(1).counts shouldBe Seq(StatCount(DefaultTaskStatus.Done.statusName, 2), StatCount("Custom", 1))
      groups(2).counts shouldBe Seq(StatCount("Critical", 1))
      groups(3).counts shouldBe Seq(StatCount(DefaultTaskLabel.Bug.labelName, 2), StatCount("No label", 1))
    }

    "leave out the groups with nothing in them" in {
      MemberStatCounts.groupsOf(member(List.empty, TaskAttributesDto.empty)) shouldBe empty
      MemberStatCounts
        .groupsOf(member(List(StatusCountDto(LogEntryStatus.Blocked, 1)), TaskAttributesDto.empty))
        .map(_.title) shouldBe Seq("Log entries by status")
    }
  }
}
