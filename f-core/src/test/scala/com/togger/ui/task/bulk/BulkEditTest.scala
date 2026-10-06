package com.togger.ui.task.bulk

import com.togger.common.WorkDayCalendar
import com.togger.task.TaskPriority
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

class BulkEditTest extends AnyWordSpec with Matchers {

  private val workDays = WorkDayCalendar.DefaultWorkDays
  private val monday = LocalDate.of(2026, 10, 12)
  private val defaults = BulkTaskDefaults.of(monday.minusDays(3), workDays, Seq.empty)

  private def task(name: String) = BulkTask.withDefaults(UUID.randomUUID(), name, defaults)
  private val (t1, t2, t3) = (task("A"), task("B"), task("C"))
  private val all = Seq(t1, t2, t3)
  private def edit(selected: Seq[BulkTask], bulkEdit: BulkEdit) =
    BulkTaskEditor(all, selected.map(_.key).toSet, bulkEdit, workDays)

  "BulkTaskEditor" should {
    "change an attribute of the selected tasks only" in {
      val result = edit(Seq(t1, t3), BulkEdit.SetPriority(TaskPriority.High))
      result.map(_.priority) shouldBe Seq(TaskPriority.High, TaskPriority.Medium, TaskPriority.High)
    }

    "set the status, milestone and assignee" in {
      val (status, milestone, assignee) = (UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
      val result = BulkTaskEditor(
        BulkTaskEditor(
          BulkTaskEditor(Seq(t1), Set(t1.key), BulkEdit.SetStatus(status), workDays),
          Set(t1.key),
          BulkEdit.SetMilestone(Some(milestone)),
          workDays
        ),
        Set(t1.key),
        BulkEdit.SetAssignee(Some(assignee)),
        workDays
      )
      (result.head.statusId, result.head.milestoneId, result.head.assigneeId) shouldBe
        (Some(status), Some(milestone), Some(assignee))
    }

    "clear a milestone and an assignee" in {
      val set = t1.copy(milestoneId = Some(UUID.randomUUID()), assigneeId = Some(UUID.randomUUID()))
      val key = Set(set.key)
      val cleared = BulkTaskEditor(
        BulkTaskEditor(Seq(set), key, BulkEdit.SetMilestone(None), workDays),
        key,
        BulkEdit.SetAssignee(None),
        workDays
      )
      (cleared.head.milestoneId, cleared.head.assigneeId) shouldBe (None, None)
    }

    "add, remove and replace labels" in {
      val (x, y, z) = (UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
      val labelled = t1.copy(labelIds = Set(x))
      val key = Set(labelled.key)
      def run(labelEdit: BulkEdit) = BulkTaskEditor(Seq(labelled), key, labelEdit, workDays).head.labelIds
      (run(BulkEdit.AddLabels(Set(y))), run(BulkEdit.RemoveLabels(Set(x, z))), run(BulkEdit.SetLabels(Set(z)))) shouldBe
        (Set(x, y), Set.empty[UUID], Set(z))
    }

    "move the due date with the start date, keeping the length in work days" in {
      val result = edit(Seq(t1), BulkEdit.SetStartDate(monday.plusDays(2)))
      (result.head.startDate, result.head.dueDate) shouldBe (monday.plusDays(2), monday.plusDays(8))
    }

    "keep a due date that was picked by hand while it is still after the new start" in {
      val picked = edit(Seq(t1), BulkEdit.SetDueDate(monday.plusDays(11)))
      val moved = BulkTaskEditor(picked, Set(t1.key), BulkEdit.SetStartDate(monday.plusDays(1)), workDays)
      (moved.head.startDate, moved.head.dueDate) shouldBe (monday.plusDays(1), monday.plusDays(11))
    }

    "move a hand-picked due date along when the new start would pass it" in {
      val picked = edit(Seq(t1), BulkEdit.SetDueDate(monday.plusDays(1)))
      val moved = BulkTaskEditor(picked, Set(t1.key), BulkEdit.SetStartDate(monday.plusDays(3)), workDays)
      moved.head.dueDate.isBefore(moved.head.startDate) shouldBe false
    }

    "refuse a start or due date that is not a work day" in {
      val saturday = monday.plusDays(5)
      (edit(Seq(t1), BulkEdit.SetStartDate(saturday)), edit(Seq(t1), BulkEdit.SetDueDate(saturday))) shouldBe (all, all)
    }

    "refuse a due date before the task's start" in {
      edit(Seq(t1), BulkEdit.SetDueDate(t1.startDate.minusDays(3))) shouldBe all
    }

    "end the task after the given number of work days, over a weekend, and keep it when the start moves" in {
      val longer = edit(Seq(t1), BulkEdit.SetWorkDays(10)).head
      val moved = BulkTaskEditor(Seq(longer), Set(longer.key), BulkEdit.SetStartDate(monday.plusDays(1)), workDays).head
      (longer.dueDate, longer.dueDateManual, moved.dueDate) shouldBe (monday.plusDays(11), true, monday.plusDays(11))
    }

    "keep the number of work days between 1 and the maximum" in {
      (
        edit(Seq(t1), BulkEdit.SetWorkDays(0)).head.dueDate,
        edit(Seq(t1), BulkEdit.SetWorkDays(1)).head.dueDate,
        edit(Seq(t1), BulkEdit.SetWorkDays(100000)).head.dueDate ==
          edit(Seq(t1), BulkEdit.SetWorkDays(BulkEdit.MaxWorkDays)).head.dueDate
      ) shouldBe (t1.startDate, t1.startDate, true)
    }

    "leave tasks alone when nothing is selected" in {
      edit(Seq.empty, BulkEdit.SetPriority(TaskPriority.Low)) shouldBe all
    }

    "refuse every date when the project has no work days" in {
      BulkTaskEditor(all, Set(t1.key), BulkEdit.SetStartDate(monday), Set.empty[DayOfWeek]) shouldBe all
    }
  }

  "BulkSelection" should {
    val keys = all.map(_.key)

    "select the range between the anchor and the target in either direction" in {
      (BulkSelection.rangeBetween(keys, keys(0), keys(2)), BulkSelection.rangeBetween(keys, keys(2), keys(1))) shouldBe
        (keys.toSet, Set(keys(1), keys(2)))
    }

    "select just the target when the anchor is gone" in {
      BulkSelection.rangeBetween(keys, UUID.randomUUID(), keys(1)) shouldBe Set(keys(1))
    }

    "toggle on a plain click and remember the anchor" in {
      val (on, anchor) = BulkSelection.click(Set.empty, keys, None, keys(1), shift = false)
      val (off, _) = BulkSelection.click(on, keys, anchor, keys(1), shift = false)
      (on, anchor, off) shouldBe (Set(keys(1)), Some(keys(1)), Set.empty[UUID])
    }

    "extend the selection on a shift click" in {
      BulkSelection.click(Set(keys(0)), keys, Some(keys(0)), keys(2), shift = true) shouldBe
        (keys.toSet, Some(keys(2)))
    }

    "treat a shift click without an anchor as a plain click" in {
      BulkSelection.click(Set.empty, keys, None, keys(1), shift = true) shouldBe (Set(keys(1)), Some(keys(1)))
    }

    "select all, then clear when everything is selected" in {
      (BulkSelection.toggleAll(Set(keys(0)), keys), BulkSelection.toggleAll(keys.toSet, keys)) shouldBe
        (keys.toSet, Set.empty[UUID])
    }

    "not select anything of an empty list" in {
      BulkSelection.toggleAll(Set.empty, Seq.empty) shouldBe Set.empty[UUID]
    }

    "prune keys of removed tasks" in {
      BulkSelection.prune(Set(keys(0), UUID.randomUUID()), keys) shouldBe Set(keys(0))
    }
  }
}
