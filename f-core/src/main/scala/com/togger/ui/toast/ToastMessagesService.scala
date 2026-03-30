package com.togger.ui.toast

import com.togger.common.TgUnitSyntax.TgUnitOps
import com.togger.logging.TgLogging
import com.raquo.airstream.ownership.Owner
import com.raquo.laminar.api.L._

import java.util.UUID

class ToastMessagesService(delayInMillis: Int = 1000, owner: Owner) extends TgLogging {

  private val toastMessagesInternalState: Var[Map[UUID, ToastMessage]] = Var(Map.empty[UUID, ToastMessage])

  val $toastMessages: Signal[Map[UUID, ToastMessage]] =
    toastMessagesInternalState.signal

  // TODO: check why can't rename $newMessages
  private val (newMessages, emitNewToastMessage) = EventStream.withCallback[ToastMessage]

  // Adding new messages
  newMessages
    .map { newToastMessage =>
      val currentMessages = toastMessagesInternalState.now()
      val newMessages = currentMessages + newToastMessage.toMap
      logger.info(s"Adding toast message: $newToastMessage")
      toastMessagesInternalState.set(newMessages)
    }
    .foreach(_ => ())(owner)
    .toUnit()

  // Deleting messages
  newMessages
    .delay(delayInMillis)
    .map { newToastMessage =>
      val currentMessages = toastMessagesInternalState.now()
      val newMessages = currentMessages - newToastMessage.id
      logger.info(s"Deleting toast message: $newToastMessage")
      toastMessagesInternalState.set(newMessages)
    }
    .foreach(_ => ())(owner)
    .toUnit()

  def createAToastMessage(newMessage: ToastMessage): Unit =
    emitNewToastMessage(newMessage)

}
