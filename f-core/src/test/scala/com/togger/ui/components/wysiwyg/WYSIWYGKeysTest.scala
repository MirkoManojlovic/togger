package com.togger.ui.components.wysiwyg

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.markdown.TgMarkdownStyles
import com.togger.ui.components.wysiwyg.WYSIWYGTestDom._
import org.scalajs.dom
import org.scalatest.Assertion

import scala.scalajs.js

class WYSIWYGKeysTest extends AsyncJsWordSpec {

  private val commands = new WYSIWYGCommands(TgMarkdownStyles.default)
  private val keys = new WYSIWYGKeys(commands)

  /** Built from a literal rather than `new dom.KeyboardEventInit { … }`: filling that anonymous subclass in
    * leaves `key` empty, so every handler would silently decline.
    */
  private def keyEvent(key: String, shift: Boolean = false): dom.KeyboardEvent =
    new dom.KeyboardEvent(
      "keydown",
      js.Dynamic
        .literal(key = key, shiftKey = shift, bubbles = true, cancelable = true)
        .asInstanceOf[dom.KeyboardEventInit]
    )

  private def onSurface(markdown: String)(body: dom.Element => Assertion): Assertion = {
    val element = surface(markdown)

    try body(element)
    finally discard(element)
  }

  testNameFromClassName should {

    "split a list item on Enter" in { _ =>
      onSurface("- onetwo") { element =>
        caret(textNode(element), 3)
        keys.onKeyDown(element, keyEvent("Enter")) shouldBe true
        serialize(element) shouldBe "- one\n- two\n"
      }
    }

    "leave the list when Enter is pressed in an empty item" in { _ =>
      onSurface("- one") { element =>
        val item = textNode(element)

        caret(item, item.data.length)
        keys.onKeyDown(element, keyEvent("Enter"))
        keys.onKeyDown(element, keyEvent("Enter")) shouldBe true
        serialize(element) shouldBe "- one\n"
      }
    }

    "start a paragraph when Enter is pressed in a heading" in { _ =>
      onSurface("## title") { element =>
        caret(textNode(element), 3)
        keys.onKeyDown(element, keyEvent("Enter")) shouldBe true
        serialize(element) shouldBe "## tit\n\nle\n"
      }
    }

    "split a paragraph on Enter" in { _ =>
      onSurface("onetwo") { element =>
        caret(textNode(element), 3)
        keys.onKeyDown(element, keyEvent("Enter")) shouldBe true
        serialize(element) shouldBe "one\n\ntwo\n"
      }
    }

    "insert a newline rather than a block inside a code block" in { _ =>
      onSurface("```\nab\n```") { element =>
        val code = textNode(element)

        caret(code, 1)
        keys.onKeyDown(element, keyEvent("Enter")) shouldBe true
        serialize(element) shouldBe "```\na\nb\n```\n"
      }
    }

    "not handle Shift+Enter" in { _ =>
      onSurface("text") { element =>
        caret(textNode(element), 2)
        keys.onKeyDown(element, keyEvent("Enter", shift = true)) shouldBe false
      }
    }

    "merge a paragraph into the one above on Backspace at its start" in { _ =>
      onSurface("one\n\ntwo") { element =>
        caret(textNode(element, 1), 0)
        keys.onKeyDown(element, keyEvent("Backspace")) shouldBe true
        serialize(element) shouldBe "onetwo\n"
      }
    }

    "not intercept Backspace away from a block start" in { _ =>
      onSurface("one\n\ntwo") { element =>
        caret(textNode(element, 1), 2)
        keys.onKeyDown(element, keyEvent("Backspace")) shouldBe false
      }
    }

    "indent a list item on Tab" in { _ =>
      onSurface("- one\n- two") { element =>
        caret(textNode(element, 1), 1)
        keys.onKeyDown(element, keyEvent("Tab")) shouldBe true
        serialize(element) shouldBe "- one\n  - two\n"
      }
    }

    "outdent a nested item on Shift+Tab" in { _ =>
      onSurface("- one\n  - two") { element =>
        caret(textNode(element, 1), 1)
        keys.onKeyDown(element, keyEvent("Tab", shift = true)) shouldBe true
        serialize(element) shouldBe "- one\n- two\n"
      }
    }

    "leave Tab alone outside a list, so it still moves focus" in { _ =>
      onSurface("plain text") { element =>
        caret(textNode(element), 2)
        keys.onKeyDown(element, keyEvent("Tab")) shouldBe false
      }
    }

    "not indent the first item of a list" in { _ =>
      onSurface("- only") { element =>
        caret(textNode(element), 1)
        keys.onKeyDown(element, keyEvent("Tab")) shouldBe false
      }
    }
  }
}
