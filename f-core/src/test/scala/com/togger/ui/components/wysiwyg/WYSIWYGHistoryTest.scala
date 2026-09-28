package com.togger.ui.components.wysiwyg

import com.togger.common.suites.AsyncJsWordSpec

class WYSIWYGHistoryTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "have nothing to undo when fresh" in { _ =>
      val history = new WYSIWYGHistory()

      history.reset("one")
      history.canUndo shouldBe false
      history.undo() shouldBe None
    }

    "step back through recorded snapshots" in { _ =>
      val history = new WYSIWYGHistory()

      history.reset("one")
      history.record("two")
      history.record("three")

      history.undo() shouldBe Some("two")
      history.undo() shouldBe Some("one")
      history.undo() shouldBe None
    }

    "step forward again after undoing" in { _ =>
      val history = new WYSIWYGHistory()

      history.reset("one")
      history.record("two")
      history.undo()

      history.canRedo shouldBe true
      history.redo() shouldBe Some("two")
      history.redo() shouldBe None
    }

    "drop the redo branch once a new edit lands" in { _ =>
      val history = new WYSIWYGHistory()

      history.reset("one")
      history.record("two")
      history.undo()
      history.record("different")

      history.canRedo shouldBe false
      history.redo() shouldBe None
      history.undo() shouldBe Some("one")
    }

    "ignore a snapshot identical to the current one" in { _ =>
      val history = new WYSIWYGHistory()

      history.reset("one")
      history.record("one")
      history.record("one")

      history.canUndo shouldBe false
    }

    "forget the oldest snapshots past its limit" in { _ =>
      val history = new WYSIWYGHistory(limit = 3)

      history.reset("0")
      (1 to 10).foreach(step => history.record(step.toString))

      (1 to 3).foreach(_ => history.undo())
      history.undo() shouldBe None
    }

    "clear both directions on reset" in { _ =>
      val history = new WYSIWYGHistory()

      history.reset("one")
      history.record("two")
      history.undo()
      history.reset("fresh")

      history.canUndo shouldBe false
      history.canRedo shouldBe false
    }
  }
}
