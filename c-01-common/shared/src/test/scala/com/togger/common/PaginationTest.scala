package com.togger.common

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

class PaginationTest extends AnyWordSpec with Matchers with EitherValues with TableDrivenPropertyChecks {

  "DbPage.fromInt" should {
    "accept pages >= 1" in {
      forAll(Table("page", 1, 2, 10)) { page =>
        DbPage.fromInt(page).value.pageValue shouldBe page
      }
    }
    "reject pages < 1" in {
      forAll(Table("page", 0, -1, -100)) { page =>
        DbPage.fromInt(page).isLeft shouldBe true
      }
    }
  }

  "DbSize.fromInt" should {
    "accept sizes between 1 and the max" in {
      forAll(Table("size", 1, 20, PageRequest.MaxSize)) { size =>
        DbSize.fromInt(size).value.limitValue shouldBe size
      }
    }
    "reject sizes outside the range" in {
      forAll(Table("size", 0, -5, PageRequest.MaxSize + 1, 1000)) { size =>
        DbSize.fromInt(size).isLeft shouldBe true
      }
    }
  }

  "DbPageRequest" should {
    "compute limit and row offset" in {
      val request = DbPageRequest.fromPageRequest(PageRequest(3, 20)).value
      request.limit shouldBe 20
      request.rowOffset shouldBe 40L
    }
    "offer the first page of the maximum size" in {
      DbPageRequest.firstMaxSized.page.pageValue shouldBe 1
      DbPageRequest.firstMaxSized.limit shouldBe PageRequest.MaxSize
    }
    "accumulate page and size errors" in {
      DbPageRequest.fromPageRequest(PageRequest(0, 101)).left.value.length shouldBe 2L
    }
  }

  "PaginatedResponse" should {
    "compute total pages" in {
      forAll(Table(("total", "size", "pages"), (0L, 5, 0L), (5L, 5, 1L), (6L, 5, 2L), (11L, 5, 3L))) {
        (total, size, pages) =>
          PaginatedResponse.of(1, size, List.empty[Int], total).totalNumberOfPages shouldBe pages
      }
    }
    "map items" in {
      PaginatedResponse.of(1, 5, List(1, 2), 2L).mapItem(_.toString).items shouldBe List("1", "2")
    }
  }
}
