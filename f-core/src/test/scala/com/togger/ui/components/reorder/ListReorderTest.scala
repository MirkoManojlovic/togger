package com.togger.ui.components.reorder

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ListReorderTest extends AnyWordSpec with Matchers {

  private val abc = Seq("A", "B", "C")

  "ListReorder.moveToInsertion" should {
    "move an item below the last" in {
      ListReorder.moveToInsertion(abc, 0, 3) shouldBe Seq("B", "C", "A")
    }

    "move an item above the first" in {
      ListReorder.moveToInsertion(abc, 2, 0) shouldBe Seq("C", "A", "B")
    }

    "leave the list when the item would stay in place" in {
      (ListReorder.moveToInsertion(abc, 1, 1), ListReorder.moveToInsertion(abc, 1, 2)) shouldBe ((abc, abc))
    }

    "leave the list for positions out of range" in {
      (ListReorder.moveToInsertion(abc, 5, 0), ListReorder.moveToInsertion(abc, 0, 9)) shouldBe ((abc, abc))
    }
  }

  "ListReorder.moveBy" should {
    "move one step up or down" in {
      (ListReorder.moveBy(abc, 1, -1), ListReorder.moveBy(abc, 1, 1)) shouldBe
        ((Seq("B", "A", "C"), Seq("A", "C", "B")))
    }

    "do nothing at either end" in {
      (ListReorder.moveBy(abc, 0, -1), ListReorder.moveBy(abc, 2, 1)) shouldBe ((abc, abc))
    }
  }
}
