package com.togger.ui.daily

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.logentry.LogEntryStatus
import com.togger.ui.localstorage.LocalStorageTest

import java.time.LocalDate
import java.util.UUID

class DailyDraftStoreTest extends AsyncJsWordSpec {

  private val account = UUID.randomUUID()
  private val project = UUID.randomUUID()
  private val entryKey = DailyEntryKey(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 10, 19))
  private val draft = DailyDraft(
    Some(project),
    Seq(entryKey.memberId),
    todayFirst = true,
    Seq(DailyEntryRecord(entryKey, DailyEntryDraft(LogEntryStatus.Blocked, "internal", "external", skipped = false)))
  )

  testNameFromClassName should {

    "load what was saved" in { _ =>
      val store = new DailyDraftStore(new LocalStorageTest, account, project)
      store.save(draft)
      store.load() shouldBe Some(draft)
    }

    "remove the draft when saving an empty one" in { _ =>
      val store = new DailyDraftStore(new LocalStorageTest, account, project)
      store.save(draft)
      store.save(draft.copy(entries = Seq.empty))
      store.load() shouldBe None
    }

    "keep drafts of other projects apart" in { _ =>
      val storage = new LocalStorageTest
      new DailyDraftStore(storage, account, project).save(draft)
      new DailyDraftStore(storage, account, UUID.randomUUID()).load() shouldBe None
    }

    "ignore a draft of another version" in { _ =>
      val storage = new LocalStorageTest
      storage.setItem(s"${DailyDraftStore.KeyPrefix}$account-$project", """{"version":999,"draft":{}}""")
      new DailyDraftStore(storage, account, project).load() shouldBe None
    }

    "clear" in { _ =>
      val store = new DailyDraftStore(new LocalStorageTest, account, project)
      store.save(draft)
      store.clear()
      store.load() shouldBe None
    }
  }
}
