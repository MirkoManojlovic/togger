package com.togger.ui.components.wysiwyg

import com.raquo.laminar.api.L._
import com.togger.common.suites.AsyncJsWordSpec
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorLive.WYSIWYGTextEditorLiveSyntax
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom
import org.scalatest.Assertion

import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.js

class WYSIWYGTextEditorLiveTest extends AsyncJsWordSpec {

  private case class TestForm(name: String = "untouched", description: String = "")

  private val debounce = 10
  private val settle = debounce * 6

  /** The syntax helper takes the component's own default debounce, not this suite's shortened one. */
  private val settleAtDefaultDebounce = 600

  /** Mounts a real editor over a caller-owned `Var`, wired the way a form would wire it — the observer writes back
    * into the same `Var` the signal is read from, which is exactly the loop the editor has to absorb.
    */
  private def mounted(
    initial: String
  )(body: (dom.Element, Var[String], mutable.Buffer[String]) => Future[Assertion]): Future[Assertion] = {
    val state = Var(initial)
    val emitted = mutable.Buffer.empty[String]
    val container = dom.document.createElement("div")

    dom.document.body.appendChild(container)

    val editor = WYSIWYGTextEditorLive(
      inputSignal = state.signal,
      inputStateUpdater = Observer[String] { value =>
        emitted += value
        state.set(value)
      },
      labelValue = "Status update",
      placeholderValue = "Write something",
      debounceTimeMillis = debounce
    )

    val root = render(container, editor.htmlElement)
    val surface = container.querySelector("[contenteditable]")

    body(surface, state, emitted).andThen { case _ =>
      root.unmount()
      dom.document.body.removeChild(container)
    }
  }

  private def typeInto(surface: dom.Element, text: String): Unit = {
    WYSIWYGTestDom.textNodes(surface).lastOption.foreach { node =>
      node.data = node.data + text
      WYSIWYGTestDom.caret(node, node.data.length)
    }
    surface.dispatchEvent(new dom.Event("input", js.Dynamic.literal(bubbles = true).asInstanceOf[dom.EventInit]))
  }

  /** Types one character at a time through the caret, the way a browser does — including reading the offset
    * before touching the node, since a live range shifts when text data changes underneath it.
    */
  private def typeAtCaret(surface: dom.Element, text: String): Unit = {
    val selection = dom.window.getSelection()

    if (selection.rangeCount > 0) {
      val range = selection.getRangeAt(0)

      range.startContainer match {
        case node: dom.Text =>
          val at = range.startOffset

          node.data = node.data.substring(0, at) + text + node.data.substring(at)
          WYSIWYGDom.caretAt(node, at + text.length)
        case element: dom.Element =>
          val node = dom.document.createTextNode(text)

          if (element.firstChild != null && WYSIWYGDom.tagOf(element.firstChild) == "br")
            element.replaceChild(node, element.firstChild)
          else element.appendChild(node)
          WYSIWYGDom.caretAt(node, text.length)
      }
    }
    surface.dispatchEvent(new dom.Event("input", js.Dynamic.literal(bubbles = true).asInstanceOf[dom.EventInit]))
  }

  testNameFromClassName should {

    /** The reported bug: typing `# Heading` in an empty editor left the text as it was. Browsers insert U+00A0
      * for a space at the end of the content, and the block rules only matched an ordinary space.
      */
    "turn a hash and the space a browser inserts into a heading" in { _ =>
      mounted("") { (surface, _, _) =>
        WYSIWYGDom.caretAtStartOf(surface.firstChild)
        typeAtCaret(surface, "#")
        typeAtCaret(surface, "\u00a0")
        typeAtCaret(surface, "E")

        Future.successful {
          surface.innerHTML should include("<h1")
          surface.textContent should include("E")
        }
      }
    }

    "turn a dash and the space a browser inserts into a list" in { _ =>
      mounted("") { (surface, _, _) =>
        WYSIWYGDom.caretAtStartOf(surface.firstChild)
        typeAtCaret(surface, "-")
        typeAtCaret(surface, "\u00a0")

        Future.successful(surface.innerHTML should include("<ul"))
      }
    }


    "paint the initial value" in { _ =>
      mounted("# Hello\n\nbody text") { (surface, _, _) =>
        Future.successful {
          surface.innerHTML should include("<h1")
          surface.textContent should include("Hello")
        }
      }
    }

    "report markdown after an edit settles" in { _ =>
      mounted("one") { (surface, _, emitted) =>
        typeInto(surface, " two")
        inAsyncContextFuture(emitted.toSeq shouldBe Seq("one two\n"), settle)
      }
    }

    "report nothing when nothing is edited" in { _ =>
      mounted("untouched") { (_, _, emitted) =>
        inAsyncContextFuture(emitted shouldBe empty, settle)
      }
    }

    "not repaint when its own value echoes back through the signal" in { _ =>
      mounted("one") { (surface, _, _) =>
        val before = surface.firstChild

        typeInto(surface, " two")
        inAsyncContextFuture(
          {
            // A repaint would have replaced the paragraph wholesale; surviving identity is what proves the loop
            // guard held while the emitted value came back in through inputSignal.
            surface.firstChild shouldBe before
          },
          settle
        )
      }
    }

    "repaint when a different value arrives from outside" in { _ =>
      mounted("one") { (surface, state, _) =>
        val before = surface.firstChild

        state.set("## something else")
        inAsyncContextFuture(
          {
            surface.firstChild should not be before
            surface.innerHTML should include("<h2")
          },
          settle
        )
      }
    }

    "escape raw HTML rather than mounting it" in { _ =>
      mounted("<script>alert(1)</script>\n\n<b>not bold</b>") { (surface, _, _) =>
        Future.successful {
          surface.getElementsByTagName("script").length shouldBe 0
          surface.textContent should include("<script>")
          surface.textContent should include("<b>not bold</b>")
        }
      }
    }

    "show a placeholder only while empty" in { _ =>
      mounted("") { (surface, state, _) =>
        val container = surface.parentNode.asInstanceOf[dom.Element]

        container.textContent should include("Write something")
        state.set("now it has content")
        inAsyncContextFuture(container.textContent should not include "Write something", settle)
      }
    }

    "keep an editable block to put the caret in when emptied" in { _ =>
      mounted("") { (surface, _, _) =>
        Future.successful(surface.getElementsByTagName("p").length shouldBe 1)
      }
    }

    "render a toolbar by default" in { _ =>
      mounted("text") { (surface, _, _) =>
        Future.successful {
          val editor = surface.parentNode.parentNode.asInstanceOf[dom.Element]

          editor.getElementsByTagName("button").length should be > 0
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

      val editor = formState.wysiwygLiveEditorSig(
        inputAndUpdater = selectorAndUpdaterMacro(_.description),
        labelValue = "Description"
      )
      val root = render(container, editor.htmlElement.amend(cls("mb-4")))
      val surface = container.querySelector("[contenteditable]")

      surface.textContent shouldBe "start"
      typeInto(surface, " and more")

      inAsyncContextFuture(
        {
          formState.now().description shouldBe "start and more\n"
          formState.now().name shouldBe "untouched"
        },
        settleAtDefaultDebounce
      ).andThen { case _ =>
        root.unmount()
        dom.document.body.removeChild(container)
      }
    }

    "canonicalize a document the first time it is edited" in { _ =>
      mounted("_italic_ text") { (surface, _, emitted) =>
        typeInto(surface, "!")
        inAsyncContextFuture(emitted.toSeq shouldBe Seq("*italic* text!\n"), settle)
      }
    }
  }
}
