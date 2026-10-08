package com.togger.ui.components.pagination

sealed trait PageItem

object PageItem {
  case class Page(number: Int) extends PageItem
  case object Ellipsis extends PageItem
}

object PaginationMath {

  private val ShowAllUpTo = 7

  /** The page buttons to show: first and last page plus the current page +-1, with an ellipsis for each gap. A gap
    * of a single page is filled with that page instead, since an ellipsis would not be shorter. The current page is
    * clamped into `1..totalPages`.
    */
  def pages(current: Int, totalPages: Int): Seq[PageItem] = {
    if (totalPages <= 0) return Seq.empty
    if (totalPages <= ShowAllUpTo) return (1 to totalPages).map(PageItem.Page(_))

    val clamped = current.max(1).min(totalPages)
    val shown =
      Set(1, totalPages, clamped - 1, clamped, clamped + 1).filter(n => n >= 1 && n <= totalPages).toList.sorted

    shown.zip(shown.tail).foldLeft(Vector[PageItem](PageItem.Page(shown.head))) { case (acc, (previous, next)) =>
      next - previous match {
        case 1 => acc :+ PageItem.Page(next)
        case 2 => acc :+ PageItem.Page(previous + 1) :+ PageItem.Page(next)
        case _ => acc :+ PageItem.Ellipsis :+ PageItem.Page(next)
      }
    }
  }
}
