package com.togger.ui.navbar

import com.raquo.laminar.api.L._
import com.togger.ui.localstorage.LocalStorage

class SidebarCollapseStore(localStorage: LocalStorage) {

  private val collapsedVar: Var[Boolean] = Var(readCollapsed)

  val current: Signal[Boolean] = collapsedVar.signal

  def now(): Boolean = collapsedVar.now()

  def set(collapsed: Boolean): Unit = {
    collapsedVar.set(collapsed)
    localStorage.setItem(SidebarCollapseStore.StorageKey, collapsed.toString)
  }

  def toggle(): Unit = set(!now())

  private def readCollapsed: Boolean =
    localStorage.getItem(SidebarCollapseStore.StorageKey).contains("true")

}

object SidebarCollapseStore {
  val StorageKey = "tg-sidebar-collapsed"
}
