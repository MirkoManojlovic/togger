package com.togger.markdown

import com.togger.common.suites.AsyncJsWordSpec

class TgMarkdownTasksTest extends AsyncJsWordSpec {

  private def states(markdown: String): Seq[Boolean] = TgMarkdownTasks.markers(markdown).map(_.checked)

  testNameFromClassName should {

    "find markers in document order" in { _ =>
      states("- [ ] one\n- [x] two\n- [ ] three") shouldBe Seq(false, true, false)
    }

    "accept an upper case marker, the way the renderer does" in { _ =>
      states("- [X] shouting") shouldBe Seq(true)
    }

    "ignore a plain list item" in { _ =>
      states("- plain\n- [x] task\n- plain again") shouldBe Seq(true)
    }

    "ignore brackets with nothing after them" in { _ =>
      states("- [x]") shouldBe empty
    }

    "find markers in ordered and nested lists" in { _ =>
      states("1. [ ] one\n   - [x] nested\n2) [ ] two") shouldBe Seq(false, true, false)
    }

    "count a marker inside a block quote, because the renderer does" in { _ =>
      states("> - [x] quoted") shouldBe Seq(true)
    }

    "skip a marker inside a fenced code block" in { _ =>
      states("- [ ] real\n\n```\n- [x] not a task\n```\n\n- [x] also real") shouldBe Seq(false, true)
    }

    "skip a tilde fence too" in { _ =>
      states("~~~\n- [x] code\n~~~") shouldBe empty
    }

    "check an item and leave the rest of the document alone" in { _ =>
      TgMarkdownTasks.setChecked("# Title\n\n- [ ] one\n- [ ] two\n", 1, checked = true) shouldBe
        Some("# Title\n\n- [ ] one\n- [x] two\n")
    }

    "uncheck an item" in { _ =>
      TgMarkdownTasks.setChecked("- [x] one", 0, checked = false) shouldBe Some("- [ ] one")
    }

    "keep indentation, the bullet character and the ordered marker style" in { _ =>
      TgMarkdownTasks.setChecked("  * [ ] starred\n1) [ ] parens", 1, checked = true) shouldBe
        Some("  * [ ] starred\n1) [x] parens")
    }

    /** `linesIterator` would drop this, and a rewrite that silently trims the document is a rewrite that shows up
      * as a spurious diff on every toggle.
      */
    "preserve a trailing newline" in { _ =>
      TgMarkdownTasks.setChecked("- [ ] one\n", 0, checked = true) shouldBe Some("- [x] one\n")
    }

    "number past a fenced code block the way the renderer does" in { _ =>
      TgMarkdownTasks.setChecked("- [ ] one\n\n```\n- [ ] fake\n```\n\n- [ ] two", 1, checked = true) shouldBe
        Some("- [ ] one\n\n```\n- [ ] fake\n```\n\n- [x] two")
    }

    "decline an index that resolves to nothing" in { _ =>
      TgMarkdownTasks.setChecked("- [ ] one", 4, checked = true) shouldBe None
    }

    /** What an index the renderer and the scanner disagree about looks like from here: the click costs nothing
      * rather than flipping some other line.
      */
    "decline a marker already in the requested state" in { _ =>
      TgMarkdownTasks.setChecked("- [x] one", 0, checked = true) shouldBe None
    }

    "decline anything in a document with no task items" in { _ =>
      TgMarkdownTasks.setChecked("just a paragraph", 0, checked = true) shouldBe None
    }
  }
}
