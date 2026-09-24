package com.togger.ui.emoji

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.ui.localstorage.LocalStorageTest

class EmojiSkinToneStoreTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "default to the neutral tone" in { _ =>
      new EmojiSkinToneStore(new LocalStorageTest).now() shouldBe SkinTone.Default
    }

    "persist the chosen tone" in { _ =>
      val localStorage = new LocalStorageTest
      new EmojiSkinToneStore(localStorage).set(SkinTone.MediumDark)

      new EmojiSkinToneStore(localStorage).now() shouldBe SkinTone.MediumDark
    }

    "fall back to the default for a non-numeric stored value" in { _ =>
      val localStorage = new LocalStorageTest
      localStorage.setItem(EmojiSkinToneStore.StorageKey, "abc")

      new EmojiSkinToneStore(localStorage).now() shouldBe SkinTone.Default
    }

    "fall back to the default for an out-of-range stored value" in { _ =>
      val localStorage = new LocalStorageTest
      localStorage.setItem(EmojiSkinToneStore.StorageKey, "9")

      new EmojiSkinToneStore(localStorage).now() shouldBe SkinTone.Default
    }

    "expose tones in emoji-mart's order" in { _ =>
      SkinTone.values.map(_.index) shouldBe IndexedSeq(0, 1, 2, 3, 4, 5)
    }

  }

}
