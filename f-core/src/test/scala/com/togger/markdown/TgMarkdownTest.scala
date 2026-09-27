package com.togger.markdown

import com.togger.common.suites.AsyncJsWordSpec

class TgMarkdownTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "render a heading" in { _ =>
      TgMarkdown.toHtml("# Title") shouldBe "<h1>Title</h1>\n"
    }

    "render emphasis, strong emphasis and code spans" in { _ =>
      TgMarkdown.toHtml("*a* **b** `c`") shouldBe "<p><em>a</em> <strong>b</strong> <code>c</code></p>\n"
    }

    "render a bullet list" in { _ =>
      TgMarkdown.toHtml("- one\n- two") shouldBe "<ul>\n<li>one</li>\n<li>two</li>\n</ul>\n"
    }

    "escape HTML in text" in { _ =>
      TgMarkdown.toHtml("a < b & c") shouldBe "<p>a &lt; b &amp; c</p>\n"
    }
  }
}
