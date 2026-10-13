package com.togger.ui.components

import com.togger.common.suites.AsyncJsWordSpec

class TgProgressComponentTest extends AsyncJsWordSpec {

  private def fillClasses(
    color: TgProgressColor = TgProgressColor.Brand,
    size: TgProgressSize = TgProgressSize.Default,
    label: TgProgressLabel = TgProgressLabel.None
  ): Set[String] = TgProgressStyles.fill(color, size, label).split(" ").toSet

  testNameFromClassName should {

    "compute the percent" in { _ =>
      assert(TgProgressStyles.percent(45, 100) == 45)
      assert(TgProgressStyles.percent(1, 3) == 33)
      assert(TgProgressStyles.percent(2, 3) == 67)
    }

    "clamp the percent and survive a zero max" in { _ =>
      assert(TgProgressStyles.percent(150, 100) == 100)
      assert(TgProgressStyles.percent(-5, 100) == 0)
      assert(TgProgressStyles.percent(5, 0) == 0)
    }

    "use each color's fill class" in { _ =>
      assert(TgProgressColor.values.forall(c => fillClasses(color = c).contains(c.fillCls)))
    }

    "carry no dark: class on any color" in { _ =>
      assert(TgProgressColor.values.forall(!_.fillCls.contains("dark:")))
    }

    "use the plain height without an inside label" in { _ =>
      val outside = fillClasses(size = TgProgressSize.Large, label = TgProgressLabel.Outside("x"))
      assert(outside.contains("h-2.5") && outside.contains("rounded-full"))
      assert(!outside.contains("text-white"))
      assert(fillClasses(size = TgProgressSize.Large).contains("h-2.5"))
    }

    "use the inside height and centered white text with an inside label" in { _ =>
      val classes = fillClasses(size = TgProgressSize.Large, label = TgProgressLabel.Inside)
      assert(Set("h-5", "flex", "items-center", "justify-center", "text-white").subsetOf(classes))
      assert(!classes.contains("h-2.5"))
    }

    "never render a small bar thinner than the default when the label is inside" in { _ =>
      val classes = fillClasses(size = TgProgressSize.Small, label = TgProgressLabel.Inside)
      assert(classes.contains("h-4") && !classes.contains("h-1.5"))
    }

    "build the track with the extra class and no stray spaces" in { _ =>
      val track = TgProgressStyles.track(TgProgressLabel.None, "mt-2")
      assert(track == "w-full bg-neutral-quaternary rounded-full mt-2")
      assert(TgProgressStyles.track(TgProgressLabel.Inside, "") == "w-full bg-neutral-quaternary rounded-full")
    }

    "leave the extra class to the wrapper of an outside label" in { _ =>
      assert(!TgProgressStyles.track(TgProgressLabel.Outside("x"), "mt-2").contains("mt-2"))
    }
  }
}
