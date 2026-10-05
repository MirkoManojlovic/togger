package com.togger.ui.components

import com.togger.common.suites.AsyncJsWordSpec

class TgBreadcrumbsTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "treat a single item as the current page" in { _ =>
      TgBreadcrumbs.roleOf(index = 0, size = 1, hasPage = false) shouldBe TgCrumbRole.Current
    }

    "treat the last item as current even when it has a page" in { _ =>
      TgBreadcrumbs.roleOf(index = 2, size = 3, hasPage = true) shouldBe TgCrumbRole.Current
    }

    "treat an earlier item with a page as a link" in { _ =>
      TgBreadcrumbs.roleOf(index = 0, size = 3, hasPage = true) shouldBe TgCrumbRole.Link
    }

    "treat an earlier item without a page as text" in { _ =>
      TgBreadcrumbs.roleOf(index = 1, size = 3, hasPage = false) shouldBe TgCrumbRole.Text
    }

  }

}
