package com.togger.ui.components.wysiwyg

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.ui.components.wysiwyg.WYSIWYGMarkdownCommands._

/** The commands are pure transforms of text plus selection, so this suite needs no DOM at all.
  *
  * Cases are written in one marked-up notation: `{…}` is the selection, `|` is a caret. Both the input and the
  * expected output use it, which is what makes a command's effect on the *selection* as readable as its effect on
  * the text — and the selection is half of what these commands are for.
  */
class WYSIWYGMarkdownCommandsTest extends AsyncJsWordSpec {

  private def parse(marked: String): TextSelection =
    if (marked.contains("|")) {
      val caret = marked.indexOf('|')

      TextSelection(marked.replace("|", ""), caret, caret)
    } else
      TextSelection(marked.replace("{", "").replace("}", ""), marked.indexOf('{'), marked.indexOf('}') - 1)

  private def show(selection: TextSelection): String =
    if (selection.collapsed) selection.text.patch(selection.start, "|", 0)
    else selection.text.patch(selection.end, "}", 0).patch(selection.start, "{", 0)

  private def run(marked: String)(command: TextSelection => TextSelection): String =
    show(command(parse(marked)))

  testNameFromClassName should {

    "wrap a selected word and leave the word selected, not the markers" in { _ =>
      run("one {two} three")(toggleInline(_, "**")) shouldBe "one **{two}** three"
    }

    "unwrap a selection whose markers sit just outside it" in { _ =>
      run("one **{two}** three")(toggleInline(_, "**")) shouldBe "one {two} three"
    }

    "unwrap a selection that covers its own markers" in { _ =>
      run("one {**two**} three")(toggleInline(_, "**")) shouldBe "one {two} three"
    }

    "unwrap from a caret sitting inside the markers" in { _ =>
      run("one **tw|o** three")(toggleInline(_, "**")) shouldBe "one tw|o three"
    }

    "insert a marker pair around a caret and leave it between the halves" in { _ =>
      run("one | three")(toggleInline(_, "**")) shouldBe "one **|** three"
    }

    "read emphasis and strong apart" in { _ =>
      run("one **{two}** three")(toggleInline(_, "*")) shouldBe "one ***{two}*** three"
    }

    "wrap in code spans" in { _ =>
      run("call {now}")(toggleInline(_, "`")) shouldBe "call `{now}`"
    }

    "set a heading on the line the selection touches" in { _ =>
      run("{title}")(togglePrefix(_, "## ")) shouldBe "{## title}"
    }

    "replace a heading of another level rather than stacking one on it" in { _ =>
      run("### {title}")(togglePrefix(_, "## ")) shouldBe "## {title}"
    }

    "remove a heading the line already carries" in { _ =>
      run("## {title}")(togglePrefix(_, "## ")) shouldBe "{title}"
    }

    "move a caret past a marker it just added" in { _ =>
      run("|title")(togglePrefix(_, "## ")) shouldBe "## |title"
    }

    "quote every line the selection touches" in { _ =>
      run("{one\ntwo}")(togglePrefix(_, "> ")) shouldBe "{> one\n> two}"
    }

    "bullet every line the selection touches" in { _ =>
      run("{one\ntwo}")(toggleList(_, ordered = false)) shouldBe "{- one\n- two}"
    }

    "number an ordered list from one" in { _ =>
      run("{one\ntwo\nthree}")(toggleList(_, ordered = true)) shouldBe "{1. one\n2. two\n3. three}"
    }

    "remove a list marker every touched line already carries" in { _ =>
      run("{- one\n- two}")(toggleList(_, ordered = false)) shouldBe "{one\ntwo}"
    }

    "convert bullets to numbers rather than prefixing them twice" in { _ =>
      run("{- one\n- two}")(toggleList(_, ordered = true)) shouldBe "{1. one\n2. two}"
    }

    "expand a caret's line to the whole line before prefixing it" in { _ =>
      run("- al|ready")(toggleList(_, ordered = false)) shouldBe "al|ready"
    }

    "mark touched lines as tasks" in { _ =>
      run("{one\ntwo}")(toggleTaskList) shouldBe "{- [ ] one\n- [ ] two}"
    }

    "convert an existing list to tasks rather than prefixing it twice" in { _ =>
      run("{- one\n2. two}")(toggleTaskList) shouldBe "{- [ ] one\n- [ ] two}"
    }

    "strip a task marker whole, checkbox included" in { _ =>
      run("{- [ ] one\n- [x] two}")(toggleTaskList) shouldBe "{one\ntwo}"
    }

    "strip a task line back to plain text with the bullet button" in { _ =>
      run("{- [x] done}")(toggleList(_, ordered = false)) shouldBe "{done}"
    }

    "link a selection and keep the label selected" in { _ =>
      run("see {the docs} now")(insertLink(_, "https://x")) shouldBe "see [{the docs}](https://x) now"
    }

    "link a caret and leave it inside empty brackets" in { _ =>
      run("see |")(insertLink(_, "https://x")) shouldBe "see [|](https://x)"
    }

    "report strong and not emphasis inside a double marker" in { _ =>
      activeMarks(parse("a **b|c** d")) shouldBe Set("strong")
    }

    "report emphasis inside a single marker" in { _ =>
      activeMarks(parse("a *b|c* d")) shouldBe Set("em")
    }

    "report a code span" in { _ =>
      activeMarks(parse("`co|de`")) shouldBe Set("code")
    }

    "report every heading level under the toolbar's one heading key" in { _ =>
      activeMarks(parse("#### tit|le")) shouldBe Set("h2")
    }

    "report the list kind the caret is in" in { _ =>
      activeMarks(parse("- it|em")) shouldBe Set("ul")
      activeMarks(parse("1. it|em")) shouldBe Set("ol")
    }

    "report a task line as a task and not also as a bullet list" in { _ =>
      activeMarks(parse("- [ ] it|em")) shouldBe Set("task")
      activeMarks(parse("- [x] do|ne")) shouldBe Set("task")
    }

    "report a block quote" in { _ =>
      activeMarks(parse("> quo|ted")) shouldBe Set("blockquote")
    }

    "report nothing in plain text" in { _ =>
      activeMarks(parse("plain |text")) shouldBe empty
    }

    /** A pure function should not depend on a textarea being the only caller. */
    "survive a selection that runs past the end of the text" in { _ =>
      toggleInline(TextSelection("ab", 1, 99), "**").text shouldBe "a**b**"
    }
  }
}
