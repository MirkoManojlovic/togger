package com.togger.db

import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.implicits._
import doobie.{ConnectionIO, Fragment}

/** Runs a page query and its count query in the same transaction.
  *
  * Ordering in `pageQuery` must be total (end with a unique tie-breaker such as `id`) or pages can overlap.
  */
case class TgFindPaginatedQuery[A](
  pageQuery: DbPageRequest => ConnectionIO[List[A]],
  countQuery: ConnectionIO[Long]
) {

  def run(request: DbPageRequest): ConnectionIO[PaginatedResponse[A]] =
    countQuery.flatMap { total =>
      if (total == 0L || request.rowOffset >= total)
        PaginatedResponse.of(request.page.pageValue, request.limit, List.empty[A], total).pure[ConnectionIO]
      else
        pageQuery(request).map(PaginatedResponse.of(request.page.pageValue, request.limit, _, total))
    }
}

object PaginationSql {

  def limitOffset(request: DbPageRequest): Fragment =
    fr"limit ${request.limit} offset ${request.rowOffset}"

  def orderBy(columns: Fragment*): Fragment =
    fr"order by" ++ columns.reduce((a, b) => a ++ fr"," ++ b)

  def countOf(select: Fragment): Fragment =
    fr"select count(*) from (" ++ select ++ fr") as paged_count"
}
