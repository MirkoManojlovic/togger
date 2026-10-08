package com.togger.common

case class PageRequest(page: Int, size: Int)

object PageRequest {
  val DefaultPage: Int = 1
  val DefaultSize: Int = 20
  val MaxSize: Int = 100
  val SizeOptions: List[Int] = List(10, 20, 50, 100)

  val default: PageRequest = PageRequest(DefaultPage, DefaultSize)

  def fromOptions(page: Option[Int], size: Option[Int]): PageRequest =
    PageRequest(page.getOrElse(DefaultPage), size.getOrElse(DefaultSize))

  def toOptions(request: PageRequest): (Option[Int], Option[Int]) =
    (Some(request.page), Some(request.size))
}
