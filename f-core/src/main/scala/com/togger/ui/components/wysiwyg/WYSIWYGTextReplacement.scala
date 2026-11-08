package com.togger.ui.components.wysiwyg

/** The smallest edit that turns one text into another: `replacement` takes the place of the characters `start` until
  * `end` of the old text.
  *
  * The editor applies a toolbar command as this edit instead of assigning the whole value, because only an edit the
  * browser performs itself lands in the textarea's undo stack, and a small one keeps the browser's caret and scroll
  * where they were.
  */
case class WYSIWYGTextReplacement(start: Int, end: Int, replacement: String)

object WYSIWYGTextReplacement {

  def between(oldText: String, newText: String): WYSIWYGTextReplacement = {
    val prefix = commonPrefixLength(oldText, newText)
    val suffix = commonSuffixLength(oldText, newText, prefix)

    WYSIWYGTextReplacement(prefix, oldText.length - suffix, newText.substring(prefix, newText.length - suffix))
  }

  private def commonPrefixLength(a: String, b: String): Int =
    a.zip(b).takeWhile { case (x, y) => x == y }.length

  /** Never reaches into the prefix, so `abab` -> `ab` is one deletion and not an overlap of the two matches. */
  private def commonSuffixLength(a: String, b: String, prefix: Int): Int = {
    val limit = math.min(a.length, b.length) - prefix

    a.reverseIterator.zip(b.reverseIterator).take(limit).takeWhile { case (x, y) => x == y }.length
  }
}
