package com.togger.ui.modal

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons

/** A wide modal around some content to look at, closed by its button, the backdrop or Escape. */
case class PreviewModal(title: String, content: HtmlElement) extends TgModal {

  private val modalClickedBus = new EventBus[ModalResult]
  val isClicked: Signal[Option[ModalResult]] = modalClickedBus.events.toWeakSignal

  private val closeObserver: Observer[Unit] = modalClickedBus.writer.contramap(_ => Cancel)

  val element: HtmlDiv =
    div(
      cls(ModalStyles.wrapperCls),
      tabIndex(-1),
      ModalStyles.closeOnBackdropOrEscape(closeObserver),
      div(
        cls("relative w-full max-w-6xl max-h-full"),
        div(
          cls(ModalStyles.contentBoxCls),
          cls("max-h-[90vh] overflow-y-auto"),
          div(
            cls(ModalStyles.headerCls),
            h3(cls(ModalStyles.titleCls), title),
            button(
              `type`("button"),
              cls(ModalStyles.closeButtonCls),
              TgIcons.closeIcon("w-3 h-3"),
              span(cls("sr-only"), "Close"),
              onClick.mapToUnit --> closeObserver
            )
          ),
          content
        )
      )
    )
}
