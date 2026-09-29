package com.togger.ui.components.wysiwyg

import com.raquo.laminar.api.L._
import com.togger.common.suites.AsyncJsWordSpec
import com.togger.markdown.TgMarkdownStyles
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom
import org.scalatest.Assertion

import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.js

class WYSIWYGTextEditorSplitTest extends AsyncJsWordSpec {

  private case class TestForm(name: String = "untouched", description: String = "")

  private val debounce = 10
  private val settle = debounce * 6

  /** The syntax helper takes the component's own default debounce, not this suite's shortened one. */
  private val settleAtDefaultDebounce = 600

  /** The panes of a mounted editor, plus the caller's `Var` and everything the editor has reported into it. */
  private case class Mounted(
    source: dom.html.TextArea,
    preview: dom.Element,
    editor: dom.Element,
    state: Var[String],
    emitted: mutable.Buffer[String]
  )

  /** Mounts a real editor over a caller-owned `Var`, wired the way a form would wire it — the observer writes back
    * into the same `Var` the signal is read from, which is exactly the loop the editor has to absorb.
    */
  private def mounted(initial: String)(body: Mounted => Future[Assertion]): Future[Assertion] = {
    val state = Var(initial)
    val emitted = mutable.Buffer.empty[String]
    val container = dom.document.createElement("div")

    dom.document.body.appendChild(container)

    val editor = WYSIWYGTextEditorSplit(
      inputSignal = state.signal,
      inputStateUpdater = Observer[String] { value =>
        emitted += value
        state.set(value)
      },
      labelValue = "Status update",
      placeholderValue = "Write something",
      debounceTimeMillis = debounce,
      previewDebounceMillis = debounce
    )

    val root = render(container, editor.htmlElement)

    body(
      Mounted(
        source = container.querySelector("textarea").asInstanceOf[dom.html.TextArea],
        preview = container.querySelector("[aria-label='Status update preview']"),
        editor = container,
        state = state,
        emitted = emitted
      )
    ).andThen { case _ =>
      root.unmount()
      dom.document.body.removeChild(container)
    }
  }

  /** What a browser does when a key lands in a textarea: the value changes, then `input` fires. */
  private def typeInto(source: dom.html.TextArea, text: String): Unit = {
    source.value = source.value + text
    source.setSelectionRange(source.value.length, source.value.length)
    source.dispatchEvent(new dom.Event("input", js.Dynamic.literal(bubbles = true).asInstanceOf[dom.EventInit]))
  }

  private def click(element: dom.Element): Unit = {
    val init = js.Dynamic.literal(bubbles = true).asInstanceOf[dom.MouseEventInit]

    element.dispatchEvent(new dom.MouseEvent("click", init))
  }

  private def toolbarButton(editor: dom.Element, label: String): dom.Element =
    editor.querySelector(s"button[aria-label='$label']")

