package com.togger.ui.modal

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.{MouseEvent, html}

sealed trait ModalMessage

case class PlainTextModalMessage(text: String) extends ModalMessage

case class HtmlModalMessage(text: ReactiveHtmlElement[html.Element]) extends ModalMessage

sealed trait ModalResult

case object Confirm extends ModalResult

case object Cancel extends ModalResult

final case class PlainModal(
  title: String = "Attention",
  message: ModalMessage,
  onClose: (() => Unit) => Unit,
  confirmButtonMsg: String = "Confirm",
  cancelButtonMsg: String = "Cancel"
) {

  private val modalClickedBus = new EventBus[ModalResult]
  private val modalClickedWriter = modalClickedBus.writer
  val isClicked: Signal[Option[ModalResult]] = modalClickedBus.events.toWeakSignal

  private val okayObserver = (_: MouseEvent) => onClose(() => modalClickedWriter.onNext(Confirm))

  private val cancelObserver = (_: MouseEvent) => modalClickedWriter.onNext(Cancel)

  val element: ReactiveHtmlElement[html.Div] = {
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
          div(
            cls("content"),
            message match {
              case PlainTextModalMessage(text)   => p(text)
              case HtmlModalMessage(htmlElement) => htmlElement
            }
          )
        ),
        div(
          cls("modal-footer"),
          button(
            cls("btn btn-primary"),
            confirmButtonMsg,
            onClick --> okayObserver
          ),
          a(
            cls("btn btn-link"),
            cancelButtonMsg,
            onClick --> cancelObserver
          )
        )
      )
    )
  }

}
