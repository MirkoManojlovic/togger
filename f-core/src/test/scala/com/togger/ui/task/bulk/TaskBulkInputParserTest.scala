package com.togger.ui.task.bulk

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TaskBulkInputParserTest extends AnyWordSpec with Matchers {

  "TaskBulkInputParser.parse" should {
    "return nothing for empty input" in {
      TaskBulkInputParser.parse("") shouldBe ParsedBulkInput(Seq.empty, Seq.empty)
    }

    "parse a single task" in {
      TaskBulkInputParser.parse("- Task name 01").lines shouldBe Seq(ParsedBulkLine(1, "Task name 01"))
    }

    "parse many tasks in order" in {
      TaskBulkInputParser.parse("- Task name 01\n- Task name 02\n- Task name 03").lines.map(_.name) shouldBe
        Seq("Task name 01", "Task name 02", "Task name 03")
    }

    "skip blank lines but keep real line numbers" in {
      TaskBulkInputParser.parse("- A\n\n   \n- B") shouldBe
        ParsedBulkInput(Seq(ParsedBulkLine(1, "A"), ParsedBulkLine(4, "B")), Seq.empty)
    }

    "report a line without the prefix" in {
      val parsed = TaskBulkInputParser.parse("- A\nB\n- C")
      parsed.lines.map(_.name) shouldBe Seq("A", "C")
      parsed.issues shouldBe Seq(BulkParseIssue(2, BulkParseIssueReason.MissingPrefix))
    }

    "not treat a dash glued to a word as a task" in {
      TaskBulkInputParser.parse("-v2").issues shouldBe Seq(BulkParseIssue(1, BulkParseIssueReason.MissingPrefix))
    }

    "report a prefix with an empty name" in {
      TaskBulkInputParser.parse("-\n-   ").issues shouldBe
        Seq(BulkParseIssue(1, BulkParseIssueReason.EmptyName), BulkParseIssue(2, BulkParseIssueReason.EmptyName))
    }

    "collapse whitespace and trim" in {
      TaskBulkInputParser.parse("   -   Task    name   ").lines shouldBe Seq(ParsedBulkLine(1, "Task name"))
    }

    "handle Windows line endings" in {
      TaskBulkInputParser.parse("- A\r\n- B\r\n").lines.map(_.name) shouldBe Seq("A", "B")
    }

    "keep a dash inside the name" in {
      TaskBulkInputParser.parse("- Fix - the thing").lines.map(_.name) shouldBe Seq("Fix - the thing")
    }

    "accept very long names" in {
      val name = "x" * 5000
      TaskBulkInputParser.parse(s"- $name").lines.map(_.name) shouldBe Seq(name)
    }
  }

  "TaskBulkInputParser.applyOrder" should {
    "write the names over their lines in the new order" in {
      TaskBulkInputParser.applyOrder("- A\n- B\n- C", Seq("C", "A", "B")) shouldBe "- C\n- A\n- B"
    }

    "leave blank and invalid lines where they are" in {
      TaskBulkInputParser.applyOrder("- A\n\nnote\n- B", Seq("B", "A")) shouldBe "- B\n\nnote\n- A"
    }

    "keep a trailing newline" in {
      TaskBulkInputParser.applyOrder("- A\n- B\n", Seq("B", "A")) shouldBe "- B\n- A\n"
    }

    "leave the text alone when the number of names differs" in {
      TaskBulkInputParser.applyOrder("- A\n- B", Seq("A")) shouldBe "- A\n- B"
    }
  }
}
