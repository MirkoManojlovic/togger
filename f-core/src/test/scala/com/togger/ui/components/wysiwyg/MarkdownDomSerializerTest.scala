package com.togger.ui.components.wysiwyg

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.markdown.TgMarkdown
import io.github.edadma.markdown.MarkdownConfig
import org.scalajs.dom

class MarkdownDomSerializerTest extends AsyncJsWordSpec {

  private def rendered(markdown: String, config: MarkdownConfig = MarkdownConfig.default): dom.Element = {
    val host = dom.document.createElement("div")

    new TgMarkdown(config).toLaminar(markdown).foreach(element => host.appendChild(element.ref))
    host
  }

  private def roundTrip(markdown: String, config: MarkdownConfig = MarkdownConfig.default): String =
    MarkdownDomSerializer.serialize(rendered(markdown, config))

  private def fromHtml(html: String): String = {
    val host = dom.document.createElement("div")

    host.innerHTML = html
    MarkdownDomSerializer.serialize(host)
  }

  testNameFromClassName should {

    "serialize headings as ATX" in { _ =>
      roundTrip("# One\n\n### Three") shouldBe "# One\n\n### Three\n"
    }

    "serialize a setext heading as ATX" in { _ =>
      roundTrip("Title\n=====") shouldBe "# Title\n"
    }

    "serialize a paragraph" in { _ =>
      roundTrip("hello there") shouldBe "hello there\n"
    }

    "serialize emphasis, strong emphasis and code spans" in { _ =>
      roundTrip("*a* **b** `c`") shouldBe "*a* **b** `c`\n"
    }

    "nest inline marks" in { _ =>
      roundTrip("**a *b* c**") shouldBe "**a *b* c**\n"
    }

    "normalize every bullet marker to a dash" in { _ =>
      roundTrip("* one\n* two") shouldBe "- one\n- two\n"
      roundTrip("+ one\n+ two") shouldBe "- one\n- two\n"
    }

    "indent a nested list by two spaces" in { _ =>
      roundTrip("- one\n  - nested\n- two") shouldBe "- one\n  - nested\n- two\n"
    }

    "keep an ordered list's start number" in { _ =>
      roundTrip("3. three\n4. four") shouldBe "3. three\n4. four\n"
    }

    "serialize a block quote" in { _ =>
      roundTrip("> quoted") shouldBe "> quoted\n"
    }

    "serialize a fenced code block with its language" in { _ =>
      roundTrip("```scala\nval x = 1\n```") shouldBe "```scala\nval x = 1\n```\n"
    }

    "serialize a thematic break" in { _ =>
      roundTrip("---") shouldBe "---\n"
    }

    "keep a link's destination" in { _ =>
      roundTrip("[text](https://example.com)") shouldBe "[text](https://example.com)\n"
    }

    "keep a link's title" in { _ =>
      roundTrip("""[text](https://example.com "a title")""") shouldBe
        "[text](https://example.com \"a title\")\n"
    }

    "keep an image's alt text and source" in { _ =>
      roundTrip("![alt](pic.png)") shouldBe "![alt](pic.png)\n"
    }

    "serialize a table" in { _ =>
      roundTrip("| a | b |\n| - | -: |\n| 1 | 2 |", MarkdownConfig.all) shouldBe
        "| a | b |\n| --- | ---: |\n| 1 | 2 |\n"
    }

    "serialize an empty surface to nothing" in { _ =>
      fromHtml("") shouldBe ""
      fromHtml("<p></p>") shouldBe ""
    }

    "unwrap a span the browser inserted" in { _ =>
      fromHtml("<p>a <span>b</span> c</p>") shouldBe "a b c\n"
    }

    "unwrap a font tag from a paste" in { _ =>
      fromHtml("""<p>a <font color="red">b</font> c</p>""") shouldBe "a b c\n"
    }

    "treat a browser-inserted div as a paragraph" in { _ =>
      fromHtml("<div>one</div><div>two</div>") shouldBe "one\n\ntwo\n"
    }

    "map b and i to strong and emphasis" in { _ =>
      fromHtml("<p><b>a</b> <i>c</i></p>") shouldBe "**a** *c*\n"
    }

    "escape characters that would otherwise be syntax" in { _ =>
      fromHtml("<p>a * b _ c ` d [ e</p>") shouldBe "a \\* b \\_ c \\` d \\[ e\n"
    }

    "escape a block marker at the start of a paragraph" in { _ =>
      fromHtml("<p># not a heading</p>") shouldBe "\\# not a heading\n"
      fromHtml("<p>1. not a list</p>") shouldBe "\\1. not a list\n"
      fromHtml("<p>- not a bullet</p>") shouldBe "\\- not a bullet\n"
    }

    "move whitespace outside emphasis markers" in { _ =>
      fromHtml("<p><strong>a </strong>b</p>") shouldBe "**a** b\n"
    }

    "lengthen a code span's fence around backticks" in { _ =>
      fromHtml("<p><code>a ` b</code></p>") shouldBe "``a ` b``\n"
    }

    "turn a hard break into two trailing spaces" in { _ =>
      fromHtml("<p>a<br>b</p>") shouldBe "a  \nb\n"
    }
  }
}
