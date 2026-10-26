package com.togger.ui.modal

import com.raquo.laminar.api.L._

class ModalFactory(val parentElementForModal: HtmlElement) {

  private val currentModalVar: Var[Option[HtmlElement]] = Var(None)

  parentElementForModal.amend(child.maybe <-- currentModalVar.signal)

  def formModal(message: FormForModal, onClose: (() => Unit) => Unit, title: String = "Attention"): Unit =
    show(ModalForForm(title, message, onClose))

  def previewModal(content: HtmlElement, title: String = "Preview"): Unit =
    show(PreviewModal(title, content))

  def plainModal(
    message: ModalMessage,
    title: String = "Attention",
    variant: PlainModalVariant = PlainModalVariant.Info,
    confirmButtonMsg: String = "Confirm",
    cancelButtonMsg: String = "Cancel",
    confirmationText: Option[String] = None
  )(onClose: (() => Unit) => Unit): Unit =
    show(PlainModal(title, message, onClose, confirmButtonMsg, cancelButtonMsg, variant, confirmationText))

  private def show(modal: TgModal): Unit = {
    currentModalVar.set(Some(modal.element))
    modal.isClicked.changes.foreach {
      case Some(_) => currentModalVar.set(None)
      case None    => ()
    }(unsafeWindowOwner)
  }
}
