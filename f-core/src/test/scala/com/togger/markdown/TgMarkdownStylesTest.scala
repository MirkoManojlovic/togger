package com.togger.markdown

import com.togger.common.suites.AsyncJsWordSpec

class TgMarkdownStylesTest extends AsyncJsWordSpec {

  private val styles = TgMarkdownStyles.default

  testNameFromClassName should {

    "map each heading level to its own field" in { _ =>
      (1 to 6).map(styles.heading) shouldBe
        Seq(styles.heading1, styles.heading2, styles.heading3, styles.heading4, styles.heading5, styles.heading6)
    }

    "clamp a heading level past six" in { _ =>
      styles.heading(7) shouldBe styles.heading6
    }

    "give every heading level a distinct style" in { _ =>
      (1 to 6).map(styles.heading).distinct.size shouldBe 6
    }

    "color a callout from its kind" in { _ =>
      styles.callout("warning") should include("bg-warning-soft")
      styles.callout("danger") should include("bg-danger-soft")
      styles.callout("success") should include("bg-success-soft")
    }

    "treat a callout kind's aliases the same" in { _ =>
      styles.callout("caution") shouldBe styles.callout("warning")
      styles.callout("error") shouldBe styles.callout("danger")
      styles.callout("tip") shouldBe styles.callout("success")
    }

    "match a callout kind regardless of case" in { _ =>
      styles.callout("WARNING") shouldBe styles.callout("warning")
    }

    "fall back to the brand tokens for an unknown callout kind" in { _ =>
      styles.callout("whatever") should include("bg-brand-softer")
      styles.callout("note") shouldBe styles.callout("whatever")
    }

    "override only the copied field" in { _ =>
      val overridden = styles.copy(paragraph = "text-xs")

      overridden.paragraph shouldBe "text-xs"
      overridden.heading1 shouldBe styles.heading1
      overridden.link shouldBe styles.link
    }

    "spell out every class as a literal, so Tailwind can scan them" in { _ =>
      val everyClass = styles.productIterator.collect { case s: String => s }.toSeq

      everyClass.filter(_.nonEmpty).foreach(_ should not include "$")
      everyClass.size should be > 30
    }
  }
}
