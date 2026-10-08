package com.togger.common

import cats.implicits._
import doobie.ConnectionIO
import org.scalatest.Assertion

/** The shared scenario of the paginated repo tests: 11 rows read in pages of 5 give 3 pages (5, 5, 1), then an empty
  * page past the end that still carries the totals.
  */
trait PaginationTestSupport { self: BusinessTgDbSpec =>

  def pageRequest(pageNumber: Int, size: Int): DbPageRequest =
    DbPageRequest.fromPageRequest(PageRequest(pageNumber, size)).toOption.get

  def assertElevenRowsOverThreePages[A, K](
    read: DbPageRequest => ConnectionIO[PaginatedResponse[A]],
    expectedKeys: Seq[K]
  )(key: A => K): ConnectionIO[Assertion] =
    for {
      pages <- (1 to 3).toList.traverse(number => read(pageRequest(number, 5)))
      pastEnd <- read(pageRequest(4, 5))
    } yield {
      val cp = new Checkpoint
      cp(pages.map(_.items.size) shouldBe List(5, 5, 1))
      cp(pages.map(_.numberOfItemsReturned) shouldBe List(5, 5, 1))
      cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(11L))
      cp(pages.map(_.totalNumberOfPages).distinct shouldBe List(3L))
      cp(pages.map(_.page) shouldBe List(1, 2, 3))
      cp(pages.flatMap(_.items.map(key)) shouldBe expectedKeys.toList)
      cp(pastEnd.items shouldBe empty)
      cp(pastEnd.totalNumberOfItems shouldBe 11L)
      cp(pastEnd.totalNumberOfPages shouldBe 3L)
      cp.reportAll()
      succeed
    }
}
