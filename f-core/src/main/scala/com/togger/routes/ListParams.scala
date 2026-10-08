package com.togger.routes

import com.togger.common.PageRequest

/** Which page of a list the URL asks for. */
case class ListParams(page: Int = PageRequest.DefaultPage, size: Int = PageRequest.DefaultSize) {
  def pageRequest: PageRequest = PageRequest(page, size)

  /** Another page of the same list. */
  def atPage(newPage: Int, newSize: Int): ListParams = copy(page = newPage, size = newSize)
}

object ListParams {
  val default: ListParams = ListParams()

  /** A hand-edited URL never gets to the backend as a 400: bad page or size fall back to the defaults. */
  def fromQuery(page: Option[String], size: Option[String]): ListParams =
    ListParams(
      page = page.flatMap(_.trim.toIntOption).filter(_ >= 1).getOrElse(PageRequest.DefaultPage),
      size = size
        .flatMap(_.trim.toIntOption)
        .filter(PageRequest.SizeOptions.contains)
        .getOrElse(PageRequest.DefaultSize)
    )

  def pageToQuery(params: ListParams): Option[String] =
    Option.when(params.page != PageRequest.DefaultPage)(params.page.toString)

  def sizeToQuery(params: ListParams): Option[String] =
    Option.when(params.size != PageRequest.DefaultSize)(params.size.toString)
}
