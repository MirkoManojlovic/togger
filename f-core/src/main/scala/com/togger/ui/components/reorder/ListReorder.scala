package com.togger.ui.components.reorder

object ListReorder {

  /** Moves an item to a drop position, counted in the gaps before each item: 0 is above the first, `size` below the
    * last.
    */
  def moveToInsertion[A](items: Seq[A], from: Int, insertion: Int): Seq[A] = {
    if (!items.indices.contains(from) || insertion < 0 || insertion > items.size) return items
    val target = if (insertion > from) insertion - 1 else insertion
    if (target == from) return items
    val without = items.patch(from, Nil, 1)
    without.patch(target, Seq(items(from)), 0)
  }

  /** The gap that one step up (-1) or down (+1) from the index lands in. */
  def insertionFor(index: Int, delta: Int): Int = if (delta > 0) index + delta + 1 else index + delta

  /** One step up (-1) or down (+1); at either end nothing moves. */
  def moveBy[A](items: Seq[A], index: Int, delta: Int): Seq[A] =
    moveToInsertion(items, index, insertionFor(index, delta))
}
