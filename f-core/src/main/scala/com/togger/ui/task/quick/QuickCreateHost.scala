package com.togger.ui.task.quick

import com.raquo.laminar.api.L._

/** Owns the single slot [[CreateTaskQuick]] is rendered into. The slot is bound once; each open builds a fresh
  * component, so its state is reset for free.
  */
class QuickCreateHost(parentElement: HtmlElement, create: (() => Unit) => HtmlElement) {

  private val currentVar: Var[Option[HtmlElement]] = Var(None)

  parentElement.amend(child.maybe <-- currentVar.signal)

  def open(): Unit =
    if (currentVar.now().isEmpty) currentVar.set(Some(create(() => close())))

  def close(): Unit =
    currentVar.set(None)
}
