package com.togger.markdown

import com.raquo.laminar.api.L._
import com.togger.common.suites.AsyncJsWordSpec
import io.github.edadma.markdown.MarkdownConfig
import org.scalajs.dom

class TgMarkdownRenderTest extends AsyncJsWordSpec {

  private def html(
      markdown: String,
      config: MarkdownConfig = TgMarkdown.defaultConfig,
      allowRawHtml: Boolean = false
  ): String = {
    val container = dom.document.createElement("div")

    dom.document.body.appendChild(container)

    val root = render(container, TgMarkdown.toLaminarDiv(markdown, config, allowRawHtml))
    val rendered = container.firstElementChild.innerHTML

    root.unmount()
    dom.document.body.removeChild(container)
    rendered
  }

  testNameFromClassName should {

    "render headings at the parsed level" in { _ =>
      html("# One\n\n### Three") shouldBe "<h1>One</h1><h3>Three</h3>"
    }

    "render emphasis, strong emphasis and code spans inside a paragraph" in { _ =>
      html("*a* **b** `c`") shouldBe "<p><em>a</em> <strong>b</strong> <code>c</code></p>"
    }

    "render a tight bullet list without wrapping items in a paragraph" in { _ =>
      html("- one\n- two") shouldBe "<ul><li>one</li><li>two</li></ul>"
    }

    "keep paragraphs inside a loose list" in { _ =>
      html("- one\n\n- two") shouldBe "<ul><li><p>one</p></li><li><p>two</p></li></ul>"
    }

    "carry an ordered list's start number" in { _ =>
      html("3. three\n4. four") shouldBe "<ol start=\"3\"><li>three</li><li>four</li></ol>"
    }

    "tag a fenced code block with its language" in { _ =>
      html("```scala\nval x = 1\n```") shouldBe "<pre><code class=\"language-scala\">val x = 1\n</code></pre>"
    }

    "render block quotes, thematic breaks and links" in { _ =>
      html("> quoted\n\n---\n\n[text](https://example.com)") shouldBe
        "<blockquote><p>quoted</p></blockquote><hr>" +
        "<p><a href=\"https://example.com\">text</a></p>"
    }

    "use the image's inline content as its alt text" in { _ =>
      html("![some *alt*](pic.png)") shouldBe "<p><img src=\"pic.png\" alt=\"some alt\"></p>"
    }

    "escape text rather than treating it as markup" in { _ =>
      html("a < b & c") shouldBe "<p>a &lt; b &amp; c</p>"
    }

    "render a table when the extension is enabled" in { _ =>
      html("| a | b |\n| - | -: |\n| 1 | 2 |", MarkdownConfig.all) shouldBe
        "<table><thead><tr><th>a</th><th style=\"text-align: right;\">b</th></tr></thead>" +
        "<tbody><tr><td>1</td><td style=\"text-align: right;\">2</td></tr></tbody></table>"
    }

    "escape inline raw HTML by default" in { _ =>
      html("text <span class=\"raw\">hi</span>") shouldBe
        "<p>text &lt;span class=\"raw\"&gt;hi&lt;/span&gt;</p>"
    }

    "escape a raw HTML block by default" in { _ =>
      html("<div onclick=\"steal()\">hi</div>") shouldBe
        "<div>&lt;div onclick=\"steal()\"&gt;hi&lt;/div&gt;\n</div>"
    }

    "insert inline raw HTML when it is allowed" in { _ =>
      html("text <span class=\"raw\">hi</span>", allowRawHtml = true) shouldBe
        "<p>text <span class=\"raw\">hi</span></p>"
    }

    "insert a raw HTML block, in a wrapper, when it is allowed" in { _ =>
      html("<div class=\"raw\">hi</div>", allowRawHtml = true) shouldBe
        "<div><div class=\"raw\">hi</div>\n</div>"
    }
  }
}
