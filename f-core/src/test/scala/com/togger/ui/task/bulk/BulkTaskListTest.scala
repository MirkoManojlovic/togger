package com.togger.ui.task.bulk

import com.togger.common.WorkDayCalendar
import com.togger.task.TaskPriority
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate
import java.util.UUID

class BulkTaskListTest extends AnyWordSpec with Matchers {

  private val defaults = BulkTaskDefaults.of(LocalDate.of(2026, 10, 7), WorkDayCalendar.DefaultWorkDays, Seq.empty)

  private def task(name: String) = BulkTask.withDefaults(UUID.randomUUID(), name, defaults)
  private def reconcile(existing: Seq[BulkTask], names: String*) =
    BulkTaskList.reconcile(existing, names, defaults, () => UUID.randomUUID())

  private val (ta, tb, tc) = (task("A"), task("B"), task("C"))

  "BulkTaskList.reconcile" should {
    "create tasks with defaults from nothing" in {
      reconcile(Seq.empty, "A", "B").map(_.name) shouldBe Seq("A", "B")
    }

    "keep keys and attributes of tasks whose name is unchanged" in {
      val edited = tb.copy(priority = TaskPriority.High)
      val result = reconcile(Seq(ta, edited), "A", "B")
      (result.map(_.key), result(1).priority) shouldBe (Seq(ta.key, tb.key), TaskPriority.High)
    }

    "follow the order of the text" in {
      reconcile(Seq(ta, tb, tc), "C", "A", "B").map(_.key) shouldBe Seq(tc.key, ta.key, tb.key)
    }

    "drop tasks whose line was deleted" in {
      reconcile(Seq(ta, tb, tc), "A", "C").map(_.key) shouldBe Seq(ta.key, tc.key)
    }

    "keep the task of a line that is renamed in place" in {
      val edited = tb.copy(priority = TaskPriority.High)
      val result = reconcile(Seq(ta, edited, tc), "A", "B2", "C")
      (result.map(_.key), result(1).name, result(1).priority) shouldBe
        (Seq(ta.key, tb.key, tc.key), "B2", TaskPriority.High)
    }

    "add a new task for a new line" in {
      val result = reconcile(Seq(ta, tb), "A", "B", "C")
      (result.map(_.name), result.take(2).map(_.key)) shouldBe (Seq("A", "B", "C"), Seq(ta.key, tb.key))
    }

    "keep duplicate names apart in their original order" in {
      val (first, second) = (task("X"), task("X"))
      reconcile(Seq(first, second), "X", "X").map(_.key) shouldBe Seq(first.key, second.key)
    }
  }

  "BulkTaskList.moveToInsertion" should {
    "move a task down to a gap below it" in {
      BulkTaskList.moveToInsertion(Seq(ta, tb, tc), 0, 3).map(_.name) shouldBe Seq("B", "C", "A")
    }

    "move a task up to a gap above it" in {
      BulkTaskList.moveToInsertion(Seq(ta, tb, tc), 2, 0).map(_.name) shouldBe Seq("C", "A", "B")
    }

    "not move a task dropped next to itself" in {
      val tasks = Seq(ta, tb, tc)
      (BulkTaskList.moveToInsertion(tasks, 1, 1), BulkTaskList.moveToInsertion(tasks, 1, 2)) shouldBe (tasks, tasks)
    }

    "ignore an index that is out of range" in {
      (BulkTaskList.moveToInsertion(Seq(ta, tb), 5, 0), BulkTaskList.moveToInsertion(Seq(ta, tb), 0, 9)) shouldBe
        (Seq(ta, tb), Seq(ta, tb))
    }
  }

  "BulkTaskList.moveBy" should {
    "swap with the neighbour" in {
      val tasks = Seq(ta, tb, tc)
      (BulkTaskList.moveBy(tasks, 1, -1).map(_.name), BulkTaskList.moveBy(tasks, 1, 1).map(_.name)) shouldBe
        (Seq("B", "A", "C"), Seq("A", "C", "B"))
    }

    "stay put at the ends" in {
      val tasks = Seq(ta, tb)
      (BulkTaskList.moveBy(tasks, 0, -1), BulkTaskList.moveBy(tasks, 1, 1)) shouldBe (tasks, tasks)
    }
  }

  "BulkTaskList.reapplyDefaults" should {
    "reset attributes but keep keys and names" in {
      val edited = ta.copy(priority = TaskPriority.High)
      BulkTaskList.reapplyDefaults(Seq(edited), defaults) shouldBe Seq(ta)
    }
  }
}
