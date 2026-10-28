package com.togger.mcp

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.io.Source
import scala.util.Using

/** Same check as the backend's CheckDesktopAppRunning: polls whether the Togger desktop app is still running and calls
  * `onStopped` once when it is not.
  */
class CheckDesktopAppRunning(
  isRunning: () => Boolean = CheckDesktopAppRunning.desktopAppProcessExists,
  checkInterval: FiniteDuration = 30.seconds,
  onStopped: () => Unit
):

  def start(): Thread =
    val watcher = Thread(() => loop(), "check-desktop-app-running")
    watcher.setDaemon(true)
    watcher.start()
    watcher

  private def loop(): Unit =
    Thread.sleep(checkInterval.toMillis)
    scribe.info("Checking if app is running")
    if isRunning() then
      scribe.info("Desktop app is running")
      loop()
    else
      scribe.info("Desktop app is not running")
      onStopped()

object CheckDesktopAppRunning:
  private val MacOsProcess = "Togger.app/Contents/MacOS/app"

  def containsDesktopApp(psOutput: String): Boolean =
    psOutput.contains(MacOsProcess)

  /** A failing `ps` counts as running, so a hiccup never takes the server down. */
  def desktopAppProcessExists(): Boolean =
    try
      val process = ProcessBuilder("ps", "aux").redirectErrorStream(true).start()
      val output = Using.resource(Source.fromInputStream(process.getInputStream))(_.mkString)
      process.waitFor()
      containsDesktopApp(output)
    catch
      case e: Exception =>
        scribe.warn(s"Cannot check if the desktop app is running: ${e.getMessage}")
        true
