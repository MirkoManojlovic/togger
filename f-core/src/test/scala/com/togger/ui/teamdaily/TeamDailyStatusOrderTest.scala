package com.togger.ui.daily

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.task.DefaultTaskStatus

class DailyStatusOrderTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "rank the four daily statuses in order" in { _ =>
      Seq(
        DefaultTaskStatus.InProgress,
        DefaultTaskStatus.Testing,
        DefaultTaskStatus.Waiting,
        DefaultTaskStatus.Blocked
      ).map(status => DailyStatusOrder.rank(status.statusName)) shouldBe Seq(Some(0), Some(1), Some(2), Some(3))
    }

    "ignore other statuses" in { _ =>
      DailyStatusOrder.rank(DefaultTaskStatus.Todo.statusName) shouldBe None
      DailyStatusOrder.rank("Something custom") shouldBe None
    }

    "recognise a name without its emoji" in { _ =>
      DailyStatusOrder.rank("in progress") shouldBe Some(0)
    }
  }
}
