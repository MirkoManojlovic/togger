package com.togger.ui.toast

import com.togger.common._
import com.togger.logging.TgLogging
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.util.SplunkMacro.sp
import org.scalajs.dom.html

import java.util.UUID

class Toast(
  toastMessagesService: ToastMessagesService,
  elementForToast: HtmlElement
) extends TgLogging {

  private val toastElementClasses: Map[ToastMessageType, String] =
    Map(
      ToastSuccess -> "toast toast-success",
      ToastError -> "toast toast-error"
    )

  elementForToast.amend(
    children <-- toastMessagesService.$toastMessages.map(formatToastMessages)
  )

//
//  <div id="toast-success" class= role="alert">
//    <div class=>
//      <svg class= aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24"><path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d=/></svg>
//      <span class="sr-only">Check icon</span>
//    </div>
//    <div class=>Item moved successfully.</div>
//    <button type="button" class="ms-auto flex items-center justify-center text-body hover:text-heading bg-transparent box-border border border-transparent hover:bg-neutral-secondary-medium focus:ring-4 focus:ring-neutral-tertiary font-medium leading-5 rounded text-sm h-8 w-8 focus:outline-none" data-dismiss-target="#toast-success" aria-label="Close">
//      <span class="sr-only">Close</span>
//      <svg class="w-5 h-5" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24"><path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18 17.94 6M18 18 6.06 6"/></svg>
//    </button>
//  </div>
//

  private def formatToastMessages(uuidToToastMessage: Map[UUID, ToastMessage]): Seq[ReactiveHtmlElement[html.Div]] =
    uuidToToastMessage.values
      .map(toastMessage =>
//        div(
//          cls(
//            toastElementClasses(toastMessage.toastType),
//            "mb-4"
////            ToastElement.toastElementStyle.htmlClass
//          ),
//          toastMessage.message
//        )

        // new

        div(
          idAttr("toast"),
          position.fixed,
          cls(
            "flex items-center w-full max-w-sm p-4 text-body bg-neutral-primary-soft rounded-base shadow-xs border border-default"
          ),
          div(
            cls("inline-flex items-center justify-center shrink-0 w-7 h-7 text-fg-success bg-success-soft rounded"),
            svg.svg(
              svg.className("w-5 h-5"),
              svg.width("24"),
              svg.fill("none"),
              svg.viewBox("0 0 24 24"),
              svg.path(
                svg.stroke("currentColor"),
                svg.strokeLineCap("round"),
                svg.strokeLineJoin("round"),
                svg.strokeWidth("2"),
                svg.d("M5 11.917 9.724 16.5 19 7.5")
              )
            ),
            span(cls("sr-only"), "Check icon")
          ),
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

//object ToastElement extends StyleSheet.Inline {
//
//  val toastElementStyle: StyleA = style(
//    marginBottom(0.25.rem)
//  )
//
//}
