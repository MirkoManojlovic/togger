package com.togger.common

import cats.data.NonEmptyChain
import com.togger.common.TgResponseTypes.{TgResp, TgRespIO}

trait TgError extends TgErrorType {
  def message: String
}

trait TgErrorType {
  // Type used in JSON serialization/deserialization
  def `type`: String = this.getClass.getSimpleName.replace("$", "")
}

object TgError {

  implicit class TgErrorSyntax(error: TgError) {
    def toBadRequest: TgJsonFailure = TgJsonFailure.badRequest(TgJsonError.fromError(error))

    def toBadRequestLeft[T]: TgResp[T] = TgJsonFailure.badRequestLeft(TgJsonError.fromError(error))

    def toBadRequestLeftIO[T]: TgRespIO[T] = TgJsonFailure.badRequestLeftIO(TgJsonError.fromError(error))
  }

  implicit class TgErrorNecSyntax(errors: NonEmptyChain[TgError]) {
    def toBadRequest: TgJsonFailure = TgJsonFailure.badRequest(TgJsonError.fromErrorsNec(errors))

    def toBadRequestLeft[T]: TgResp[T] = TgJsonFailure.badRequestLeft(TgJsonError.fromErrorsNec(errors))

    def toBadRequestLeftIO[T]: TgRespIO[T] = TgJsonFailure.badRequestLeftIO(TgJsonError.fromErrorsNec(errors))
  }

}
