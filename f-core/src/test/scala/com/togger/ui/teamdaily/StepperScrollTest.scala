package com.togger.ui.teamdaily

import com.togger.common.suites.AsyncJsWordSpec

class StepperScrollTest extends AsyncJsWordSpec {

  // five steps of 100 in a view of 250, so the 3rd is cut and the 4th and 5th are hidden
  private val steps = (0 until 5).map(i => StepSpan(i * 100.0, i * 100.0 + 100))

  testNameFromClassName should {

    "scroll right so the next hidden steps end at the edge" in { _ =>
      StepperScroll.targetRight(steps, scrollLeft = 0, viewWidth = 250, count = 1) shouldBe 50.0
      StepperScroll.targetRight(steps, scrollLeft = 0, viewWidth = 250, count = 2) shouldBe 150.0
    }

    "not go past the last step" in { _ =>
      StepperScroll.targetRight(steps, scrollLeft = 0, viewWidth = 250, count = 10) shouldBe 250.0
    }

    "stay put when nothing is hidden on the right" in { _ =>
      StepperScroll.targetRight(steps, scrollLeft = 250, viewWidth = 250, count = 2) shouldBe 250.0
    }

    "scroll left so the previous hidden steps start at the edge" in { _ =>
      StepperScroll.targetLeft(steps, scrollLeft = 250, count = 1) shouldBe 200.0
      StepperScroll.targetLeft(steps, scrollLeft = 250, count = 2) shouldBe 100.0
    }

    "not go before the first step" in { _ =>
      StepperScroll.targetLeft(steps, scrollLeft = 250, count = 10) shouldBe 0.0
    }

    "stay put when nothing is hidden on the left" in { _ =>
      StepperScroll.targetLeft(steps, scrollLeft = 0, count = 2) shouldBe 0.0
    }

    "know which way there is more to scroll" in { _ =>
      StepperScroll.canScrollLeft(0) shouldBe false
      StepperScroll.canScrollLeft(40) shouldBe true
      StepperScroll.canScrollRight(0, 250, 500) shouldBe true
      StepperScroll.canScrollRight(250, 250, 500) shouldBe false
    }
  }
}
