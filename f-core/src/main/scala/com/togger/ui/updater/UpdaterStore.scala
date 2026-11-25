package com.togger.ui.updater

import com.raquo.laminar.api.L._

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.Failure
import scala.util.Success

sealed trait UpdateState
object UpdateState {
  case object Idle extends UpdateState
  case object Checking extends UpdateState
  case object UpToDate extends UpdateState
  case class Available(version: String, notes: String) extends UpdateState
  case class Downloading(downloaded: Long, total: Option[Long]) extends UpdateState
  case object Stopping extends UpdateState
  case object Installing extends UpdateState
  case object Restarting extends UpdateState
  case class Failed(message: String) extends UpdateState

  /** An update is running: the user must stay on the page until it is done. */
  def isBusy(state: UpdateState): Boolean =
    state match {
      case Downloading(_, _) | Stopping | Installing | Restarting => true
      case _                                                      => false
    }
}

/** Holds the update state for the whole app, so a check on startup shows up on the Updates page. */
class UpdaterStore {
  import UpdateState._

  private val stateVar = Var[UpdateState](Idle)

  val state: Signal[UpdateState] = stateVar.signal

  val busy: Signal[Boolean] = state.map(UpdateState.isBusy)

  // provided by main.js in the desktop app only
  private val updater: Option[js.Dynamic] =
    Option(js.Dynamic.global.window.tgUpdater).filterNot(js.isUndefined).map(_.asInstanceOf[js.Dynamic])

  val isAvailableInApp: Boolean = updater.isDefined

  /** Checks for an update; a failure is reported through the state only when the user asked for the check. */
  def check(reportFailure: Boolean = true, onAvailable: String => Unit = _ => ()): Unit =
    updater.filterNot(_ => stateVar.now() == Checking || UpdateState.isBusy(stateVar.now())).foreach { updater =>
      stateVar.set(Checking)
      updater.check().asInstanceOf[js.Promise[js.Dynamic]].toFuture.onComplete {
        case Success(result) if js.isUndefined(result) || result == null => stateVar.set(UpToDate)
        case Success(result) =>
          val version = result.version.asInstanceOf[String]
          stateVar.set(Available(version, result.notes.asInstanceOf[String]))
          onAvailable(version)
        case Failure(error) =>
          stateVar.set(if (reportFailure) Failed(error.getMessage) else Idle)
      }
    }

  /** Checks once when the app starts and calls onAvailable when there is a newer version. */
  def checkOnStartup(onAvailable: String => Unit): Unit =
    check(reportFailure = false, onAvailable)

  def install(): Unit =
    updater.foreach { updater =>
      stateVar.set(Downloading(0L, None))
      val onEvent: js.Function1[js.Dynamic, Unit] = event => {
        val downloaded = Option(event.downloaded).filterNot(js.isUndefined).fold(0L)(_.asInstanceOf[Double].toLong)
        val total = Option(event.total).filterNot(js.isUndefined).map(_.asInstanceOf[Double].toLong)
        UpdateProgressModel.stateFor(event.phase.asInstanceOf[String], downloaded, total).foreach(stateVar.set)
      }
      updater
        .install(onEvent)
        .asInstanceOf[js.Promise[Unit]]
        .toFuture
        .failed
        .foreach(e => stateVar.set(Failed(e.getMessage)))
    }

}
