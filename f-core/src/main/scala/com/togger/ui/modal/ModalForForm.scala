package com.togger.ui.modal

import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import org.scalajs.dom.MouseEvent

case class FormForModal(element: HtmlDiv, hasErrorsEvents: EventStream[Boolean])

case class ModalForForm(title: String = "Attention", message: FormForModal, onClose: (() => Unit) => Unit) {

  private val confirmButton: String = "Confirm"
  private val cancelButton: String = "Cancel"

  private val modalClickedBus = new EventBus[ModalResult]
  private val modalClickedWriter = modalClickedBus.writer
  val isClicked: Signal[Option[ModalResult]] = modalClickedBus.events.toWeakSignal

  private val cancelObserver = (_: MouseEvent) => {
    modalClickedWriter.onNext(Cancel)
  }

  val element: HtmlDiv =
    div(
      cls("modal active"),
      a(cls("modal-overlay")),
      div(
        cls("modal-container"),
        div(
          cls("modal-header"),
          //          a(cls("btn btn-clear float-right"), href := "#"),
          div(cls("modal-title h5"), title)
        ),
        div(
          cls("modal-body"),
          div(cls("content"), message.element)
        ),
        div(
          cls("modal-footer"),
          TgHtmlButton
            .withError(
              disabledEvents = message.hasErrorsEvents,
              debounceTimeInMillis = 0
            )
            .amend(
              cls("btn btn-primary"),
              confirmButton,
              onClick --> { _ =>
                onClose(() => modalClickedWriter.onNext(Confirm))
              }
            ),
          a(
            cls("btn btn-link"),
            cancelButton,
            onClick --> cancelObserver
          )
        )
      )
    )

}
