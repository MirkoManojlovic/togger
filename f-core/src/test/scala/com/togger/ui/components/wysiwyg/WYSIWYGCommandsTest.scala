package com.togger.ui.components.wysiwyg

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.markdown.TgMarkdownStyles
import com.togger.ui.components.wysiwyg.WYSIWYGTestDom._
import org.scalatest.Assertion

class WYSIWYGCommandsTest extends AsyncJsWordSpec {

  private val styles = TgMarkdownStyles.default
  private val commands = new WYSIWYGCommands(styles)

  private def onSurface(markdown: String)(body: org.scalajs.dom.Element => Assertion): Assertion = {
    val element = surface(markdown)

    try body(element)
    finally discard(element)
  }

  testNameFromClassName should {

    "wrap a selected word in strong emphasis" in { _ =>
      onSurface("one two three") { element =>
        selectWord(element, "two")
        commands.toggleInline(element, "strong") shouldBe true
        serialize(element) shouldBe "one **two** three\n"
      }
    }

    "style a mark it creates like a rendered one" in { _ =>
      onSurface("one two three") { element =>
        selectWord(element, "two")
        commands.toggleInline(element, "strong")
        element.innerHTML should include(styles.strong)
      }
    }

    "unwrap a mark the selection already sits in" in { _ =>
      onSurface("one **two** three") { element =>
        selectWord(element, "two")
        commands.toggleInline(element, "strong") shouldBe true
        serialize(element) shouldBe "one two three\n"
      }
    }

    "split a mark when only part of it is selected" in { _ =>
      onSurface("**abcdef**") { element =>
        val bold = textNode(element)

        select(bold, 2, 4)
        commands.toggleInline(element, "strong")
        serialize(element) shouldBe "**ab**cd**ef**\n"
      }
    }

    "do nothing when wrapping a collapsed selection" in { _ =>
      onSurface("one two") { element =>
        caret(textNode(element), 3)
        commands.toggleInline(element, "strong") shouldBe false
        serialize(element) shouldBe "one two\n"
      }
    }

    "convert a paragraph into a heading" in { _ =>
      onSurface("some text") { element =>
        caret(textNode(element), 2)
        commands.setBlock(element, "h2") shouldBe true
        serialize(element) shouldBe "## some text\n"
      }
    }

    "convert a heading back into a paragraph" in { _ =>
      onSurface("## some text") { element =>
        caret(textNode(element), 2)
        commands.setBlock(element, "p")
        serialize(element) shouldBe "some text\n"
      }
    }

    "convert a paragraph into a bullet list" in { _ =>
      onSurface("an item") { element =>
        caret(textNode(element), 2)
        commands.toggleList(element, ordered = false) shouldBe true
        serialize(element) shouldBe "- an item\n"
      }
    }

    "convert a paragraph into a numbered list" in { _ =>
      onSurface("an item") { element =>
        caret(textNode(element), 2)
        commands.toggleList(element, ordered = true)
        serialize(element) shouldBe "1. an item\n"
      }
    }

    "lift a list item back out of its list" in { _ =>
      onSurface("- an item") { element =>
        caret(textNode(element), 2)
        commands.toggleList(element, ordered = false)
        serialize(element) shouldBe "an item\n"
      }
    }

    "wrap a selection in a link" in { _ =>
      onSurface("see the docs") { element =>
        selectWord(element, "docs")
        commands.insertLink(element, "https://example.com") shouldBe true
        serialize(element) shouldBe "see the [docs](https://example.com)\n"
      }
    }

    "report the marks the caret sits inside" in { _ =>
      onSurface("## a **bold** heading") { element =>
        selectWord(element, "bold")
        val marks = commands.activeMarks(element)

        marks should contain("strong")
        marks should contain("h2")
      }
    }

    "report nothing when the selection is outside the surface" in { _ =>
      onSurface("text") { element =>
        val outside = surface("elsewhere")

        try {
          caret(textNode(outside), 1)
          commands.activeMarks(element) shouldBe Set.empty
        } finally discard(outside)
      }
    }
  }
}
