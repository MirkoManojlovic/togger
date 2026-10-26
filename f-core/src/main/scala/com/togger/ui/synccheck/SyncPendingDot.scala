package com.togger.ui.synccheck

import com.raquo.laminar.api.L._

/** The small circle on the corner of an import or export button, shown while the button has something to do. */
object SyncPendingDot {

  def apply(pending: Signal[Boolean], label: String): Modifier[HtmlElement] =
    child <-- pending.map { isPending =>
      if (isPending)
        span(
          cls("absolute -top-0.5 -right-0.5 block w-2 h-2 rounded-full bg-brand pointer-events-none"),
          title(label),
          span(cls("sr-only"), label)
        )
      else emptyNode
    }

}
