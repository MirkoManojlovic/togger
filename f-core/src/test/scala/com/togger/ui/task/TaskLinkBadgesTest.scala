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

    "not open other schemes or an empty address" in { _ =>
      TaskLinkBadges.safeHref("ftp://example.com") shouldBe None
      TaskLinkBadges.safeHref("  ") shouldBe None
    }
  }
}
