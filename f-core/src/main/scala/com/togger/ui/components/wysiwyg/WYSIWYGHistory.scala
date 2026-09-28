package com.togger.ui.components.wysiwyg

/** Undo/redo as a stack of markdown snapshots.
  *
  * The browser's native stack cannot be relied on: input rules, toolbar commands and the Enter/Backspace handlers
  * all rewrite the DOM behind its back, so a native undo would step over those edits or restore a DOM that no
  * longer matches the model. The editor intercepts the shortcuts and drives this instead.
  *
  * Snapshots are whole documents. They are small (a log entry, not a book) and it keeps undo honest — restoring one
  * is a repaint, which cannot leave the DOM in a state the serializer disagrees with.
  */
class WYSIWYGHistory(limit: Int = 100) {

  private var past: List[String] = Nil
  private var future: List[String] = Nil
  private var current: Option[String] = None

  def reset(value: String): Unit = {
    past = Nil
    future = Nil
    current = Some(value)
  }

  /** A new edit invalidates anything that was undone — the usual branch-discarding behaviour. */
  def record(value: String): Unit =
    if (!current.contains(value)) {
      current.foreach(previous => past = (previous :: past).take(limit))
      current = Some(value)
      future = Nil
    }

  def undo(): Option[String] = past match {
    case previous :: rest =>
      current.foreach(value => future = value :: future)
      past = rest
      current = Some(previous)
      Some(previous)
    case Nil => None
  }

  def redo(): Option[String] = future match {
    case next :: rest =>
      current.foreach(value => past = value :: past)
      future = rest
      current = Some(next)
      Some(next)
    case Nil => None
  }

  def canUndo: Boolean = past.nonEmpty

  def canRedo: Boolean = future.nonEmpty
}
