package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgInputType
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

/** The popup that creates a link or changes one: the link name above the address and a small button that is labelled
  * for what it does. It is placed by the caller, just below the badge it belongs to.
  *
  * The form it sits in must not see Enter, which would submit the whole form; Enter submits the popup instead.
  */
class TaskLinkEditor(
  initial: TaskLinkFormData,
  submitLabel: String,
  onSubmit: TaskLinkFormData => Unit,
  onCancel: () => Unit
) {

  private val formState = Var(initial)

  private val isValid: Signal[Boolean] = formState.signal.map(form => TaskLinkBadges.safeHref(form.url).isDefined)

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Link name",
    tgInputType = TgInputType.Text,
    isRequired = false
  )

  private val urlInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.url),
    errors = formState.signal.changes.map { form =>
      if (form.url.trim.nonEmpty && TaskLinkBadges.safeHref(form.url).isEmpty) Seq("Use a web address")
      else Seq.empty
    },
    labelValue = "Url",
    tgInputType = TgInputType.Text,
    isRequired = false
  )

  private def submit(): Unit = if (TaskLinkBadges.safeHref(formState.now().url).isDefined) onSubmit(formState.now())

  val element: HtmlElement =
    div(
      cls("absolute left-0 top-full z-20 mt-1 w-80 max-w-[90vw] rounded-base border border-default-medium"),
      cls("bg-neutral-primary-soft p-3 shadow-sm"),
      onKeyDown.filter(_.key == "Enter").preventDefault.mapToUnit --> (_ => submit()),
      onMountBind { _ =>
        documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> (_ => onCancel())
      },
      onMountBind { context =>
        documentEvents(_.onMouseDown)
          .filter(event => !context.thisNode.ref.contains(event.target.asInstanceOf[dom.Node]))
          .mapToUnit --> (_ => onCancel())
      },
      nameInput.htmlElement.amend(cls("mb-3")),
      urlInput.htmlElement.amend(cls("mb-3")),
      div(
        cls("flex justify-end"),
        button(
          typ("button"),
          cls(TgHtmlButton.tailwindSmallButton),
          cls("disabled:opacity-50 disabled:cursor-not-allowed"),
          disabled <-- isValid.map(!_),
          submitLabel,
          onClick.mapToUnit --> (_ => submit())
        )
      )
    )
}
