package com.togger.ui.teamdaily

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.task.DefaultTaskStatus

class TeamDailyStatusOrderTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "rank the daily statuses in order, the done and won't do ones last" in { _ =>
      Seq(
        DefaultTaskStatus.InProgress,
        DefaultTaskStatus.Testing,
        DefaultTaskStatus.Waiting,
        DefaultTaskStatus.Blocked,
        DefaultTaskStatus.Done,
        DefaultTaskStatus.WontDo
      ).map(status => TeamDailyStatusOrder.rank(status.statusName)) shouldBe
        Seq(Some(0), Some(1), Some(2), Some(3), Some(4), Some(5))
    }

    "ignore other statuses" in { _ =>
      TeamDailyStatusOrder.rank(DefaultTaskStatus.Todo.statusName) shouldBe None
      TeamDailyStatusOrder.rank("Something custom") shouldBe None
    }

    "recognise a name without its emoji" in { _ =>
      TeamDailyStatusOrder.rank("in progress") shouldBe Some(0)
    }

    "know that waiting and blocked tasks are on hold" in { _ =>
      Seq(DefaultTaskStatus.Waiting, DefaultTaskStatus.Blocked).map(s =>
        TeamDailyStatusOrder.isOnHold(s.statusName)
      ) shouldBe
        Seq(true, true)
      TeamDailyStatusOrder.isOnHold(DefaultTaskStatus.InProgress.statusName) shouldBe false
    }
  }
}
