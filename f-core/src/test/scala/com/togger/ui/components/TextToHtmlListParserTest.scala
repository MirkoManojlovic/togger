package com.togger.ui.components

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.ui.components.TextToHtmlListParser.ListItem

class TextToHtmlListParserTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "parse a single dash bullet, stripping the marker" in { _ =>
      TextToHtmlListParser.parse("- item") shouldBe Seq(ListItem("item"))
    }

    "treat *, + and - markers identically" in { _ =>
      val results = Seq("- item", "* item", "+ item").map(TextToHtmlListParser.parse)
      results.distinct shouldBe Seq(Seq(ListItem("item")))
    }

    "skip blank lines between bullets" in { _ =>
      TextToHtmlListParser.parse("- first\n\n- second") shouldBe Seq(ListItem("first"), ListItem("second"))
    }

    "skip a non-bullet line mixed between bullet lines" in { _ =>
      TextToHtmlListParser.parse("- first\nplain text\n- second") shouldBe
        Seq(ListItem("first"), ListItem("second"))
    }

    "nest an indented bullet under the preceding top-level item" in { _ =>
      TextToHtmlListParser.parse("- first\n  - nested") shouldBe
        Seq(ListItem("first", children = Seq(ListItem("nested"))))
    }

    "treat an indented bullet with no preceding top-level item as top-level" in { _ =>
      TextToHtmlListParser.parse("  - first") shouldBe Seq(ListItem("first"))
    }

    "clamp indentation deeper than one level to a single nested level" in { _ =>
      TextToHtmlListParser.parse("- first\n  - nested\n    - deeper") shouldBe
        Seq(ListItem("first", children = Seq(ListItem("nested"), ListItem("deeper"))))
    }

    "fall back to a single item holding the trimmed whole string when there are no bullets" in { _ =>
      TextToHtmlListParser.parse("  just a plain sentence  ") shouldBe Seq(ListItem("just a plain sentence"))
    }

    "parse to no items for an empty or whitespace-only string" in { _ =>
      (TextToHtmlListParser.parse(""), TextToHtmlListParser.parse("   \n  ")) shouldBe ((Nil, Nil))
    }

    "trim leading and trailing whitespace from item text" in { _ =>
      TextToHtmlListParser.parse("-   padded text   ") shouldBe Seq(ListItem("padded text"))
    }

  }

}
