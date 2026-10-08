package com.togger.controllers.common

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.togger.common.{BadRequest, DbPageRequest, PageRequest, TgJsonSuccess}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PageRequestValidationTest extends AnyWordSpec with Matchers {

  "PageRequestValidation.withPage" should {

    "run the function with the validated request" in {
      val result = PageRequestValidation
        .withPage(PageRequest(2, 50))(page => TgJsonSuccess.justBodyRightIO((page.page.pageValue, page.limit)))
        .unsafeRunSync()
      result.toOption.flatMap(_.body) shouldBe Some((2, 50))
    }

    "answer 400 with both errors for page 0 and size 101, without running the function" in {
      var ran = false
      val result = PageRequestValidation
        .withPage(PageRequest(0, 101)) { (_: DbPageRequest) =>
          ran = true
          IO.pure(TgJsonSuccess.justBodyRight("never"))
        }
        .unsafeRunSync()
      ran shouldBe false
      result match {
        case Left(BadRequest(errors)) =>
          errors.map(_.`type`) shouldBe Set("DbPageError", "DbSizeError")
        case other => fail(s"Expected a bad request but got $other")
      }
    }

    "answer 400 for size 0 alone" in {
      val result = PageRequestValidation
        .withPage(PageRequest(1, 0))(_ => TgJsonSuccess.justBodyRightIO("never"))
        .unsafeRunSync()
      result.left.toOption.collect { case BadRequest(errors) => errors.size } shouldBe Some(1)
    }
  }
}
