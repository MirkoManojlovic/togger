package com.togger.markdown

import com.togger.common.suites.AsyncJsWordSpec

class TaskProgressParserTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "report no progress for a description with no task list" in { _ =>
      TaskProgressParser.parse("just a paragraph") shouldBe TaskProgress(checked = 0, total = 0)
    }

    "count checked and unchecked items" in { _ =>
      TaskProgressParser.parse("- [x] one\n- [ ] two\n- [x] three") shouldBe TaskProgress(checked = 2, total = 3)
    }

    "ignore plain list items" in { _ =>
      TaskProgressParser.parse("- plain\n- [x] task") shouldBe TaskProgress(checked = 1, total = 1)
    }

    "round the percentage rather than floor it" in { _ =>
      TaskProgressParser.parse("- [x] one\n- [ ] two\n- [ ] three").percent shouldBe 33
    }

    "report 100 percent when every item is checked" in { _ =>
      TaskProgressParser.parse("- [x] one\n- [x] two").percent shouldBe 100
    }

    "report 0 percent for an empty task list" in { _ =>
      TaskProgressParser.parse("no checkboxes here").percent shouldBe 0
    }
  }
}
