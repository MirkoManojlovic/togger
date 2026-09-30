package com.togger.ui.modal

import com.raquo.laminar.api.L._

trait TgModal {
  def element: HtmlElement
  def isClicked: Signal[Option[ModalResult]]
}
