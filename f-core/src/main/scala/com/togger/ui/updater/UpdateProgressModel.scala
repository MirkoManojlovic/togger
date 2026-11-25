package com.togger.ui.updater

/** Pure logic of the update progress, kept apart from the DOM so it can be tested. */
object UpdateProgressModel {

  /** The percent downloaded; None when the total size is unknown or zero. */
  def percent(downloaded: Long, total: Option[Long]): Option[Int] =
    total.filter(_ > 0).map(t => math.min(100L, math.max(0L, downloaded * 100 / t)).toInt)

  def formatBytes(bytes: Long): String = {
    val megabytes = bytes.toDouble / (1024 * 1024)
    if (megabytes >= 1) f"$megabytes%.1f MB" else f"${bytes.toDouble / 1024}%.0f KB"
  }

  /** E.g. "12.3 MB of 48.0 MB", or just the downloaded size when the total is unknown. */
  def sizeText(downloaded: Long, total: Option[Long]): String =
    total.filter(_ > 0).fold(formatBytes(downloaded))(t => s"${formatBytes(downloaded)} of ${formatBytes(t)}")

  /** Maps an event of `window.tgUpdater.install` to the state; None for a phase this UI does not know. */
  def stateFor(phase: String, downloaded: Long, total: Option[Long]): Option[UpdateState] =
    phase match {
      case "downloading" => Some(UpdateState.Downloading(downloaded, total))
      case "stopping"    => Some(UpdateState.Stopping)
      case "installing"  => Some(UpdateState.Installing)
      case "restarting"  => Some(UpdateState.Restarting)
      case _             => None
    }
}
