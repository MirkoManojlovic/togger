package com.togger.ui.timebox.gantt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class GanttAssigneeColorsTest extends AnyWordSpec with Matchers {

  private def id(n: Int) = UUID.fromString(f"00000000-0000-0000-0000-$n%012d")

  "GanttAssigneeColors.Palette" should {
    "have twenty different colours" in {
      GanttAssigneeColors.Palette.distinct.size shouldBe 20
    }
  }

  "GanttAssigneeColors.classFor" should {
    "give the first member blue and the second green" in {
      val members = Seq(id(1), id(2))
      (GanttAssigneeColors.classFor(Some(id(1)), members), GanttAssigneeColors.classFor(Some(id(2)), members)) shouldBe
        (("bg-blue-600", "bg-green-600"))
    }

    "not depend on the order the members come in" in {
      val members = Seq(id(3), id(1), id(2))
      GanttAssigneeColors.classFor(Some(id(2)), members) shouldBe "bg-green-600"
    }

    "start over after the last colour" in {
      val members = (1 to 21).map(id)
      GanttAssigneeColors.classFor(Some(id(21)), members) shouldBe GanttAssigneeColors.Palette.head
    }

    "use the neutral colour for an unassigned task" in {
      GanttAssigneeColors.classFor(None, Seq(id(1))) shouldBe GanttAssigneeColors.Unassigned
    }

    "use the neutral colour for an assignee who is not a member" in {
      GanttAssigneeColors.classFor(Some(id(9)), Seq(id(1))) shouldBe GanttAssigneeColors.Unassigned
    }
  }
}
