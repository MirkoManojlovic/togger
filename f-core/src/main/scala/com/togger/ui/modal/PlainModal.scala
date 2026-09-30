package com.togger.ui.modal

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.ui.organization.TgIcons
import org.scalajs.dom.{MouseEvent, html}

sealed trait ModalMessage

case class PlainTextModalMessage(text: String) extends ModalMessage

case class HtmlModalMessage(text: ReactiveHtmlElement[html.Element]) extends ModalMessage

sealed trait ModalResult

case object Confirm extends ModalResult

case object Cancel extends ModalResult

sealed trait PlainModalVariant

object PlainModalVariant {
  case object Info extends PlainModalVariant
  case object Danger extends PlainModalVariant
}

final case class PlainModal(
  title: String = "Attention",
  message: ModalMessage,
  onClose: (() => Unit) => Unit,
  confirmButtonMsg: String = "Confirm",
  cancelButtonMsg: String = "Cancel",
  variant: PlainModalVariant = PlainModalVariant.Info
) extends TgModal {

  private val modalClickedBus = new EventBus[ModalResult]
  private val modalClickedWriter = modalClickedBus.writer
  val isClicked: Signal[Option[ModalResult]] = modalClickedBus.events.toWeakSignal

  private val cancelWriterObserver: Observer[Unit] = modalClickedWriter.contramap(_ => Cancel)

  private val okayObserver = (_: MouseEvent) => onClose(() => modalClickedWriter.onNext(Confirm))
  private val cancelObserver = (_: MouseEvent) => modalClickedWriter.onNext(Cancel)

  private val iconCls = variant match {
    case PlainModalVariant.Info   => "mx-auto mb-4 w-12 h-12 text-fg-disabled"
    case PlainModalVariant.Danger => "mx-auto mb-4 w-12 h-12 text-fg-danger"
  }

  private val confirmButtonCls = variant match {
    case PlainModalVariant.Info   => ModalStyles.primaryButtonCls
    case PlainModalVariant.Danger => ModalStyles.dangerButtonCls
  }

  private val titleId = s"plain-modal-title-${scala.scalajs.js.Math.random().toString}"

  val element: ReactiveHtmlElement[html.Div] = {
    div(
      cls(ModalStyles.wrapperCls),
      tabIndex(-1),
      aria.labelledBy(titleId),
      ModalStyles.closeOnBackdropOrEscape(cancelWriterObserver),
      div(
        cls("relative w-full max-w-md max-h-full"),
        div(
          cls(ModalStyles.contentBoxCls),
          button(
            `type`("button"),
            cls(ModalStyles.popupCloseButtonCls),
            TgIcons.closeIcon("w-3 h-3"),
            span(cls("sr-only"), "Close"),
            onClick --> cancelObserver
          ),
          div(
            cls("p-4 md:p-5 text-center"),
            TgIcons.toastWarningIcon(iconCls),
            h2(idAttr(titleId), cls("sr-only"), title),
            div(
              cls("mb-5 text-lg font-normal text-heading"),
              message match {
                case PlainTextModalMessage(text)   => p(text)
                case HtmlModalMessage(htmlElement) => htmlElement
              }
            ),
            div(
              cls(ModalStyles.popupFooterCls),
              button(
                `type`("button"),
                cls(confirmButtonCls),
                confirmButtonMsg,
                onClick --> okayObserver
              ),
              button(
                `type`("button"),
                cls(ModalStyles.secondaryButtonCls),
                cancelButtonMsg,
                onClick --> cancelObserver
              )
            )
          )
        )
      )
    )
  }

}
