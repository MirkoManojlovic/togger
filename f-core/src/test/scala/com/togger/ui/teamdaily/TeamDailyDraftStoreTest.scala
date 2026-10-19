package com.togger.ui.teamdaily

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.logentry.LogEntryStatus
import com.togger.ui.localstorage.LocalStorageTest

import java.time.LocalDate
import java.util.UUID

class TeamDailyDraftStoreTest extends AsyncJsWordSpec {

  private val account = UUID.randomUUID()
  private val project = UUID.randomUUID()
  private val entryKey = TeamDailyEntryKey(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 10, 19))
  private val draft = TeamDailyDraft(
    Some(project),
    Seq(entryKey.memberId),
    Some(Seq(entryKey.taskId)),
    Seq(TeamDailyEntryRecord(entryKey, TeamDailyEntryDraft(LogEntryStatus.Blocked, "external", skipped = false)))
  )

  testNameFromClassName should {

    "load what was saved" in { _ =>
      val store = new TeamDailyDraftStore(new LocalStorageTest, account, project)
      store.save(draft)
      store.load() shouldBe Some(draft)
    }

    "remove the draft when saving an empty one" in { _ =>
      val store = new TeamDailyDraftStore(new LocalStorageTest, account, project)
      store.save(draft)
      store.save(draft.copy(entries = Seq.empty))
      store.load() shouldBe None
    }

    "keep drafts of other projects apart" in { _ =>
      val storage = new LocalStorageTest
      new TeamDailyDraftStore(storage, account, project).save(draft)
      new TeamDailyDraftStore(storage, account, UUID.randomUUID()).load() shouldBe None
    }

    "ignore a draft of another version" in { _ =>
      val storage = new LocalStorageTest
      storage.setItem(s"${TeamDailyDraftStore.KeyPrefix}$account-$project", """{"version":999,"draft":{}}""")
      new TeamDailyDraftStore(storage, account, project).load() shouldBe None
    }

    "clear" in { _ =>
      val store = new TeamDailyDraftStore(new LocalStorageTest, account, project)
      store.save(draft)
      store.clear()
      store.load() shouldBe None
    }
  }
}
