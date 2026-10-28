package com.togger.ui.modal

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons
import org.scalajs.dom

/** A wide modal around some content to look at, closed by its button, the backdrop or Escape. */
case class PreviewModal(title: String, content: HtmlElement, maxWidthCls: String = "max-w-6xl") extends TgModal {

  private val modalClickedBus = new EventBus[ModalResult]
  val isClicked: Signal[Option[ModalResult]] = modalClickedBus.events.toWeakSignal

  private val closeObserver: Observer[Unit] = modalClickedBus.writer.contramap(_ => Cancel)

  /** A link in the content navigates away from the page the modal is shown over, so the modal goes with it. */
  private val closeOnLinkClick: Modifier[HtmlElement] =
    onClick
      .filter { ev =>
        val link = ev.target.asInstanceOf[dom.Element].closest("a[href]")
        link != null && !(ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey)
      }
      .mapToUnit --> closeObserver

  val element: HtmlDiv =
    div(
      cls(ModalStyles.wrapperCls),
      tabIndex(-1),
      ModalStyles.closeOnBackdropOrEscape(closeObserver),
      div(
        cls("relative w-full max-h-full"),
        cls(maxWidthCls),
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
          content.amend(closeOnLinkClick)
        )
      )
    )
}
