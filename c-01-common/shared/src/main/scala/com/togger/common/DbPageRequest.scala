package com.togger.common

import cats.data.{EitherNec, ValidatedNec}
import cats.implicits._

abstract sealed case class DbPage private (pageValue: Int)

object DbPage {
  case class DbPageError(message: String) extends TgError

  def fromInt(page: Int): Either[DbPageError, DbPage] =
    if (page < 1) Left(DbPageError(s"Page must be 1 or greater but was $page"))
    else Right(new DbPage(page) {})
}

abstract sealed case class DbSize private (limitValue: Int)

object DbSize {
  case class DbSizeError(message: String) extends TgError

  def fromInt(size: Int): Either[DbSizeError, DbSize] =
    if (size < 1 || size > PageRequest.MaxSize)
      Left(DbSizeError(s"Size must be between 1 and ${PageRequest.MaxSize} but was $size"))
    else Right(new DbSize(size) {})
}

case class DbPageRequest(page: DbPage, size: DbSize) {
  def limit: Int = size.limitValue
  def rowOffset: Long = (page.pageValue.toLong - 1L) * size.limitValue
}

object DbPageRequest {

  def validated(request: PageRequest): ValidatedNec[TgError, DbPageRequest] =
    (
      DbPage.fromInt(request.page).leftMap[TgError](identity).toValidatedNec,
      DbSize.fromInt(request.size).leftMap[TgError](identity).toValidatedNec
    ).mapN(DbPageRequest(_, _))

  def fromPageRequest(request: PageRequest): EitherNec[TgError, DbPageRequest] =
    validated(request).toEither

  /** Page 1 with the default size; for callers/tests that need a request without validation. */
  val default: DbPageRequest =
    fromPageRequest(PageRequest.default).fold(errors => throw new IllegalStateException(errors.toString), identity)
}
