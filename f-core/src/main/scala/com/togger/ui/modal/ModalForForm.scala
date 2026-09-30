package com.togger.ui.modal

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.components.button.TgHtmlButton
import com.togger.ui.organization.TgIcons
import org.scalajs.dom.MouseEvent

case class FormForModal(element: HtmlDiv, hasErrorsEvents: EventStream[Boolean])

case class ModalForForm(title: String = "Attention", message: FormForModal, onClose: (() => Unit) => Unit)
    extends TgModal {

  private val confirmButton: String = "Confirm"
  private val cancelButton: String = "Cancel"

  private val modalClickedBus = new EventBus[ModalResult]
  private val modalClickedWriter = modalClickedBus.writer
  val isClicked: Signal[Option[ModalResult]] = modalClickedBus.events.toWeakSignal

  private val cancelWriterObserver: Observer[Unit] = modalClickedWriter.contramap(_ => Cancel)

  private val cancelObserver = (_: MouseEvent) => {
    modalClickedWriter.onNext(Cancel)
  }

  val element: HtmlDiv =
    div(
      cls(ModalStyles.wrapperCls),
      tabIndex(-1),
      ModalStyles.closeOnBackdropOrEscape(cancelWriterObserver),
      div(
        cls("relative w-full max-w-md max-h-full"),
        div(
          cls(ModalStyles.contentBoxCls),
          div(
            cls(ModalStyles.headerCls),
            h3(cls(ModalStyles.titleCls), title),
            button(
              `type`("button"),
              cls(ModalStyles.closeButtonCls),
              TgIcons.closeIcon("w-3 h-3"),
              span(cls("sr-only"), "Close"),
              onClick --> cancelObserver
            )
          ),
          message.element,
          div(
            cls(ModalStyles.footerCls),
            TgHtmlButton
              .withError(
                disabledEvents = message.hasErrorsEvents,
                debounceTimeInMillis = 0
              )
              .amend(
                cls(ModalStyles.primaryButtonCls),
                confirmButton,
                onClick --> { _ =>
                  onClose(() => modalClickedWriter.onNext(Confirm))
                }
              ),
            button(
              `type`("button"),
              cls(ModalStyles.secondaryButtonCls),
              cancelButton,
              onClick --> cancelObserver
            )
          )
        )
      )
    )

}
