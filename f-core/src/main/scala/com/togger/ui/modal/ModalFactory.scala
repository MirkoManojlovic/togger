package com.togger.ui.modal

import com.raquo.laminar.api.L.{HtmlElement, _}

class ModalFactory(val parentElementForModal: HtmlElement) {

  def formModal(message: FormForModal, onClose: (() => Unit) => Unit, title: String = "Attention"): Unit = {
    val modal = ModalForForm(title, message, onClose)
    val renderModalBasedOnClicked =
      modal.isClicked.map {
        case None => Some(modal.element)
        case _    => None
      }
    parentElementForModal.amend(child.maybe <-- renderModalBasedOnClicked)
  }

  def plainModal(message: ModalMessage, title: String = "Attention")(onClose: (() => Unit) => Unit): Unit = {
    val modal = PlainModal(title, message, onClose)
    val renderModalBasedOnClicked =
      modal.isClicked.map {
        case None => Some(modal.element)
        case _    => None
      }
    parentElementForModal.amend(child.maybe <-- renderModalBasedOnClicked)
  }
}
