package com.togger.toast

import cats.effect.IO
import com.togger.common.TgJsonFailure
import com.togger.common.TgResponseTypes.{TgResp, TgRespIO}
import com.togger.ui.toast.Toast


trait ToastMessagesTgResp {
  def toast: Toast

  implicit class TgRespToastSyntax[DTO](resp: TgResp[DTO]) {

    def toastJustErrorsUnit(successFn: DTO => Unit): Unit =
      resp.fold(
        error => toast.error(error),
        s => successFn(s.body.get)
      )

    def toastJustErrors[A](successFn: DTO => A)(onEmpty: A): A =
      resp.fold(
        error => {
          toast.error(error)
          onEmpty
        },
        s => successFn(s.body.get)
      )

    def toastMessageCodeBlock(successMessage: String)(codeBlock: => Unit): Unit =
      resp.fold(
        error => toast.error(error),
        _ => {
          toast.success(successMessage)
          codeBlock
        }
      )

    def toastMessageFn(successMessage: String)(successFn: DTO => Unit): Unit =
      resp.fold(
        error => toast.error(error),
        s => {
          toast.success(successMessage)
          successFn(s.body.get)
        }
      )

  }
}

trait ToastMessageTgRespIO {

  def toast: Toast

  implicit class TgRespToastSyntaxIO[DTO](resp: TgRespIO[DTO]) {

    def toastMessageJustMessage(successMessage: String): IO[Unit] =
      resp.flatMap(
        _.fold(
          error => IO(toast.error(error)),
          _ => IO(toast.success(successMessage))
        )
      )

    def toastJustErrorsUnit(successFn: DTO => Unit): IO[Unit] =
      resp.flatMap(
        _.fold(
          error => IO(toast.error(error)),
          s => IO(successFn(s.body.get))
        )
      )

    def toastJustErrors[A](onEmpty: A)(successFn: DTO => A): IO[A] =
      resp.flatMap(
        _.fold(
          error => IO { toast.error(error); onEmpty },
          s => IO(successFn(s.body.get))
        )
      )

    def toastMessageFn(successMessage: String)(successFn: DTO => Unit): IO[Unit] =
      resp.flatMap(
        _.fold(
          error => IO(toast.error(error)),
          s => IO(toast.success(successMessage)) >> IO(successFn(s.body.get))
        )
      )

    def toastMessageCodeBlock(successMessage: String)(codeBlock: => Unit): IO[Unit] =
      resp.flatMap(
        _.fold(
          error => IO(toast.error(error)),
          _ => IO(toast.success(successMessage)) >> IO(codeBlock)
        )
      )

    def toastMessageDto(successMessage: DTO => String)(successFn: DTO => IO[Unit]): IO[Unit] =
      resp.flatMap(
        _.fold(
          error => IO(toast.error(error)),
          s => IO(toast.success(successMessage(s.body.get))) >> successFn(s.body.get)
        )
      )

    def toastBackendMessageCodeBlock(codeBlock: => Unit): IO[Unit] =
      resp.flatMap(
        _.fold(
          error => IO(toast.error(error)),
          s => IO(toast.success(s.message.get)) >> IO(codeBlock)
        )
      )

    def toastBackendMessage: IO[Unit] =
      resp.toastBackendMessageCodeBlock(())

    def toastHandleErrorUnit(e: PartialFunction[TgJsonFailure, Unit])(successFn: DTO => Unit): IO[Unit] =
      resp.flatMap(
        _.fold(
          error => IO(e.apply(error)),
          s => IO(successFn(s.body.get))
        )
      )

  }
}

trait ToastMessages extends ToastMessagesTgResp with ToastMessageTgRespIO
