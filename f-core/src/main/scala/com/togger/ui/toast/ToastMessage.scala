package com.togger.ui.toast

import java.util.UUID

sealed trait ToastMessageType
case object ToastSuccess extends ToastMessageType
case object ToastError extends ToastMessageType

case class ToastMessage(
  id: UUID = UUID.randomUUID(),
  message: String,
  toastType: ToastMessageType
) {
  def toMap: (UUID, ToastMessage) = (id, this)
}

object ToastMessage {

  def success(message: String): ToastMessage =
    ToastMessage(message = message, toastType = ToastSuccess)

  def failure(message: String): ToastMessage =
    ToastMessage(message = message, toastType = ToastError)

}