  testNameFromClassName should {

    "fill the source pane with the initial value" in { _ =>
      mounted("# Hello\n\nbody text") { view =>
        Future.successful(view.source.value shouldBe "# Hello\n\nbody text")
      }
    }

    "render the initial value into the preview pane" in { _ =>
      mounted("## Hello") { view =>
        inAsyncContextFuture(
          {
            view.preview.innerHTML should include("<h2")
            view.preview.innerHTML should include(TgMarkdownStyles.default.heading2)
            view.preview.textContent should include("Hello")
          },
          settle
        )
      }
    }

    "follow the source pane as it is edited" in { _ =>
      mounted("one") { view =>
        typeInto(view.source, "\n\n## two")
        inAsyncContextFuture(view.preview.innerHTML should include("<h2"), settle)
      }
    }

    "report what was typed once it settles" in { _ =>
      mounted("one") { view =>
        typeInto(view.source, " two")
        inAsyncContextFuture(view.emitted.toSeq shouldBe Seq("one two"), settle)
      }
    }

    "report nothing when nothing is edited" in { _ =>
      mounted("untouched") { view =>
        inAsyncContextFuture(view.emitted shouldBe empty, settle)
      }
    }

    /** The editor's central promise: the value is the author's text, not a rendering of a rendering of it. An
      * editor that parsed and re-serialized would hand back `*a*` for `_a_` and `- x` for `+ x`.
      */
    "report the author's own markdown rather than a canonical form of it" in { _ =>
      mounted("_italic_ and + a list marker") { view =>
        typeInto(view.source, "!")
        inAsyncContextFuture(view.emitted.toSeq shouldBe Seq("_italic_ and + a list marker!"), settle)
      }
    }

    "not touch the source pane when its own value echoes back through the signal" in { _ =>
      mounted("one") { view =>
        typeInto(view.source, " two")
        view.source.setSelectionRange(3, 3)

        inAsyncContextFuture(
          {
            // A reset would have re-assigned `value`, which is what moves a textarea's caret to the end.
            view.source.value shouldBe "one two"
            view.source.selectionStart shouldBe 3
          },
          settle
        )
      }
    }

    "take a different value that arrives from outside" in { _ =>
      mounted("one") { view =>
        view.state.set("## something else")
        inAsyncContextFuture(
          {
            view.source.value shouldBe "## something else"
            view.preview.innerHTML should include("<h2")
          },
          settle
        )
      }
    }

    "show a line in the preview instead of an empty pane while there is nothing to render" in { _ =>
      mounted("") { view =>
        inAsyncContextFuture(view.preview.textContent shouldBe "Nothing to preview", settle)
      }
    }

    "escape raw HTML rather than mounting it" in { _ =>
      mounted("<script>alert(1)</script>\n\n<b>not bold</b>") { view =>
        inAsyncContextFuture(
          {
            view.preview.getElementsByTagName("script").length shouldBe 0
            view.preview.textContent should include("<script>")
            view.preview.textContent should include("<b>not bold</b>")
          },
          settle
        )
      }
    }

    "keep both panes mounted, because the responsive split is CSS" in { _ =>
      mounted("text") { view =>
        Future.successful {
          view.editor.querySelectorAll("textarea").length shouldBe 1
          Option(view.preview) shouldBe defined
        }
      }
    }

    "put the placeholder on the source pane" in { _ =>
      mounted("") { view =>
        Future.successful(view.source.getAttribute("placeholder") shouldBe "Write something")
      }
    }

    "render a toolbar by default" in { _ =>
      mounted("text") { view =>
        Future.successful(Option(toolbarButton(view.editor, "Bold")) shouldBe defined)
      }
    }

    "apply a toolbar command to the selected source text and report the result" in { _ =>
      mounted("one two three") { view =>
        view.source.setSelectionRange(4, 7)
        click(toolbarButton(view.editor, "Bold"))

        view.source.value shouldBe "one **two** three"

        inAsyncContextFuture(view.emitted.toSeq shouldBe Seq("one **two** three"), settle)
      }
    }

    "leave the word selected after a toolbar command, so a second click undoes it" in { _ =>
      mounted("one two three") { view =>
        view.source.setSelectionRange(4, 7)
        click(toolbarButton(view.editor, "Bold"))
        click(toolbarButton(view.editor, "Bold"))

        Future.successful(view.source.value shouldBe "one two three")
      }
    }

    "prefix the caret's line from the heading button" in { _ =>
      mounted("title") { view =>
        view.source.setSelectionRange(2, 2)
        click(toolbarButton(view.editor, "Heading"))

        Future.successful(view.source.value shouldBe "## title")
      }
    }

    "offer a Write and a Preview tab for narrow screens" in { _ =>
      mounted("text") { view =>
        val tabs = view.editor.querySelectorAll("[role='tab']")

        Future.successful {
          tabs.length shouldBe 2
          tabs.map(_.textContent).toSeq shouldBe Seq("Write", "Preview")
        }
      }
    }

    "render task markers in the preview as checkboxes" in { _ =>
      mounted("- [ ] open\n- [x] done") { view =>
        inAsyncContextFuture(
          {
            view.preview.querySelectorAll("input[type='checkbox']").length shouldBe 2
            view.preview.textContent should not include "[ ]"
          },
          settle
        )
      }
    }

    "check the source's marker when a preview checkbox is clicked" in { _ =>
      mounted("- [ ] open\n- [ ] also open") { view =>
        inAsyncContextFuture((), settle).flatMap { _ =>
          click(view.preview.querySelectorAll("input[type='checkbox']")(1))

          view.source.value shouldBe "- [ ] open\n- [x] also open"

          inAsyncContextFuture(view.emitted.toSeq shouldBe Seq("- [ ] open\n- [x] also open"), settle)
        }
      }
    }

    "uncheck it again on a second click" in { _ =>
      mounted("- [x] done") { view =>
        inAsyncContextFuture((), settle).flatMap { _ =>
          click(view.preview.querySelector("input[type='checkbox']"))
          view.source.value shouldBe "- [ ] done"

          // The preview has to be rebuilt from the new source before the second click has a box to land on.
          inAsyncContextFuture((), settle).map { _ =>
            click(view.preview.querySelector("input[type='checkbox']"))
            view.source.value shouldBe "- [x] done"
          }
        }
      }
    }

    /** The click happened in the preview, so where the author was in the source pane is none of its business. */
    "leave the source pane's selection alone when a checkbox is clicked" in { _ =>
      mounted("- [ ] open") { view =>
        inAsyncContextFuture((), settle).map { _ =>
          view.source.setSelectionRange(2, 4)
          click(view.preview.querySelector("input[type='checkbox']"))

          view.source.selectionStart shouldBe 2
          view.source.selectionEnd shouldBe 4
        }
      }
    }

    /** The shape `CreateTaskComponent` and `EditTaskComponent` use: a form `Var`, a field selected by macro, and
      * the editor writing back into that field.
      */
    "write back into a form field through the syntax helper" in { _ =>
      val formState = Var(TestForm(description = "start"))
      val container = dom.document.createElement("div")

      dom.document.body.appendChild(container)

      val editor = formState.wysiwygSplitEditorSig(
        inputAndUpdater = selectorAndUpdaterMacro(_.description),
        labelValue = "Description"
      )
      val root = render(container, editor.htmlElement.amend(cls("mb-4")))
      val source = container.querySelector("textarea").asInstanceOf[dom.html.TextArea]

      source.value shouldBe "start"
      typeInto(source, " and more")

      inAsyncContextFuture(
        {
          formState.now().description shouldBe "start and more"
          formState.now().name shouldBe "untouched"
        },
        settleAtDefaultDebounce
      ).andThen { case _ =>
        root.unmount()
        dom.document.body.removeChild(container)
      }
    }
  }
}
