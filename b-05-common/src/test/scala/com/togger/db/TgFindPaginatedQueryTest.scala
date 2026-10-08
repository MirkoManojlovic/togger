package com.togger.db

import cats.effect.unsafe.implicits.global
import com.togger.common.{DbPageRequest, PageRequest, PaginatedResponse}
import doobie.free.connection.ConnectionIO
import cats.implicits._
import doobie.implicits._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TgFindPaginatedQueryTest extends AnyWordSpec with Matchers {

  private val transactor = new MockedTransactor {}.transactorMock
  private val all = (1 to 11).toList

  private def query(onPageQuery: => Unit = ()): TgFindPaginatedQuery[Int] =
    TgFindPaginatedQuery(
      pageQuery = request =>
        { onPageQuery; all.slice(request.rowOffset.toInt, request.rowOffset.toInt + request.limit) }.pure[ConnectionIO],
      countQuery = all.size.toLong.pure[ConnectionIO]
    )

  private def run(page: Int, size: Int, q: TgFindPaginatedQuery[Int] = query()) = {
    val request = DbPageRequest.fromPageRequest(PageRequest(page, size)).toOption.get
    q.run(request).transact(transactor).unsafeRunSync()
  }

  "TgFindPaginatedQuery" should {
    "return the first page with totals" in {
      val r = run(1, 5)
      r.items shouldBe List(1, 2, 3, 4, 5)
      r.totalNumberOfItems shouldBe 11L
      r.totalNumberOfPages shouldBe 3L
    }
    "return a partial last page" in {
      run(3, 5).items shouldBe List(11)
    }
    "return an empty page past the end without running the page query" in {
      var called = false
      val r = run(4, 5, query { called = true })
      r.items shouldBe empty
      r.totalNumberOfItems shouldBe 11L
      called shouldBe false
    }
  }

  "TgPages.readAll" should {
    "read every page of a list longer than one page" in {
      val items = (1 to 250).toList
      val read = (request: DbPageRequest) =>
        PaginatedResponse
          .of(
            request.page.pageValue,
            request.limit,
            items.slice(request.rowOffset.toInt, request.rowOffset.toInt + request.limit),
            items.size.toLong
          )
          .pure[ConnectionIO]
      TgPages.readAll(read).transact(transactor).unsafeRunSync() shouldBe items
    }
    "return an empty list for an empty result" in {
      val read = (request: DbPageRequest) =>
        PaginatedResponse.empty[Int](request.page.pageValue, request.limit).pure[ConnectionIO]
      TgPages.readAll(read).transact(transactor).unsafeRunSync() shouldBe List.empty[Int]
    }
  }
}
