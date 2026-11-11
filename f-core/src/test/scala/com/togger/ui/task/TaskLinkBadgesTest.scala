package com.togger.ui.task

import com.togger.common.suites.AsyncJsWordSpec

class TaskLinkBadgesTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "keep a web address as it is" in { _ =>
      TaskLinkBadges.safeHref(" https://example.com/a?b=1 ") shouldBe Some("https://example.com/a?b=1")
      TaskLinkBadges.safeHref("http://example.com") shouldBe Some("http://example.com")
    }

    "open a bare address as https" in { _ =>
      TaskLinkBadges.safeHref("example.com/x") shouldBe Some("https://example.com/x")
    }

    "not open scripts, data or files" in { _ =>
      TaskLinkBadges.safeHref("javascript:alert(1)") shouldBe None
      TaskLinkBadges.safeHref(" JavaScript:alert(1)") shouldBe None
      TaskLinkBadges.safeHref("data:text/html,x") shouldBe None
      TaskLinkBadges.safeHref("file:///etc/passwd") shouldBe None
    }

    "not open the other unsafe schemes" in { _ =>
      TaskLinkBadges.safeHref("vbscript:x") shouldBe None
      TaskLinkBadges.safeHref("blob:https://example.com/x") shouldBe None
      TaskLinkBadges.safeHref("view-source:https://example.com") shouldBe None
      TaskLinkBadges.safeHref("ms-msdt://x") shouldBe None
      TaskLinkBadges.safeHref("search-ms:query=x") shouldBe None
    }

    "keep any other scheme as it is" in { _ =>
      TaskLinkBadges.safeHref("togger://task/1") shouldBe Some("togger://task/1")
      TaskLinkBadges.safeHref("TOGGER://task/1") shouldBe Some("TOGGER://task/1")
      TaskLinkBadges.safeHref("ftp://example.com") shouldBe Some("ftp://example.com")
      TaskLinkBadges.safeHref("mailto:a@b.com") shouldBe Some("mailto:a@b.com")
    }

    "open a host with a port as https" in { _ =>
      TaskLinkBadges.safeHref("localhost:3000/x") shouldBe Some("https://localhost:3000/x")
    }

    "not open an empty address" in { _ =>
      TaskLinkBadges.safeHref("  ") shouldBe None
    }
  }
}
