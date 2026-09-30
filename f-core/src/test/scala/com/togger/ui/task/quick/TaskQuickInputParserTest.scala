package com.togger.ui.task.quick

import com.togger.ui.task.quick.QuickDropdown._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TaskQuickInputParserTest extends AnyWordSpec with Matchers {

  "TaskQuickInputParser.parse" should {
    "return plain text as the task name with no tokens" in {
      TaskQuickInputParser.parse("New Task 01") shouldBe ParsedQuickInput("New Task 01", Seq.empty)
    }

    "recognise each trigger char" in {
      TaskQuickInputParser.parse("Fix ~app #bug !p1 +todo").dropdowns shouldBe
        Seq(ProjectDropdown, LabelsDropdown, PriorityDropdown, TaskStatusDropdown)
    }

    "strip tokens from the name and collapse whitespace" in {
      TaskQuickInputParser.parse("  Fix ~app   the #bug thing ").taskName shouldBe "Fix the thing"
    }

    "keep the query after the trigger" in {
      TaskQuickInputParser.parse("x #bu").tokens shouldBe Seq(QuickToken(LabelsDropdown, "bu", 2, 5))
    }

    "ignore trigger chars inside a word" in {
      val parsed = TaskQuickInputParser.parse("C++ a#b hello!")
      parsed.tokens shouldBe empty
      parsed.taskName shouldBe "C++ a#b hello!"
    }

    "accept a trailing trigger with an empty query" in {
      TaskQuickInputParser.parse("New ~").tokens shouldBe Seq(QuickToken(ProjectDropdown, "", 4, 5))
    }

    "return repeated triggers in input order" in {
      TaskQuickInputParser.parse("#a #b").tokens.map(_.query) shouldBe Seq("a", "b")
    }
  }

  "TaskQuickInputParser.activeTokenAt" should {
    val raw = "Fix ~app now"

    "be empty before the token" in {
      TaskQuickInputParser.activeTokenAt(raw, 3) shouldBe None
    }

    "be empty right before the trigger char" in {
      TaskQuickInputParser.activeTokenAt(raw, 4) shouldBe None
    }

    "find the token inside and right after it" in {
      TaskQuickInputParser.activeTokenAt(raw, 6).map(_.query) shouldBe Some("app")
      TaskQuickInputParser.activeTokenAt(raw, 8).map(_.query) shouldBe Some("app")
    }

    "be empty after the token" in {
      TaskQuickInputParser.activeTokenAt(raw, 10) shouldBe None
    }
  }
}
