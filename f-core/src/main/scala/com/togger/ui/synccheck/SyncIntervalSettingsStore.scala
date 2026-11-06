package com.togger.ui.synccheck

import com.raquo.laminar.api.L._
import com.togger.ui.localstorage.LocalStorage

/** How often the frontend runs a sync by itself (the sync-execute interval); kept in this browser only. */
class SyncIntervalSettingsStore(localStorage: LocalStorage) {

  private val intervalVar: Var[Int] = Var(readIntervalSeconds)

  val intervalSeconds: Signal[Int] = intervalVar.signal

  def set(seconds: Int): Unit = {
    val valid = math.max(seconds, SyncIntervalSettingsStore.MinIntervalSeconds)
    intervalVar.set(valid)
    localStorage.setItem(SyncIntervalSettingsStore.StorageKey, valid.toString)
  }

  def reset(): Unit = set(SyncIntervalSettingsStore.DefaultIntervalSeconds)

  private def readIntervalSeconds: Int =
    SyncIntervalSettingsStore.parse(localStorage.getItem(SyncIntervalSettingsStore.StorageKey))

}

object SyncIntervalSettingsStore {
  val StorageKey = "tg-sync-check-interval-seconds"
  val DefaultIntervalSeconds = 60
  val MinIntervalSeconds = 10

  def parse(stored: Option[String]): Int =
    stored.flatMap(_.toIntOption).filter(_ >= MinIntervalSeconds).getOrElse(DefaultIntervalSeconds)
}
