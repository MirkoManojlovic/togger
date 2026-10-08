package com.togger.ui.components.pagination

import cats.effect.IO
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}

/** Reads every page of a list for the screens that need all of it (charts, progress, calendars) instead of one page.
  * Bounded: at most `MaxPages` pages of `PageRequest.MaxSize` items are read, so a huge list is cut off, not endless.
  */
object PaginatedLoader {

  val MaxPages: Int = 50

  def loadAll[A](fetch: PageRequest => TgRespIO[PaginatedResponse[A]]): TgRespIO[Seq[A]] = {
    def pageRequest(page: Int) = PageRequest(page, PageRequest.MaxSize)

    def loop(page: Int, collected: Vector[A]): TgRespIO[Seq[A]] =
      fetch(pageRequest(page)).flatMap {
        case Left(failure) => IO.pure(Left(failure))
        case Right(success) =>
          val response = success.body.getOrElse(PaginatedResponse.empty[A](page, PageRequest.MaxSize))
          val all = collected ++ response.items
          if (page >= response.totalNumberOfPages || page >= MaxPages) TgJsonSuccess.justBodyRightIO(all)
          else loop(page + 1, all)
      }

    loop(PageRequest.DefaultPage, Vector.empty)
  }
}
