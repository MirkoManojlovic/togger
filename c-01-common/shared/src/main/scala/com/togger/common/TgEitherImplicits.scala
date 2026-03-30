package com.togger.common

import cats.data.EitherT
import cats.effect.IO

object TgEitherImplicits {
  implicit class TgEitherSyntax[A, B](e: IO[Either[A, B]]) {
    def toEt: EitherT[IO, A, B] = EitherT(e)
  }
}
