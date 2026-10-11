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
}
