package com.togger.ui.updater

import com.togger.common.suites.AsyncJsWordSpec

class UpdateProgressModelTest extends AsyncJsWordSpec {

  testNameFromClassName should {
    "compute the percent of the download" in { _ =>
      UpdateProgressModel.percent(25L, Some(100L)) shouldBe Some(25)
      UpdateProgressModel.percent(150L, Some(100L)) shouldBe Some(100)
    }
    "have no percent without a usable total" in { _ =>
      UpdateProgressModel.percent(25L, None) shouldBe None
      UpdateProgressModel.percent(25L, Some(0L)) shouldBe None
    }
    "format the sizes" in { _ =>
      UpdateProgressModel.sizeText(2048L, None) shouldBe "2 KB"
      UpdateProgressModel.sizeText(1024L * 1024 * 5 / 2, Some(1024L * 1024 * 10)) shouldBe "2.5 MB of 10.0 MB"
    }
    "map the events of the updater to states" in { _ =>
      UpdateProgressModel.stateFor("downloading", 1L, Some(2L)) shouldBe Some(UpdateState.Downloading(1L, Some(2L)))
      UpdateProgressModel.stateFor("stopping", 0L, None) shouldBe Some(UpdateState.Stopping)
      UpdateProgressModel.stateFor("installing", 0L, None) shouldBe Some(UpdateState.Installing)
      UpdateProgressModel.stateFor("restarting", 0L, None) shouldBe Some(UpdateState.Restarting)
      UpdateProgressModel.stateFor("other", 0L, None) shouldBe None
    }
    "treat only the running phases as busy" in { _ =>
      UpdateState.isBusy(UpdateState.Downloading(0L, None)) shouldBe true
      UpdateState.isBusy(UpdateState.Restarting) shouldBe true
      UpdateState.isBusy(UpdateState.Failed("x")) shouldBe false
      UpdateState.isBusy(UpdateState.Available("1", "")) shouldBe false
    }
  }
}
