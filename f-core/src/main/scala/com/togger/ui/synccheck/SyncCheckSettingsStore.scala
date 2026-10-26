package com.togger.ui.synccheck

import com.raquo.laminar.api.L._
import com.togger.ui.localstorage.LocalStorage

/** How often the frontend asks the backend whether there is something to import or export; kept in this browser only. */
class SyncCheckSettingsStore(localStorage: LocalStorage) {

  private val intervalVar: Var[Int] = Var(readIntervalSeconds)

  val intervalSeconds: Signal[Int] = intervalVar.signal

  def set(seconds: Int): Unit = {
    val valid = math.max(seconds, SyncCheckSettingsStore.MinIntervalSeconds)
    intervalVar.set(valid)
    localStorage.setItem(SyncCheckSettingsStore.StorageKey, valid.toString)
  }

  def reset(): Unit = set(SyncCheckSettingsStore.DefaultIntervalSeconds)

  private def readIntervalSeconds: Int =
    SyncCheckSettingsStore.parse(localStorage.getItem(SyncCheckSettingsStore.StorageKey))

}

object SyncCheckSettingsStore {
  val StorageKey = "tg-sync-check-interval-seconds"
  val DefaultIntervalSeconds = 60
  val MinIntervalSeconds = 10

  def parse(stored: Option[String]): Int =
    stored.flatMap(_.toIntOption).filter(_ >= MinIntervalSeconds).getOrElse(DefaultIntervalSeconds)
}
