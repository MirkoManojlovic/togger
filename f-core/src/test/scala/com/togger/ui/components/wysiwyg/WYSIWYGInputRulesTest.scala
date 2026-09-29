package com.togger.ui.components.wysiwyg

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.markdown.TgMarkdownStyles
import com.togger.ui.components.wysiwyg.WYSIWYGTestDom._
import org.scalajs.dom
import org.scalatest.Assertion

class WYSIWYGInputRulesTest extends AsyncJsWordSpec {

  private val commands = new WYSIWYGCommands(TgMarkdownStyles.default)
  private val rules = new WYSIWYGInputRules(commands)

  /** Stands in for the author having just typed `typed` into an empty document: the text goes into the paragraph,
    * the caret goes after it, and the rule runs the way `onInput` would run it.
    */
  private def typed(text: String)(assert: (dom.Element, Boolean) => Assertion): Assertion = {
    val element = surface("placeholder")
    val node = textNode(element)

    try {
      node.data = text
      caret(node, text.length)
      assert(element, rules(element))
    } finally discard(element)
  }

  testNameFromClassName should {

    "turn a closed double marker into strong emphasis" in { _ =>
      typed("**bold**") { (element, fired) =>
        fired shouldBe true
        serialize(element) shouldBe "**bold**\n"
      }
    }

    "turn a closed single marker into emphasis" in { _ =>
      typed("*soft*") { (element, fired) =>
        fired shouldBe true
        serialize(element) shouldBe "*soft*\n"
      }
    }

    "turn backticks into a code span" in { _ =>
      typed("`code`") { (element, fired) =>
        fired shouldBe true
        serialize(element) shouldBe "`code`\n"
      }
    }

    "turn a tilde pair into strikethrough" in { _ =>
      typed("~~gone~~") { (element, fired) =>
        fired shouldBe true
        element.innerHTML should include("<del")
      }
    }

    "not fire on an unclosed marker" in { _ =>
      typed("**bold") { (_, fired) => fired shouldBe false }
    }

    "not fire on an empty marker pair" in { _ =>
      typed("****") { (_, fired) => fired shouldBe false }
    }

    "not read the tail of a double marker as emphasis" in { _ =>
      typed("**bold**") { (element, _) =>
        element.innerHTML should include("<strong")
        element.innerHTML should not include "<em"
      }
    }

    "leave the caret outside the new mark, so typing continues unmarked" in { _ =>
      typed("**bold**") { (element, _) =>
        val selection = dom.window.getSelection()
        val container = selection.getRangeAt(0).startContainer

        WYSIWYGDom.closest(element, container, e => WYSIWYGDom.tagOf(e) == "strong") shouldBe None
      }
    }

    "turn hashes at the start of a block into a heading" in { _ =>
      typed("## ") { (element, fired) =>
        fired shouldBe true
        element.innerHTML should include("<h2")
      }
    }

    "turn a dash at the start of a block into a bullet list" in { _ =>
      typed("- ") { (element, fired) =>
        fired shouldBe true
        element.innerHTML should include("<ul")
      }
    }

    "turn a number at the start of a block into an ordered list" in { _ =>
      typed("1. ") { (element, fired) =>
        fired shouldBe true
        element.innerHTML should include("<ol")
      }
    }

    "turn an angle bracket at the start of a block into a quote" in { _ =>
      typed("> ") { (element, fired) =>
        fired shouldBe true
        element.innerHTML should include("<blockquote")
      }
    }

    /** Browsers insert U+00A0 for a space that would otherwise collapse — which is every space typed at the end
      * of the content, and therefore every block marker.
      */
    "fire a block rule on a non-breaking space" in { _ =>
      typed("#\u00a0") { (element, fired) =>
        fired shouldBe true
        element.innerHTML should include("<h1")
      }
    }

    "fire the bullet rule on a non-breaking space" in { _ =>
      typed("-\u00a0") { (element, fired) =>
        fired shouldBe true
        element.innerHTML should include("<ul")
      }
    }

    "fire the ordered rule on a non-breaking space" in { _ =>
      typed("1.\u00a0") { (element, fired) =>
        fired shouldBe true
        element.innerHTML should include("<ol")
      }
    }

    "fire the quote rule on a non-breaking space" in { _ =>
      typed(">\u00a0") { (element, fired) =>
        fired shouldBe true
        element.innerHTML should include("<blockquote")
      }
    }

    "not fire a block rule away from the start of a block" in { _ =>
      typed("text # ") { (_, fired) => fired shouldBe false }
    }
  }
}
