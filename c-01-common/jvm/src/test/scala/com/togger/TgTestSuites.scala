package com.togger

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{NonEmptyChainHelpers, TgJsonFailure, TgJsonSuccess}
import com.togger.suites.TestNameHelper
import org.scalamock.scalatest.{AsyncMockFactory, MockFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.{AnyWordSpec, AsyncWordSpec}
import org.scalatest.{Assertion, EitherValues, Suite}

class TgAsyncWordSpecJvm extends AsyncWordSpec with AsyncIOSpec with Matchers with TestLoggingJvm with TestNameHelper

class TgAsyncWordSpecMockJvm
    extends AsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with TestLoggingJvm
    with AsyncMockFactory
    with TgRespAssertions
    with TestNameHelper

class TgWordSpecJvm extends AnyWordSpec with Matchers with TestLoggingJvm with TestNameHelper

class TgWordSpecMockJvm extends AnyWordSpec with Matchers with TestLoggingJvm with MockFactory with TestNameHelper

trait TgRespAssertions { this: Suite =>
  implicit class TgRespTestIO[T](TgRespIO: TgRespIO[T]) {
    def shouldBeError(errorAssertion: TgJsonFailure => Assertion): IO[Assertion] =
      TgRespIO.map(_.fold(errorAssertion, failSuccess))

    private def failSuccess(success: TgJsonSuccess[T]): Assertion =
      fail(s"TgResp should be a failure, but it was $success")

    def shouldBeSuccess(successAssertion: TgJsonSuccess[T] => Assertion): IO[Assertion] =
      TgRespIO.map(_.fold(failError, successAssertion))

    private def failError(failure: TgJsonFailure): Assertion =
      fail(s"TgResp should be a success, but it was $failure")

  }
}

trait TgValidationSpec extends TgWordSpecJvm with EitherValues with NonEmptyChainHelpers with TestNameHelper
