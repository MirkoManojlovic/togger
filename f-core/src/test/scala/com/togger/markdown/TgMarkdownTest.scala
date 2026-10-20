package com.togger.markdown

import com.togger.common.suites.AsyncJsWordSpec

class TgMarkdownTest extends AsyncJsWordSpec {

  private def toHtml(markdown: String): String = new TgMarkdown().toHtml(markdown)

  testNameFromClassName should {

    "render a heading" in { _ =>
      toHtml("# Title") shouldBe "<h1>Title</h1>\n"
    }

    "render emphasis, strong emphasis and code spans" in { _ =>
      toHtml("*a* **b** `c`") shouldBe "<p><em>a</em> <strong>b</strong> <code>c</code></p>\n"
    }

    "render a bullet list" in { _ =>
      toHtml("- one\n- two") shouldBe "<ul>\n<li>one</li>\n<li>two</li>\n</ul>\n"
    }

    "escape HTML in text" in { _ =>
      toHtml("a < b & c") shouldBe "<p>a &lt; b &amp; c</p>\n"
    }

    "render a list item that is still empty when the text ends on its marker" in { _ =>
      toHtml("- ") shouldBe "<ul>\n<li></li>\n</ul>\n"
      toHtml("1. ") shouldBe "<ol>\n<li></li>\n</ol>\n"
      toHtml("- a\n- ") shouldBe "<ul>\n<li>a</li>\n<li></li>\n</ul>\n"
    }

    "parse a document that ends on an empty list item inside a block quote" in { _ =>
      new TgMarkdown().toLaminar("> - ") should not be empty
    }
  }
}
