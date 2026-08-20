package com.togger.ui.toast

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.common._
import com.togger.logging.TgLogging
import com.togger.ui.organization.TgIcons
import com.togger.util.SplunkMacro.sp
import org.scalajs.dom.{HTMLDivElement, html}

import java.util.UUID

class Toast(
  toastMessagesService: ToastMessagesService,
  elementForToast: HtmlElement
) extends TgLogging {

  private val toastIcons: Map[ToastMessageType, ReactiveHtmlElement[HTMLDivElement]] =
    Map(
      ToastSuccess ->
        div(
          cls("inline-flex items-center justify-center shrink-0 w-7 h-7 text-fg-success bg-success-soft rounded"),
          TgIcons.toastSuccessIcon("w-5 h-5"),
          span(cls("sr-only"), "Check icon")
        ),
      ToastError ->
        div(
          cls("inline-flex items-center justify-center shrink-0 w-7 h-7 text-fg-danger bg-danger-soft rounded"),
          TgIcons.toastErrorIcon("w-5 h-5"),
          span(cls("sr-only"), "Check icon")
        )
    )

  elementForToast.amend(
    children <-- toastMessagesService.$toastMessages.map(formatToastMessages)
  )

  private def formatToastMessages(uuidToToastMessage: Map[UUID, ToastMessage]): Seq[ReactiveHtmlElement[html.Div]] =
    uuidToToastMessage.values
      .map(toastMessage =>
        div(
          idAttr("toast"),
          position.fixed,
          cls(
            "flex items-center w-full max-w-sm p-4 text-body bg-neutral-primary-soft rounded-base shadow-xs border border-default"
          ),
          toastIcons(toastMessage.toastType),
          div(
            cls(
              "ms-3 text-sm font-normal"
            ),
            toastMessage.message
          )
        )
      )
      .toSeq

  def success(message: String): Unit =
    toastMessagesService.createAToastMessage(ToastMessage.success(message))

  def success[T](tgJsonSuccess: TgJsonSuccess[T]): Unit =
    toastMessagesService.createAToastMessage(
      ToastMessage.success(tgJsonSuccess.message.getOrElse("Operation performed successfully"))
    )

  def error(message: String): Unit =
    toastMessagesService.createAToastMessage(ToastMessage.failure(message))

  def error(failure: TgJsonFailure): Unit =
    failure match {
      case NotFound =>
        toastMessagesService.createAToastMessage(ToastMessage.failure("Oops, not found"))
      case BadRequest(errors) =>
        errors.foreach(error => toastMessagesService.createAToastMessage(ToastMessage.failure(error.message)))
      case ServerError(error) =>
        toastMessagesService.createAToastMessage(ToastMessage.failure(error.getOrElse("Oops, a server error occurred")))
      case Unauthenticated =>
        toastMessagesService.createAToastMessage(ToastMessage.failure("Session expired, please log in again"))
      case Forbidden =>
        toastMessagesService.createAToastMessage(ToastMessage.failure("Oops, you are not allowed to do that"))
      case error @ _ =>
        logger.error(s"Error ${sp(error)}")
        toastMessagesService.createAToastMessage(ToastMessage.failure("Unknown message occurred"))
    }
}
