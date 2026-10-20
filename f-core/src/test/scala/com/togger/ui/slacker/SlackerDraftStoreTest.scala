package com.togger.ui.slacker

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.logentry.LogEntryStatus
import com.togger.ui.localstorage.LocalStorageTest
import com.togger.ui.teamdaily.TeamDailyEntryDraft

import java.time.LocalDate
import java.util.UUID

class SlackerDraftStoreTest extends AsyncJsWordSpec {

  private val account = UUID.randomUUID()
  private val project = UUID.randomUUID()
  private val taskId = UUID.randomUUID()
  private val draft = SlackerDraft(
    Some(project),
    Some(Seq(taskId)),
    Seq(taskId),
    Seq(
      SlackerEntryRecord(
        SlackerEntryKey(taskId, LocalDate.of(2026, 10, 19)),
        TeamDailyEntryDraft(LogEntryStatus.Blocked, "external")
      )
    )
  )

  testNameFromClassName should {

    "load what was saved" in { _ =>
      val store = new SlackerDraftStore(new LocalStorageTest, account, project)
      store.save(draft)
      store.load() shouldBe Some(draft)
    }

    "remove the draft when saving an empty one" in { _ =>
      val store = new SlackerDraftStore(new LocalStorageTest, account, project)
      store.save(draft)
      store.save(draft.copy(entries = Seq.empty))
      store.load() shouldBe None
    }

    "keep drafts of other projects apart" in { _ =>
      val storage = new LocalStorageTest
      new SlackerDraftStore(storage, account, project).save(draft)
      new SlackerDraftStore(storage, account, UUID.randomUUID()).load() shouldBe None
    }

    "forget a draft after clearing" in { _ =>
      val store = new SlackerDraftStore(new LocalStorageTest, account, project)
      store.save(draft)
      store.clear()
      store.load() shouldBe None
    }
  }
}
