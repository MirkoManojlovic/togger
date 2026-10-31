package com.togger.preview

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.{TgResp, TgRespIO}
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonFailure, TgJsonSuccess}
import com.togger.ui.auth.LoginRepo

import java.util.UUID

/** Small helpers so that every preview service answers the way the backend does. */
object PreviewResponses {

  def ok[A](body: A): TgRespIO[A] = TgJsonSuccess.justBodyRightIO(body)

  def okEmpty(message: String = "Done"): TgRespIO[TgEmptyBody] = IO.pure(TgJsonSuccess.justMessageRight(message))

  def notFound[A]: TgRespIO[A] = TgJsonFailure.notFoundLeftIO[A]()

  def badRequest[A](errors: String*): TgRespIO[A] = IO.pure(TgJsonFailure.badRequestStringLeft[A](errors: _*))

  def fromOption[A](found: Option[A]): TgRespIO[A] = found.fold(notFound[A])(ok(_))

  /** Pages are 1 based like in the backend repos, see [[PreviewPaging.page]]. */
  def paged[A](items: Seq[A], request: PageRequest): TgRespIO[PaginatedResponse[A]] =
    ok(PreviewPaging.page(items, request))

  /** Mirrors `ProtectedBackendClient`: no session means unauthenticated and a logout. */
  def authenticated[A](loginRepo: LoginRepo)(response: UUID => TgRespIO[A]): TgRespIO[A] =
    loginRepo.account.fold {
      loginRepo.delete()
      TgJsonFailure.unauthenticatedLeftIO[A](None)
    }(account => response(account.id))

  def leftOrRight[A](either: Either[String, A]): TgResp[A] =
    either.fold(error => TgJsonFailure.badRequestStringLeft[A](error), body => TgJsonSuccess.justBodyRight(body))
}

object PreviewPaging {

  def page[A](items: Seq[A], request: PageRequest): PaginatedResponse[A] = {
    val size = math.max(request.size, 1)
    val pageNumber = math.max(request.page, 1)
    val from = (pageNumber - 1) * size
    PaginatedResponse.of(pageNumber, size, items.slice(from, from + size).toList, items.size.toLong)
  }
}
