package com.togger.ui.emoji

import com.togger.common.suites.AsyncJsWordSpec
import org.scalatest.OptionValues

class EmojiDataSetTest extends AsyncJsWordSpec with EmojiTestData with OptionValues {

  testNameFromClassName should {

    "render the skin variant for the chosen tone" in { _ =>
      thumbsUp.native(SkinTone.Default) shouldBe "👍"
      thumbsUp.native(SkinTone.Dark) shouldBe "👍🏿"
    }

    "clamp to the only variant for an emoji without skin tones" in { _ =>
      grinning.native(SkinTone.Dark) shouldBe "😀"
      grinning.hasSkinTones shouldBe false
      thumbsUp.hasSkinTones shouldBe true
    }

    "resolve every skin variant back to its base emoji" in { _ =>
      thumbsUp.skins.foreach(variant => dataSet.findByNative(variant).value shouldBe thumbsUp)
      dataSet.findByNative("😀").value shouldBe grinning
      dataSet.findByNative("🦄") shouldBe None
    }

    "skip unknown ids when resolving a category" in { _ =>
      dataSet.resolveAll(Vector("cat", "nope", "grinning")) shouldBe Vector(cat, grinning)
    }

    "number emoji by their position in the dataset" in { _ =>
      dataSet.positionById("+1") shouldBe 0
      dataSet.positionById("flag-us") shouldBe 4
    }

    "parse the trimmed dataset format" in { _ =>
      val json =
        """{
          |  "source": "test",
          |  "categories": [
          |    { "id": "people", "emojis": ["grinning", "missing"] },
          |    { "id": "unknown-category", "emojis": ["grinning"] }
          |  ],
          |  "emojis": {
          |    "grinning": { "name": "Grinning Face", "keywords": ["smile"], "skins": ["😀"] }
          |  }
          |}""".stripMargin

      EmojiDataLoader.parse(json) match {
        case EmojiDataState.Loaded(parsed) =>
          parsed.categories.map(_.id) shouldBe Vector(EmojiCategoryId.People)
          parsed.categories.head.emojiIds shouldBe Vector("grinning")
          parsed.get("grinning").value shouldBe Emoji("grinning", "Grinning Face", Vector("smile"), Vector("😀"))
        case other => fail(s"Expected a loaded dataset, got $other")
      }
    }

    "report malformed json as a failure" in { _ =>
      EmojiDataLoader.parse("not json at all") shouldBe a[EmojiDataState.Failed]
    }

  }

}
