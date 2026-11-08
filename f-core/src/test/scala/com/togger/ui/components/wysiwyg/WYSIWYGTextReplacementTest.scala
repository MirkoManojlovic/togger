package com.togger.ui.components.wysiwyg

import com.togger.common.suites.AsyncJsWordSpec

class WYSIWYGTextReplacementTest extends AsyncJsWordSpec {

  private def apply(oldText: String, newText: String): String = {
    val edit = WYSIWYGTextReplacement.between(oldText, newText)

    oldText.patch(edit.start, edit.replacement, edit.end - edit.start)
  }

  testNameFromClassName should {

    "find an insertion, shifted to the first character that differs" in { _ =>
      WYSIWYGTextReplacement.between("one three", "one two three") shouldBe WYSIWYGTextReplacement(5, 5, "wo t")
    }

    "find a deletion" in { _ =>
      WYSIWYGTextReplacement.between("one two three", "one three") shouldBe WYSIWYGTextReplacement(5, 9, "")
    }

    "find a replacement" in { _ =>
      WYSIWYGTextReplacement.between("one two", "one 2") shouldBe WYSIWYGTextReplacement(4, 7, "2")
    }

    "replace only the word a bold command wraps" in { _ =>
      WYSIWYGTextReplacement.between("one two", "one **two**") shouldBe WYSIWYGTextReplacement(4, 7, "**two**")
    }

    "report no change for equal texts" in { _ =>
      WYSIWYGTextReplacement.between("same", "same") shouldBe WYSIWYGTextReplacement(4, 4, "")
    }

    "not let the suffix overlap the prefix" in { _ =>
      WYSIWYGTextReplacement.between("abab", "ab") shouldBe WYSIWYGTextReplacement(2, 4, "")
    }

    "rebuild the new text from the old one" in { _ =>
      val cases =
        List(("", "x"), ("x", ""), ("aaa", "aa"), ("aa", "aaa"), ("- a\n- b", "- a\n- b\n- "), ("héllo", "hello"))

      cases.foreach { case (oldText, newText) => apply(oldText, newText) shouldBe newText }
      succeed
    }
  }
}
