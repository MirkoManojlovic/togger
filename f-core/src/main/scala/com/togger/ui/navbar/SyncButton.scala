package com.togger.ui.navbar

import com.raquo.laminar.api.L._
import com.togger.ui.organization.TgIcons
import com.togger.ui.synccheck.SyncPendingDot
import com.togger.ui.synccheck.SyncService

/** The single sync button of the navbar: spins while syncing, is grayed out when sync is not possible and has a circle
  * when something is waiting to be synced.
  */
class SyncButton(syncService: SyncService) {

  val element =
    button(
      `type`("button"),
      cls(ThemeToggleButton.buttonClass, "relative"),
      cls("opacity-50 cursor-not-allowed") <-- syncService.canSync.map(!_),
      disabled <-- syncService.canSync.map(!_),
      title <-- syncService.tooltip,
      aria.busy <-- syncService.running,
      syncService.autoSyncBinder,
      SyncPendingDot(syncService.pending, "There is something to sync"),
      span(cls("sr-only"), "Sync"),
      span(cls("inline-flex"), cls("animate-spin") <-- syncService.running, TgIcons.syncIcon("w-5 h-5")),
      onClick.mapToUnit --> (_ => syncService.sync())
    )

}
