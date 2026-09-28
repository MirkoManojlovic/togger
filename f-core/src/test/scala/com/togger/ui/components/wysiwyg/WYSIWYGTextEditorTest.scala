package com.togger.ui.components.wysiwyg

import com.raquo.laminar.api.L._
import com.togger.common.suites.AsyncJsWordSpec
import org.scalajs.dom
import org.scalatest.Assertion

import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.js

class WYSIWYGTextEditorTest extends AsyncJsWordSpec {

  private val debounce = 10
  private val settle = debounce * 6

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

    val editor = WYSIWYGTextEditor(
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

  testNameFromClassName should {

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

    "canonicalize a document the first time it is edited" in { _ =>
      mounted("_italic_ text") { (surface, _, emitted) =>
        typeInto(surface, "!")
        inAsyncContextFuture(emitted.toSeq shouldBe Seq("*italic* text!\n"), settle)
      }
    }
  }
}
