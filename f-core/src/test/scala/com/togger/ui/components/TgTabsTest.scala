package com.togger.ui.components

import com.togger.common.suites.AsyncJsWordSpec

class TgTabsTest extends AsyncJsWordSpec {

  private val allEnabled = Set(0, 1, 2, 3)

  testNameFromClassName should {

    "move forward to the next enabled tab" in { _ =>
      TgTabsNavigation.next(current = 1, step = 1, enabled = allEnabled, size = 4) shouldBe Some(2)
    }

    "wrap forward past the last tab" in { _ =>
      TgTabsNavigation.next(current = 3, step = 1, enabled = allEnabled, size = 4) shouldBe Some(0)
    }

    "move backward to the previous enabled tab" in { _ =>
      TgTabsNavigation.next(current = 2, step = -1, enabled = allEnabled, size = 4) shouldBe Some(1)
    }

    "wrap backward past the first tab" in { _ =>
      TgTabsNavigation.next(current = 0, step = -1, enabled = allEnabled, size = 4) shouldBe Some(3)
    }

    "skip disabled tabs when moving forward" in { _ =>
      TgTabsNavigation.next(current = 0, step = 1, enabled = Set(0, 3), size = 4) shouldBe Some(3)
    }

    "skip disabled tabs when moving backward" in { _ =>
      TgTabsNavigation.next(current = 3, step = -1, enabled = Set(0, 3), size = 4) shouldBe Some(0)
    }

    "skip disabled tabs while wrapping" in { _ =>
      TgTabsNavigation.next(current = 2, step = 1, enabled = Set(1, 2), size = 4) shouldBe Some(1)
    }

    "stay put when the current tab is the only enabled one" in { _ =>
      TgTabsNavigation.next(current = 2, step = 1, enabled = Set(2), size = 4) shouldBe None
    }

    "stay put when the current tab is the only enabled one and moving backward" in { _ =>
      TgTabsNavigation.next(current = 2, step = -1, enabled = Set(2), size = 4) shouldBe None
    }

    "find nothing when no tab is enabled" in { _ =>
      TgTabsNavigation.next(current = 0, step = 1, enabled = Set.empty, size = 4) shouldBe None
    }

    "find nothing when there are no tabs at all" in { _ =>
      TgTabsNavigation.next(current = -1, step = 1, enabled = Set.empty, size = 0) shouldBe None
    }

    "start at the first enabled tab when nothing is selected yet" in { _ =>
      TgTabsNavigation.next(current = -1, step = 1, enabled = Set(1, 2), size = 4) shouldBe Some(1)
    }

    "start at the last enabled tab when nothing is selected yet and moving backward" in { _ =>
      TgTabsNavigation.next(current = -1, step = -1, enabled = Set(1, 2), size = 4) shouldBe Some(2)
    }

    "jump to the first enabled tab" in { _ =>
      TgTabsNavigation.first(Set(1, 2, 3)) shouldBe Some(1)
    }

    "jump to the last enabled tab" in { _ =>
      TgTabsNavigation.last(Set(0, 1, 2)) shouldBe Some(2)
    }

    "jump nowhere forward when every tab is disabled" in { _ =>
      TgTabsNavigation.first(Set.empty) shouldBe None
    }

    "jump nowhere backward when every tab is disabled" in { _ =>
      TgTabsNavigation.last(Set.empty) shouldBe None
    }

    "hand out a fresh id prefix per instance" in { _ =>
      TgTabs.nextIdPrefix() should not be TgTabs.nextIdPrefix()
    }

  }

}
