package com.togger.ui.synccheck

import com.raquo.airstream.ownership.OneTimeOwner
import com.togger.common.suites.AsyncJsWordSpec
import com.togger.ui.localstorage.LocalStorageTest

class SyncIntervalSettingsStoreTest extends AsyncJsWordSpec {

  private def intervalOf(store: SyncIntervalSettingsStore): Int = {
    var seen = 0
    store.intervalSeconds.foreach(seen = _)(new OneTimeOwner(() => ()))
    seen
  }

  testNameFromClassName should {

    "check every minute by default" in { _ =>
      intervalOf(new SyncIntervalSettingsStore(new LocalStorageTest)) shouldBe 60
    }

    "keep a chosen interval across stores" in { _ =>
      val localStorage = new LocalStorageTest
      new SyncIntervalSettingsStore(localStorage).set(300)

      intervalOf(new SyncIntervalSettingsStore(localStorage)) shouldBe 300
    }

    "not go below the minimum interval" in { _ =>
      val store = new SyncIntervalSettingsStore(new LocalStorageTest)
      store.set(1)

      intervalOf(store) shouldBe SyncIntervalSettingsStore.MinIntervalSeconds
    }

    "fall back to the default when the stored value is garbage" in { _ =>
      val localStorage = new LocalStorageTest
      localStorage.setItem(SyncIntervalSettingsStore.StorageKey, "soon")

      intervalOf(new SyncIntervalSettingsStore(localStorage)) shouldBe SyncIntervalSettingsStore.DefaultIntervalSeconds
    }

    "go back to the default on reset" in { _ =>
      val store = new SyncIntervalSettingsStore(new LocalStorageTest)
      store.set(300)
      store.reset()

      intervalOf(store) shouldBe SyncIntervalSettingsStore.DefaultIntervalSeconds
    }

  }

}
