package com.togger.markdown

import com.raquo.laminar.api.L._
import com.togger.common.suites.AsyncJsWordSpec
import io.github.edadma.markdown.MarkdownConfig
import org.scalajs.dom

class TgMarkdownRenderTest extends AsyncJsWordSpec {

  private val styles = TgMarkdownStyles.default

  private def html(
    markdown: String,
    config: MarkdownConfig = MarkdownConfig.default,
    allowRawHtml: Boolean = false,
    styles: TgMarkdownStyles = TgMarkdownStyles.default
  ): String = {
    val container = dom.document.createElement("div")

    dom.document.body.appendChild(container)

    val root = render(container, new TgMarkdown(config, allowRawHtml, styles).toLaminarDiv(markdown))
    val rendered = container.firstElementChild.innerHTML

    root.unmount()
    dom.document.body.removeChild(container)
    rendered
  }

  /** The structure assertions predate the Flowbite classes and are about the element tree, so the classes are
    * stripped back out rather than pasted into every expectation. Anything asserting on raw HTML in the source
    * uses [[html]] instead — escaped markup contains a literal `class="…"` that this would eat.
    */
  private def structure(markdown: String, config: MarkdownConfig = MarkdownConfig.default): String =
    html(markdown, config).replaceAll(""" class="[^"]*"""", "")

  private def occurrences(haystack: String, needle: String): Int = haystack.split(needle, -1).length - 1

  testNameFromClassName should {

    "render headings at the parsed level" in { _ =>
      structure("# One\n\n### Three") shouldBe "<h1>One</h1><h3>Three</h3>"
    }

    "render emphasis, strong emphasis and code spans inside a paragraph" in { _ =>
      structure("*a* **b** `c`") shouldBe "<p><em>a</em> <strong>b</strong> <code>c</code></p>"
    }

    "render a tight bullet list without wrapping items in a paragraph" in { _ =>
      structure("- one\n- two") shouldBe "<ul><li>one</li><li>two</li></ul>"
    }

    "keep paragraphs inside a loose list" in { _ =>
      structure("- one\n\n- two") shouldBe "<ul><li><p>one</p></li><li><p>two</p></li></ul>"
    }

    "carry an ordered list's start number" in { _ =>
      structure("3. three\n4. four") shouldBe "<ol start=\"3\"><li>three</li><li>four</li></ol>"
    }

    "tag a fenced code block with its language" in { _ =>
      html("```scala\nval x = 1\n```") should include("language-scala")
    }

    "render block quotes, thematic breaks and links" in { _ =>
      structure("> quoted\n\n---\n\n[text](https://example.com)") shouldBe
        "<blockquote><p>quoted</p></blockquote><hr>" +
        "<p><a href=\"https://example.com\">text</a></p>"
    }

    "use the image's inline content as its alt text" in { _ =>
      structure("![some *alt*](pic.png)") shouldBe "<p><img src=\"pic.png\" alt=\"some alt\"></p>"
    }

    "escape text rather than treating it as markup" in { _ =>
      structure("a < b & c") shouldBe "<p>a &lt; b &amp; c</p>"
    }

    "render a table when the extension is enabled" in { _ =>
      structure("| a | b |\n| - | -: |\n| 1 | 2 |", MarkdownConfig.all) shouldBe
        "<div><table><thead><tr><th>a</th><th style=\"text-align: right;\">b</th></tr></thead>" +
        "<tbody><tr><td>1</td><td style=\"text-align: right;\">2</td></tr></tbody></table></div>"
    }

    "escape inline raw HTML by default" in { _ =>
      html("text <span class=\"raw\">hi</span>") shouldBe
        s"""<p class="${styles.paragraph}">text &lt;span class="raw"&gt;hi&lt;/span&gt;</p>"""
    }

    "escape a raw HTML block by default" in { _ =>
      html("<div onclick=\"steal()\">hi</div>") shouldBe
        s"""<div class="${styles.rawHtmlBlock}">&lt;div onclick="steal()"&gt;hi&lt;/div&gt;\n</div>"""
    }

    "insert inline raw HTML when it is allowed" in { _ =>
      html("text <span class=\"raw\">hi</span>", allowRawHtml = true) shouldBe
        s"""<p class="${styles.paragraph}">text <span class="raw">hi</span></p>"""
    }

    "insert a raw HTML block, in a wrapper, when it is allowed" in { _ =>
      html("<div class=\"raw\">hi</div>", allowRawHtml = true) shouldBe
        s"""<div class="${styles.rawHtmlBlock}"><div class="raw">hi</div>\n</div>"""
    }

    "style each heading level from the matching field" in { _ =>
      val rendered = html("# One\n\n## Two\n\n###### Six")

      rendered should include(styles.heading1)
      rendered should include(styles.heading2)
      rendered should include(styles.heading6)
    }

    "keep both the heading classes and an explicit anchor id" in { _ =>
      val rendered = html("# Title {#anchor}", MarkdownConfig.all)

      rendered should include(styles.heading1)
      rendered should include("id=\"anchor\"")
    }

    "style a link with the brand token" in { _ =>
      html("[text](https://example.com)") should include(styles.link)
    }

    "style both halves of a fenced code block" in { _ =>
      val rendered = html("```scala\nval x = 1\n```")

      rendered should include(styles.codeBlock)
      rendered should include(styles.codeBlockCode)
    }

    /** Asserted on `ps-5` rather than the whole `nestedList` string: Laminar merges repeated `cls` modifiers and
      * drops duplicate names, and `space-y-1` is in both list styles, so the value never appears verbatim.
      */
    "indent a nested list and only the nested one" in { _ =>
      val rendered = html("- one\n  - nested")

      occurrences(rendered, "list-disc") shouldBe 2
      occurrences(rendered, "ps-5") shouldBe 1
    }

    "style a table down to its cells" in { _ =>
      val rendered = html("| a | b |\n| - | - |\n| 1 | 2 |", MarkdownConfig.all)

      rendered should include(styles.tableWrapper)
      rendered should include(styles.tableHead)
      rendered should include(styles.tableHeaderCell)
      rendered should include(styles.tableRow)
      rendered should include(styles.tableCell)
    }

    "leave the header row unstriped" in { _ =>
      occurrences(html("| a |\n| - |\n| 1 |", MarkdownConfig.all), styles.tableRow) shouldBe 1
    }

    "use an overridden style instead of the default" in { _ =>
      val rendered = html("hello", styles = styles.copy(paragraph = "text-xs text-body"))

      rendered shouldBe "<p class=\"text-xs text-body\">hello</p>"
    }
  }
}
