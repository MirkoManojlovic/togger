package com.togger.ui.emoji

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.ui.localstorage.LocalStorageTest

class EmojiFrequentStoreTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "seed emoji-mart's default list when nothing is stored" in { _ =>
      val store = new EmojiFrequentStore(new LocalStorageTest)

      store.top(3) shouldBe EmojiFrequentStore.DefaultFrequent.take(3)
    }

    "put a frequently picked emoji first" in { _ =>
      val store = new EmojiFrequentStore(new LocalStorageTest)
      (1 to 20).foreach(_ => store.record("cat"))

      store.top(1) shouldBe Vector("cat")
    }

    "persist counts across stores" in { _ =>
      val localStorage = new LocalStorageTest
      val store = new EmojiFrequentStore(localStorage)
      (1 to 20).foreach(_ => store.record("cat"))

      new EmojiFrequentStore(localStorage).top(1) shouldBe Vector("cat")
    }

    "fall back to the seed when the stored value is garbage" in { _ =>
      val localStorage = new LocalStorageTest
      localStorage.setItem(EmojiFrequentStore.StorageKey, "not json at all")

      new EmojiFrequentStore(localStorage).top(1) shouldBe EmojiFrequentStore.DefaultFrequent.take(1)
    }

    "ignore non-numeric counts" in { _ =>
      val localStorage = new LocalStorageTest
      localStorage.setItem(EmojiFrequentStore.StorageKey, """{"cat": "lots"}""")

      new EmojiFrequentStore(localStorage).top(1) shouldBe EmojiFrequentStore.DefaultFrequent.take(1)
    }

    "return no more than asked for" in { _ =>
      val store = new EmojiFrequentStore(new LocalStorageTest)

      store.top(4).size shouldBe 4
    }

  }

}
