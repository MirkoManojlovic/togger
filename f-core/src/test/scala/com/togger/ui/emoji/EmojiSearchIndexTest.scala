package com.togger.ui.emoji

import com.togger.common.suites.AsyncJsWordSpec

class EmojiSearchIndexTest extends AsyncJsWordSpec with EmojiTestData {

  private val index = EmojiSearchIndex(dataSet)

  testNameFromClassName should {

    "rank a prefix match above a substring match" in { _ =>
      index.search("cat").map(_.id) shouldBe Vector("cat", "copycat")
    }

    "require every term to match (AND)" in { _ =>
      index.search("flag us").map(_.id) shouldBe Vector("flag-us")
      index.search("flag grinning") shouldBe Vector.empty
    }

    "match on keywords alone" in { _ =>
      index.search("thumbsup").map(_.id) shouldBe Vector("+1")
    }

    "match on a name word" in { _ =>
      index.search("grinning face").map(_.id) shouldBe Vector("grinning")
    }

    "return nothing for a blank query" in { _ =>
      index.search("") shouldBe Vector.empty
      index.search("   ") shouldBe Vector.empty
    }

    "cap the number of results" in { _ =>
      index.search("cat", maxResults = 1).map(_.id) shouldBe Vector("cat")
    }

    "skip excluded emojis" in { _ =>
      index.search("cat", exceptEmojis = Set("cat")).map(_.id) shouldBe Vector("copycat")
    }

    "find nothing when no token matches" in { _ =>
      index.search("zzzz") shouldBe Vector.empty
    }

  }

}
