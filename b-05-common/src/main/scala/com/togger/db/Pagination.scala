package com.togger.db

import cats.MonadThrow
import cats.implicits._
import com.togger.common.{DbPageRequest, PageRequest, PaginatedResponse}
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

  /** The `ilike` pattern for "contains `term`": Postgres treats backslash as the LIKE escape by default, so the user's
    * own %, _ and \ are escaped with it.
    */
  def containsPattern(term: String): String =
    "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

  def countOf(select: Fragment): Fragment =
    fr"select count(*) from (" ++ select ++ fr") as paged_count"
}

object TgPages {

  /** Reads page after page until the last one. Only for maintenance jobs and internal runs that must see every row;
    * an endpoint never does this.
    */
  def readAll[F[_]: MonadThrow, A](read: DbPageRequest => F[PaginatedResponse[A]]): F[List[A]] = {
    def loop(page: Int, collected: Vector[A]): F[List[A]] =
      DbPageRequest.fromPageRequest(PageRequest(page, PageRequest.MaxSize)) match {
        case Left(errors) => new IllegalStateException(errors.toString).raiseError[F, List[A]]
        case Right(request) =>
          read(request).flatMap { response =>
            val all = collected ++ response.items
            if (page >= response.totalNumberOfPages) all.toList.pure[F] else loop(page + 1, all)
          }
      }

    loop(PageRequest.DefaultPage, Vector.empty)
  }
}
