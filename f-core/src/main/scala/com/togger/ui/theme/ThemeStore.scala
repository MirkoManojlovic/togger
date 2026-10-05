package com.togger.ui.theme

import com.raquo.laminar.api.L._
import com.togger.ui.localstorage.LocalStorage
import org.scalajs.dom
import org.scalajs.dom.Event

sealed trait ThemeMode
object ThemeMode {
  case object Light extends ThemeMode
  case object Dark extends ThemeMode
  case object System extends ThemeMode

  def fromStorage(value: Option[String]): ThemeMode = value match {
    case Some("light") => Light
    case Some("dark")  => Dark
    case _              => System
  }

  def toStorage(mode: ThemeMode): String = mode match {
    case Light  => "light"
    case Dark   => "dark"
    case System => "system"
  }

  def next(mode: ThemeMode): ThemeMode = mode match {
    case Light  => Dark
    case Dark   => System
    case System => Light
  }
}

class ThemeStore(localStorage: LocalStorage) {
  import ThemeMode._

  private val modeVar: Var[ThemeMode] = Var(fromStorage(localStorage.getItem(ThemeStore.StorageKey)))
  val mode: Signal[ThemeMode] = modeVar.signal

  private val systemPrefersDarkVar: Var[Boolean] = Var(systemPrefersDarkNow)

  val isDark: Signal[Boolean] =
    mode.combineWith(systemPrefersDarkVar.signal).map {
      case (Dark, _)         => true
      case (Light, _)        => false
      case (System, sysDark) => sysDark
    }

  def cycle(): Unit = set(next(modeVar.now()))

  def set(newMode: ThemeMode): Unit = {
    modeVar.set(newMode)
    localStorage.setItem(ThemeStore.StorageKey, toStorage(newMode))
  }

  def applyToDocument(dark: Boolean): Unit =
    dom.document.documentElement.classList.toggle("dark", dark)

  /** Keeps System mode live if the OS theme changes while the app is open. */
  def listenToSystemPreference(): Unit = {
    val mql = dom.window.matchMedia("(prefers-color-scheme: dark)")
    mql.addEventListener[Event]("change", (_: Event) => systemPrefersDarkVar.set(mql.matches))
  }

  private def systemPrefersDarkNow: Boolean =
    dom.window.matchMedia("(prefers-color-scheme: dark)").matches
}

object ThemeStore {
  val StorageKey = "tg-color-theme"
}
