package com.togger.common

case class PaginatedResponse[A](
  page: Int,
  size: Int,
  items: List[A],
  numberOfItemsReturned: Int,
  totalNumberOfItems: Long,
  totalNumberOfPages: Long
) {
  def mapItem[T](f: A => T): PaginatedResponse[T] =
    copy(items = items.map(f))
}

object PaginatedResponse {

  def of[A](page: Int, size: Int, items: List[A], totalNumberOfItems: Long): PaginatedResponse[A] =
    PaginatedResponse(
      page = page,
      size = size,
      items = items,
      numberOfItemsReturned = items.size,
      totalNumberOfItems = totalNumberOfItems,
      totalNumberOfPages = totalPages(totalNumberOfItems, size)
    )

  def empty[A](page: Int, size: Int): PaginatedResponse[A] =
    of(page, size, List.empty, 0L)

  def totalPages(totalNumberOfItems: Long, size: Int): Long =
    if (size <= 0) 0L else (totalNumberOfItems + size - 1) / size
}
