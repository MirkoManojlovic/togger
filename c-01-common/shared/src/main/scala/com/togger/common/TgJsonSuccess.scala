package com.togger.common

import cats.data.NonEmptyChain
import cats.effect.IO
import com.togger.common.TgResponseTypes.{TgResp, TgRespEmptyBody, TgRespEmptyBodyIO, TgRespIO}

case class TgJsonSuccess[+A](message: Option[String] = None, body: Option[A] = None)
case class TgJsonError(message: String, `type`: String)

object TgJsonSuccess {

  import io.circe.{Decoder, Encoder}

  type TgEmptyBody = String
  import io.circe.generic.semiauto._

  implicit def tgJsonSuccessEncoder[T: Encoder]: Encoder[TgJsonSuccess[T]] = {
    // For some add reason if I don't add type here it equates to Encoder[Nothing]
    val encoder: Encoder[TgJsonSuccess[T]] = deriveEncoder
    encoder.mapJson(_.dropNullValues)
  }

  implicit def tgJsonSuccessDecoder[T: Decoder]: Decoder[TgJsonSuccess[T]] = deriveDecoder

  def justBody[T](body: T): TgJsonSuccess[T] =
    TgJsonSuccess[T](body = Option(body))

  def justBodyRight[T](body: T): TgResp[T] =
    Right(TgJsonSuccess[T](body = Option(body)))

  def justBodyRightIO[T](body: T): TgRespIO[T] =
    IO(Right(TgJsonSuccess[T](body = Option(body))))

  def justMessage(message: String): TgJsonSuccess[TgEmptyBody] =
    TgJsonSuccess[TgEmptyBody](message = Some(message))

  def justMessageRight(message: String): TgRespEmptyBody =
    Right(justMessage(message))

  def justMessageRightIO[T](message: String): TgRespEmptyBodyIO =
    IO(justMessageRight(message))

  def empty[T]: TgJsonSuccess[T] =
    TgJsonSuccess(body = None)

  def emptyRight[T]: TgResp[T] =
    Right(empty)

  def emptyRightIO[T]: TgRespIO[T] =
    IO(emptyRight)
}

sealed trait TgJsonFailure
case object NotFound extends TgJsonFailure
case class BadRequest(errors: Set[TgJsonError]) extends TgJsonFailure
case class ServerError(msg: Option[String]) extends TgJsonFailure
case object Unauthenticated extends TgJsonFailure
case object Forbidden extends TgJsonFailure

object TgJsonFailure {

  def notFound(msg: Option[String] = None): TgJsonFailure =
    NotFound

  def notFoundLeft[T](msg: Option[String] = None): TgResp[T] =
    Left(notFound(msg))

  def notFoundLeftIO[T](msg: Option[String] = None): TgRespIO[T] =
    IO.pure(notFoundLeft[T](msg))

  def badRequest(errors: TgJsonError*): TgJsonFailure =
    BadRequest(Set(errors: _*))

  def badRequest(errors: Set[TgJsonError]): TgJsonFailure =
    BadRequest(errors)

  def badRequestString(errors: String*): TgJsonFailure =
    BadRequest(errors.map(msg => TgJsonError(msg, "String")).toSet)

  def badRequestLeft[T](errors: Set[TgJsonError]): TgResp[T] =
    Left(badRequest(errors))

  def badRequestStringLeft[A](errors: String*): TgResp[A] =
    Left(badRequestString(errors: _*))

  def badRequestLeftIO[T](errors: Set[TgJsonError]): TgRespIO[T] =
    IO(badRequestLeft[T](errors))

  def internalError(msg: Option[String]): TgJsonFailure =
    ServerError(msg)

  def internalErrorLeft[A](msg: Option[String]): TgResp[A] =
    Left(internalError(msg))

  def internalErrorLeftIO[A](msg: Option[String]): TgRespIO[A] =
    IO(Left(internalError(msg)))

  def unauthenticated[A](msg: Option[String]): TgJsonFailure =
    Unauthenticated

  def unauthenticatedLeft[A](msg: Option[String]): TgResp[A] =
    Left(unauthenticated(msg))

  def unauthenticatedLeftIO[A](msg: Option[String]): TgRespIO[A] =
    IO(unauthenticatedLeft(msg))

}

object TgJsonError {
  def fromError(error: TgError): Set[TgJsonError] =
    Set(TgJsonError(message = error.message, `type` = error.`type`))

  def fromErrorsSet(errors: Set[TgError]): Set[TgJsonError] =
    errors.flatMap(fromError)

  def fromErrorsNec(errors: NonEmptyChain[TgError]): Set[TgJsonError] =
    fromErrorsSet(errors.toNonEmptyList.toList.toSet)

  def fromString(message: String): TgJsonError =
    TgJsonError(message = message, `type` = "String")

}
