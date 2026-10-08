package com.togger.ui.components.pagination

import com.togger.common.PaginatedResponse

case class PaginationState(page: Int, size: Int, totalItems: Long, totalPages: Int, itemsReturned: Int) {

  def firstItem: Int =
    if (itemsReturned <= 0) 0 else (page - 1) * size + 1

  def lastItem: Int =
    if (itemsReturned <= 0) 0 else firstItem + itemsReturned - 1

  def hasPrevious: Boolean = page > 1

  def hasNext: Boolean = page < totalPages
}

object PaginationState {

  def fromResponse(response: PaginatedResponse[_]): PaginationState =
    PaginationState(
      page = response.page,
      size = response.size,
      totalItems = response.totalNumberOfItems,
      totalPages = response.totalNumberOfPages.toInt,
      itemsReturned = response.numberOfItemsReturned
    )
}
