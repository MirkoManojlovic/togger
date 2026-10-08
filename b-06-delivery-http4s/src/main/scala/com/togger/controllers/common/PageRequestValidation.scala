package com.togger.controllers.common

import com.togger.common.TgError._
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{DbPageRequest, PageRequest}

object PageRequestValidation {

  /** Validates the page request and runs `f`, or answers 400 with every page/size error. */
  def withPage[A](request: PageRequest)(f: DbPageRequest => TgRespIO[A]): TgRespIO[A] =
    DbPageRequest
      .fromPageRequest(request)
      .fold(_.toBadRequestLeftIO[A], f)
}
