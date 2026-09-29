package com.togger.ui.components.wysiwyg

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.markdown.TgMarkdown
import io.github.edadma.markdown.MarkdownConfig
import org.scalajs.dom
import org.scalatest.Assertion

/** The editor's correctness contract.
  *
  * `serialize(render(md))` is deliberately not `md` — the serializer normalizes, so `_a_` comes back `*a*` and a
  * setext heading comes back ATX. What has to hold is that a second pass changes nothing, because every edit the
  * user makes runs the document through another one. A construct that drifts on each pass would rewrite the
  * author's text a little more every time they typed.
  */
class MarkdownRoundTripTest extends AsyncJsWordSpec {

  private def pass(markdown: String, config: MarkdownConfig): String = {
    val host = dom.document.createElement("div")

    new TgMarkdown(config).toLaminar(markdown).foreach(element => host.appendChild(element.ref))
    MarkdownDomSerializer.serialize(host)
  }

  private def stable(markdown: String, config: MarkdownConfig = MarkdownConfig.default): Assertion = {
    val once = pass(markdown, config)

    pass(once, config) shouldBe once
  }

  private val everything =
    """# Heading one
      |
      |## Heading two
      |
      |A paragraph with *emphasis*, **strong**, `code` and [a link](https://example.com).
      |
      |- one
      |- two
      |  - nested
      |
      |1. first
      |2. second
      |
      |> a quote
      |
      |```scala
      |val x = 1
      |```
      |
      |![alt](pic.png)
      |
      |---
      |""".stripMargin

  testNameFromClassName should {

    "be stable over headings" in { _ =>
      stable("# One\n\n## Two\n\n###### Six")
    }

    "be stable over paragraphs and inline marks" in { _ =>
      stable("plain\n\n*a* **b** `c` [d](e.com)")
    }

    "be stable over nested lists" in { _ =>
      stable("- one\n  - nested\n    - deeper\n- two")
    }

    "be stable over ordered lists" in { _ =>
      stable("1. first\n2. second\n\n5. fifth")
    }

    "be stable over block quotes" in { _ =>
      stable("> quoted\n>\n> twice")
    }

    "be stable over code blocks" in { _ =>
      stable("```scala\nval x = 1\n```\n\n```\nplain\n```")
    }

    "be stable over tables" in { _ =>
      stable("| a | b |\n| :- | -: |\n| 1 | 2 |", MarkdownConfig.all)
    }

    "be stable over a document using every construct" in { _ =>
      stable(everything)
    }

    "be stable over text that looks like syntax" in { _ =>
      stable("a * b _ c ` d [ e ] f")
      stable("2 * 3 * 4 = 24")
      stable("see the file_name_here")
      succeed
    }

    "be stable over a paragraph starting with a block marker" in { _ =>
      stable("\\# not a heading")
      stable("\\- not a bullet")
      succeed
    }

    "keep text that looks like syntax readable after one pass" in { _ =>
      pass("2 * 3 * 4", MarkdownConfig.default) shouldBe "2 \\* 3 \\* 4\n"
    }

    "not lose content over ten passes" in { _ =>
      val once = pass(everything, MarkdownConfig.default)
      val ten = (1 to 10).foldLeft(once)((text, _) => pass(text, MarkdownConfig.default))

      ten shouldBe once
    }

    /** A task list is the one construct where the round trip is not just stable but *exact*: the renderer drops
      * the same four characters the serializer puts back.
      */
    "return a task list unchanged after one pass" in { _ =>
      pass("- [ ] open\n- [x] done\n- plain", TgMarkdown.defaultConfig) shouldBe "- [ ] open\n- [x] done\n- plain\n"
    }

    "be stable over task lists" in { _ =>
      stable("- [ ] open\n- [x] done", TgMarkdown.defaultConfig)
      stable("1. [x] numbered\n2. [ ] also", TgMarkdown.defaultConfig)
      stable("- [x] parent\n  - [ ] child", TgMarkdown.defaultConfig)
      succeed
    }

    /** With the extension off the marker is text, so it has to come back escaped rather than as a checkbox — and
      * escaped exactly once, however many passes it makes.
      */
    "be stable over a task marker when the extension is off" in { _ =>
      stable("- [ ] open", MarkdownConfig.default)
    }
  }
}
