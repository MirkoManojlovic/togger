package com.togger.ui.task.bulk

import com.togger.common.WorkDayCalendar
import com.togger.task.TaskPriority
import com.togger.ui.localstorage.LocalStorageTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate
import java.util.UUID

class BulkDraftStoreTest extends AnyWordSpec with Matchers {

  private val accountId = UUID.randomUUID()
  private val defaults =
    BulkTaskDefaults.of(LocalDate.of(2026, 10, 7), WorkDayCalendar.DefaultWorkDays, Seq.empty)

  private def task(name: String) = BulkTask.withDefaults(UUID.randomUUID(), name, defaults)

  private val full = task("B").copy(
    priority = TaskPriority.High,
    statusId = Some(UUID.randomUUID()),
    assigneeId = Some(UUID.randomUUID()),
    milestoneId = Some(UUID.randomUUID()),
    labelIds = Set(UUID.randomUUID(), UUID.randomUUID()),
    dueDateManual = true
  )
  private val draft = BulkDraft(Some(UUID.randomUUID()), "- A\n- B", Seq(task("A"), full))

  "BulkDraftStore" should {
    "give back what was saved, with every attribute of every task" in {
      val store = new BulkDraftStore(new LocalStorageTest, accountId)
      store.save(draft)
      store.load() shouldBe Some(draft)
    }

    "have no draft before anything was saved" in {
      new BulkDraftStore(new LocalStorageTest, accountId).load() shouldBe None
    }

    "forget the draft when it is cleared" in {
      val store = new BulkDraftStore(new LocalStorageTest, accountId)
      store.save(draft)
      store.clear()
      store.load() shouldBe None
    }

    "remove the draft instead of saving an empty one" in {
      val storage = new LocalStorageTest
      val store = new BulkDraftStore(storage, accountId)
      store.save(draft)
      store.save(BulkDraft(Some(UUID.randomUUID()), "  ", Seq.empty))
      (store.load(), storage.getItem(BulkDraftStore.KeyPrefix + accountId)) shouldBe (None, None)
    }

    "keep the drafts of different accounts apart" in {
      val storage = new LocalStorageTest
      val (mine, theirs) = (new BulkDraftStore(storage, accountId), new BulkDraftStore(storage, UUID.randomUUID()))
      mine.save(draft)
      (mine.load(), theirs.load()) shouldBe (Some(draft), None)
    }

    "ignore a draft that cannot be read" in {
      val storage = new LocalStorageTest
      storage.setItem(BulkDraftStore.KeyPrefix + accountId, "{not json")
      new BulkDraftStore(storage, accountId).load() shouldBe None
    }

    "ignore a draft written by another version" in {
      val storage = new LocalStorageTest
      val store = new BulkDraftStore(storage, accountId)
      store.save(draft)
      val key = BulkDraftStore.KeyPrefix + accountId
      storage.setItem(key, storage.getItem(key).get.replace("\"version\":1", "\"version\":999"))
      store.load() shouldBe None
    }

    "store a priority as its number" in {
      val storage = new LocalStorageTest
      new BulkDraftStore(storage, accountId).save(draft)
      storage.getItem(BulkDraftStore.KeyPrefix + accountId).get should include("\"priority\":2")
    }
  }
}
