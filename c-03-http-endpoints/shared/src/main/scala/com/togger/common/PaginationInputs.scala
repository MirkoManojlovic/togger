package com.togger.common

import sttp.tapir._

object PaginationInputs {

  val pageQuery: EndpointInput[PageRequest] =
    query[Option[Int]]("page")
      .and(query[Option[Int]]("size"))
      .map { case (page, size) => PageRequest.fromOptions(page, size) }(PageRequest.toOptions)
}
