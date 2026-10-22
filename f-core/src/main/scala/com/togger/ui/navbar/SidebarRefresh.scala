package com.togger.ui.navbar

import com.raquo.laminar.api.L._

/** Lets pages that change what the sidebar lists (e.g. a project icon) ask it to reload. */
class SidebarRefresh {

  private val bus = new EventBus[Unit]

  val requests: EventStream[Unit] = bus.events

  def request(): Unit = bus.emit(())
}
