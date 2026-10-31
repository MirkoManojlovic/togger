package com.togger.ui.task

import com.togger.common.WorkDayCalendar
import com.togger.common.suites.AsyncJsWordSpec
import com.togger.dto.FindTaskStatusResp

import java.time.LocalDate
import java.util.UUID

class TaskCloseTest extends AsyncJsWordSpec {

  private val monday = LocalDate.of(2026, 10, 19)
  private val workDays = WorkDayCalendar.DefaultWorkDays

  private def status(name: String) = FindTaskStatusResp(UUID.randomUUID(), UUID.randomUUID(), name, "", "")

  testNameFromClassName should {

    "find the Done and Won't do statuses whatever emoji precedes their names" in { _ =>
      val done = status("✅ Done")
      val wontDo = status("🗑️ Won't do")
      TaskClose.closingStatuses(Seq(wontDo, status("Todo"), done)) shouldBe Seq(done, wontDo)
    }

    "find no closing status when the project has none" in { _ =>
      TaskClose.closingStatuses(Seq(status("Todo"))) shouldBe Seq.empty
    }

    "accept a work day on or after the start date" in { _ =>
      TaskClose.endDateErrors(Some(monday), monday, workDays) shouldBe Seq.empty
    }

    "reject an end date before the start date" in { _ =>
      TaskClose.endDateErrors(Some(monday.minusDays(3)), monday, workDays) should have size 1
    }

    "reject a weekend end date" in { _ =>
      TaskClose.endDateErrors(Some(monday.plusDays(5)), monday, workDays) should have size 1
    }

    "ask for an end date when none is picked" in { _ =>
      TaskClose.endDateErrors(None, monday, workDays) should have size 1
    }
  }
}
