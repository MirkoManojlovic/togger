package com.togger.ui.components

import com.togger.common.suites.AsyncJsWordSpec

class TgStepperBreadcrumbTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "mark the current step as reached" in { _ =>
      TgStepperBreadcrumb.stateOf(index = 1, current = 1) shouldBe TgStepState.Reached
    }

    "mark an earlier step as reached" in { _ =>
      TgStepperBreadcrumb.stateOf(index = 0, current = 2) shouldBe TgStepState.Reached
    }

    "mark a later step as upcoming" in { _ =>
      TgStepperBreadcrumb.stateOf(index = 2, current = 1) shouldBe TgStepState.Upcoming
    }

    "mark every step as upcoming for a negative current" in { _ =>
      TgStepperBreadcrumb.stateOf(index = 0, current = -1) shouldBe TgStepState.Upcoming
    }

    "split a label on its first space" in { _ =>
      TgStepperBreadcrumb.splitLabel("A B C") shouldBe (("A", Some("B C")))
    }

    "keep a one-word label whole" in { _ =>
      TgStepperBreadcrumb.splitLabel("Review") shouldBe (("Review", None))
    }

  }

}
