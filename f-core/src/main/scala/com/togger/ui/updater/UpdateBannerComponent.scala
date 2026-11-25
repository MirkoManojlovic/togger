package com.togger.ui.updater

import com.raquo.laminar.api.L._

/** Shows a running update on every page, since the user cannot leave while it runs. */
class UpdateBannerComponent(store: UpdaterStore, onUpdatesPage: Signal[Boolean]) {

  private val visible: Signal[Boolean] =
    store.busy.combineWith(onUpdatesPage).map { case (busy, there) => busy && !there }

  val element: Div =
    div(
      child.maybe <-- visible.combineWith(store.state).map { case (show, state) =>
        Option.when(show)(banner(state))
      }
    )

  private def banner(state: UpdateState): HtmlElement =
    div(
      cls("fixed bottom-4 right-4 z-50 w-80 p-4 rounded-base bg-neutral-primary-soft border border-default shadow-lg"),
      role("status"),
      p(cls("text-sm font-medium text-heading"), "Updating Togger"),
      p(cls("mt-1 text-sm text-body"), UpdaterComponent.statusText(state)),
      UpdaterComponent.progressBar(state).getOrElse(emptyNode),
      p(cls("mt-2 text-xs text-body"), "Navigation is disabled until the update finishes.")
    )
}
