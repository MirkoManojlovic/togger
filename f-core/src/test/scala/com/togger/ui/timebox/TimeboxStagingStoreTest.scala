package com.togger.ui.timebox

import com.togger.dto.{FindTaskRespFull, FindTaskStatusResp, SearchAccountDto}
import com.togger.task.{DefaultTaskStatus, TaskPriority}
import com.togger.ui.localstorage.LocalStorageTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class TimeboxStagingStoreTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val monday = LocalDate.of(2026, 10, 12)
  private val member = SearchAccountDto(UUID.randomUUID(), "Ada", "Lovelace", "ada@example.com", UUID.randomUUID())
  private val backlog = FindTaskStatusResp(UUID.randomUUID(), projectId, DefaultTaskStatus.Backlog.statusName, "")

  private def task(name: String) = FindTaskRespFull(
    id = UUID.randomUUID(),
    projectId = projectId,
    name = name,
    description = "",
    assignedTo = None,
    createdBy = member,
    closedBy = None,
    taskStatus = backlog,
    priority = TaskPriority.High,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    latestVersion = UUID.randomUUID(),
    logEntries = Seq.empty,
    links = Seq.empty,
    startDate = monday,
    dueDate = monday.plusDays(4)
  )

  private val first = task("A")
  private val second = task("B")

  "TimeboxStagingStore.toggled" should {
    "stage a task last" in {
      TimeboxStagingStore.toggled(Seq(first), second) shouldBe Seq(first, second)
    }

    "unstage a task that is staged" in {
      TimeboxStagingStore.toggled(Seq(first, second), first) shouldBe Seq(second)
    }
  }

  "TimeboxStagingStore.refreshed" should {
    "swap a staged task for its current state and drop one that is gone" in {
      val renamed = first.copy(name = "Renamed")
      TimeboxStagingStore.refreshed(Seq(first, second), Seq(renamed)) shouldBe Seq(renamed)
    }
  }

  "TimeboxStagingStore" should {
    "give back what was staged after a reload" in {
      val storage = new LocalStorageTest
      val store = new TimeboxStagingStore(storage)
      store.toggle(projectId, first)
      store.toggle(projectId, second)

      val reloaded = new TimeboxStagingStore(storage)
      reloaded.open(projectId)
      reloaded.now(projectId) shouldBe Seq(first, second)
    }

    "keep the projects apart" in {
      val store = new TimeboxStagingStore(new LocalStorageTest)
      store.toggle(projectId, first)
      store.now(UUID.randomUUID()) shouldBe Seq.empty
    }

    "leave nothing stored once everything is unstaged" in {
      val storage = new LocalStorageTest
      val store = new TimeboxStagingStore(storage)
      store.toggle(projectId, first)
      store.unstage(projectId, first.id)
      storage.getItem(s"${TimeboxStagingStore.KeyPrefix}$projectId") shouldBe None
    }

    "ignore an entry it can't read" in {
      val storage = new LocalStorageTest
      storage.setItem(s"${TimeboxStagingStore.KeyPrefix}$projectId", "not json")
      val store = new TimeboxStagingStore(storage)
      store.open(projectId)
      store.now(projectId) shouldBe Seq.empty
    }
  }

  "TimeboxStagingStore date overrides" should {
    val tuesday = monday.plusDays(1)

    "show the changed dates on the staged task" in {
      val store = new TimeboxStagingStore(new LocalStorageTest)
      store.toggle(projectId, first)
      store.setDates(projectId, first.id, tuesday, tuesday.plusDays(1))
      store.stagedWithDatesNow(projectId).map(t => (t.startDate, t.dueDate)) shouldBe
        Seq((tuesday, tuesday.plusDays(1)))
    }

    "keep the task's own dates in the staged list" in {
      val store = new TimeboxStagingStore(new LocalStorageTest)
      store.toggle(projectId, first)
      store.setDates(projectId, first.id, tuesday, tuesday)
      store.now(projectId) shouldBe Seq(first)
    }

    "forget a change that puts the task back on its own dates" in {
      val storage = new LocalStorageTest
      val store = new TimeboxStagingStore(storage)
      store.toggle(projectId, first)
      store.setDates(projectId, first.id, tuesday, tuesday)
      store.setDates(projectId, first.id, first.startDate, first.dueDate)
      storage.getItem(s"${TimeboxStagingStore.DatesKeyPrefix}$projectId") shouldBe None
    }

    "ignore dates for a task that is not staged" in {
      val store = new TimeboxStagingStore(new LocalStorageTest)
      store.toggle(projectId, first)
      store.setDates(projectId, second.id, tuesday, tuesday)
      store.stagedWithDatesNow(projectId) shouldBe Seq(first)
    }

    "list only the staged tasks with changed dates, with the new dates" in {
      val store = new TimeboxStagingStore(new LocalStorageTest)
      store.toggle(projectId, first)
      store.toggle(projectId, second)
      store.setDates(projectId, second.id, tuesday, tuesday)
      store.withChangedDatesNow(projectId).map(t => (t.id, t.startDate, t.dueDate)) shouldBe
        Seq((second.id, tuesday, tuesday))
    }

    "drop the change when the task is unstaged" in {
      val storage = new LocalStorageTest
      val store = new TimeboxStagingStore(storage)
      store.toggle(projectId, first)
      store.setDates(projectId, first.id, tuesday, tuesday)
      store.unstage(projectId, first.id)
      store.toggle(projectId, first)
      store.stagedWithDatesNow(projectId) shouldBe Seq(first)
    }

    "drop every change on clear" in {
      val storage = new LocalStorageTest
      val store = new TimeboxStagingStore(storage)
      store.toggle(projectId, first)
      store.setDates(projectId, first.id, tuesday, tuesday)
      store.clear(projectId)
      storage.getItem(s"${TimeboxStagingStore.DatesKeyPrefix}$projectId") shouldBe None
    }

    "survive a reload" in {
      val storage = new LocalStorageTest
      val store = new TimeboxStagingStore(storage)
      store.toggle(projectId, first)
      store.setDates(projectId, first.id, tuesday, tuesday)

      val reloaded = new TimeboxStagingStore(storage)
      reloaded.open(projectId)
      reloaded.stagedWithDatesNow(projectId).map(t => (t.startDate, t.dueDate)) shouldBe Seq((tuesday, tuesday))
    }

    "keep the change when the task is refreshed" in {
      val store = new TimeboxStagingStore(new LocalStorageTest)
      store.toggle(projectId, first)
      store.setDates(projectId, first.id, tuesday, tuesday)
      store.refresh(projectId, Seq(first.copy(name = "Renamed")))
      store.stagedWithDatesNow(projectId).map(t => (t.name, t.startDate)) shouldBe Seq(("Renamed", tuesday))
    }

    "ignore an entry it can't read" in {
      val storage = new LocalStorageTest
      storage.setItem(s"${TimeboxStagingStore.DatesKeyPrefix}$projectId", "not json")
      val store = new TimeboxStagingStore(storage)
      store.open(projectId)
      store.stagedWithDatesNow(projectId) shouldBe Seq.empty
    }
  }
}
