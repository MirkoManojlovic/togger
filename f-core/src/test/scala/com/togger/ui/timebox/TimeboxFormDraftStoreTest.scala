package com.togger.ui.timebox

import com.togger.ui.localstorage.LocalStorageTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class TimeboxFormDraftStoreTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val form = TimeboxForm("2026-10-12", "2026-10-16", "Sprint")

  "TimeboxFormDraftStore" should {
    "give back what was saved, in a new store over the same storage" in {
      val storage = new LocalStorageTest
      new TimeboxFormDraftStore(storage).save(projectId, form)
      new TimeboxFormDraftStore(storage).load(projectId) shouldBe Some(form)
    }

    "keep projects apart" in {
      val store = new TimeboxFormDraftStore(new LocalStorageTest)
      store.save(projectId, form)
      store.load(UUID.randomUUID()) shouldBe None
    }

    "forget the form once cleared" in {
      val store = new TimeboxFormDraftStore(new LocalStorageTest)
      store.save(projectId, form)
      store.clear(projectId)
      store.load(projectId) shouldBe None
    }

    "store nothing for an untouched form" in {
      val store = new TimeboxFormDraftStore(new LocalStorageTest)
      store.save(projectId, form)
      store.save(projectId, TimeboxForm())
      store.load(projectId) shouldBe None
    }

    "ignore an entry it cannot read" in {
      val storage = new LocalStorageTest
      storage.setItem(s"${TimeboxFormDraftStore.KeyPrefix}$projectId", "garbage")
      new TimeboxFormDraftStore(storage).load(projectId) shouldBe None
    }
  }
}
