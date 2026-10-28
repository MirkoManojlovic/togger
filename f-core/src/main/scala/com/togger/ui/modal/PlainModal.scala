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
  variant: PlainModalVariant = PlainModalVariant.Info,
  confirmationText: Option[String] = None,
  showCancel: Boolean = true
) extends TgModal {

  // With a confirmation text, the confirm button stays disabled until the user has typed exactly that text.
  private val typedTextVar = Var("")
  private val confirmDisabledSignal: Signal[Boolean] =
    typedTextVar.signal.map(typed => confirmationText.exists(_ != typed.trim))

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
            confirmationText.map(text =>
              div(
                cls("mb-5 text-left"),
                label(
                  cls("block mb-2 text-sm font-medium text-heading"),
                  "Type ",
                  b(text),
                  " to confirm"
                ),
                input(
                  `type`("text"),
                  cls(
                    "bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base " +
                      "focus:ring-brand focus:border-brand block w-full px-3 py-2.5"
                  ),
                  aria.label(s"Type $text to confirm"),
                  controlled(value <-- typedTextVar.signal, onInput.mapToValue --> typedTextVar.writer)
                )
              )
            ),
            div(
              cls(ModalStyles.popupFooterCls),
              button(
                `type`("button"),
                cls(confirmButtonCls),
                cls("disabled:opacity-50 disabled:cursor-not-allowed"),
                disabled <-- confirmDisabledSignal,
                confirmButtonMsg,
                onClick --> okayObserver
              ),
              Option.when(showCancel)(
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
    )
  }

}
