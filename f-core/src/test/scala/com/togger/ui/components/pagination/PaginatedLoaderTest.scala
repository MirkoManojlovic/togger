package com.togger.ui.components.pagination

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.{BadRequest, PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.common.TgResponseTypes.TgRespIO

class PaginatedLoaderTest extends AsyncJsWordSpec {

  private val all = (1 to 250).toList

  private def fetchFrom(items: List[Int], requested: scala.collection.mutable.Buffer[PageRequest]) =
    (request: PageRequest) => {
      requested += request
      val slice = items.slice((request.page - 1) * request.size, request.page * request.size)
      TgJsonSuccess.justBodyRightIO(PaginatedResponse.of(request.page, request.size, slice, items.size.toLong)): TgRespIO[PaginatedResponse[Int]]
    }

  testNameFromClassName should {

    "read every page of 100 until the last" in { _ =>
      val requested = scala.collection.mutable.Buffer.empty[PageRequest]
      PaginatedLoader.loadAll(fetchFrom(all, requested)).unsafeToFuture().map { result =>
        result.toOption.flatMap(_.body) shouldBe Some(all)
        requested.toList shouldBe List(PageRequest(1, 100), PageRequest(2, 100), PageRequest(3, 100))
      }
    }

    "read a single page for an empty list" in { _ =>
      val requested = scala.collection.mutable.Buffer.empty[PageRequest]
      PaginatedLoader.loadAll(fetchFrom(List.empty, requested)).unsafeToFuture().map { result =>
        result.toOption.flatMap(_.body) shouldBe Some(Seq.empty)
        requested.size shouldBe 1
      }
    }

    "stop at the page bound" in { _ =>
      val huge = (1 to (PaginatedLoader.MaxPages + 5) * 100).toList
      val requested = scala.collection.mutable.Buffer.empty[PageRequest]
      PaginatedLoader.loadAll(fetchFrom(huge, requested)).unsafeToFuture().map { result =>
        requested.size shouldBe PaginatedLoader.MaxPages
        result.toOption.flatMap(_.body).map(_.size) shouldBe Some(PaginatedLoader.MaxPages * 100)
      }
    }

    "return the failure of a page" in { _ =>
      val failing = (_: PageRequest) => IO.pure(Left(BadRequest(Set.empty))): TgRespIO[PaginatedResponse[Int]]
      PaginatedLoader.loadAll(failing).unsafeToFuture().map(_ shouldBe Left(BadRequest(Set.empty)))
    }
  }
}
