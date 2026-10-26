package com.togger.ui.synccheck

import com.raquo.airstream.ownership.OneTimeOwner
import com.raquo.laminar.api.L._
import com.togger.common.suites.AsyncJsWordSpec
import com.togger.ui.localstorage.LocalStorageTest

class SyncCheckSettingsStoreTest extends AsyncJsWordSpec {

  private def intervalOf(store: SyncCheckSettingsStore): Int = {
    var seen = 0
    store.intervalSeconds.foreach(seen = _)(new OneTimeOwner(() => ()))
    seen
  }

  testNameFromClassName should {

    "check every minute by default" in { _ =>
      intervalOf(new SyncCheckSettingsStore(new LocalStorageTest)) shouldBe 60
    }

    "keep a chosen interval across stores" in { _ =>
      val localStorage = new LocalStorageTest
      new SyncCheckSettingsStore(localStorage).set(300)

      intervalOf(new SyncCheckSettingsStore(localStorage)) shouldBe 300
    }

    "not go below the minimum interval" in { _ =>
      val store = new SyncCheckSettingsStore(new LocalStorageTest)
      store.set(1)

      intervalOf(store) shouldBe SyncCheckSettingsStore.MinIntervalSeconds
    }

    "fall back to the default when the stored value is garbage" in { _ =>
      val localStorage = new LocalStorageTest
      localStorage.setItem(SyncCheckSettingsStore.StorageKey, "soon")

      intervalOf(new SyncCheckSettingsStore(localStorage)) shouldBe SyncCheckSettingsStore.DefaultIntervalSeconds
    }

    "go back to the default on reset" in { _ =>
      val store = new SyncCheckSettingsStore(new LocalStorageTest)
      store.set(300)
      store.reset()

      intervalOf(store) shouldBe SyncCheckSettingsStore.DefaultIntervalSeconds
    }

  }

}
