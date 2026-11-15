package com.togger.ui.backendstatus

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** The start, stop and restart of the backend and the MCP server, provided by the Tauri app (`window.tgBackend` in
  * main.js). Not there in a browser or in the frontend preview.
  */
class BackendControl {

  private val bridge: Option[js.Dynamic] =
    Option(js.Dynamic.global.window.tgBackend).filterNot(js.isUndefined).map(_.asInstanceOf[js.Dynamic])

  val isAvailable: Boolean = bridge.isDefined

  def status(): Future[List[ControlStatus]] = call(_.status())

  def start(app: Option[StatusApp]): Future[List[ControlStatus]] = call(_.start(appArg(app)))

  def stop(app: Option[StatusApp]): Future[List[ControlStatus]] = call(_.stop(appArg(app)))

  def restart(app: Option[StatusApp]): Future[List[ControlStatus]] = call(_.restart(appArg(app)))

  private def appArg(app: Option[StatusApp]): js.Any = app.map(_.id: js.Any).getOrElse(js.undefined)

  private def call(f: js.Dynamic => js.Any): Future[List[ControlStatus]] =
    bridge.fold(Future.successful(List.empty[ControlStatus])) { bridge =>
      f(bridge).asInstanceOf[js.Promise[js.Array[js.Dynamic]]].toFuture.map(_.toList.map(BackendControl.parse))
    }
}

object BackendControl {

  def parse(raw: js.Dynamic): ControlStatus =
    ControlStatus(
      app = raw.app.asInstanceOf[String],
      running = raw.running.asInstanceOf[Boolean],
      version = optionalString(raw.version),
      pid = Option(raw.pid).filterNot(js.isUndefined).map(_.asInstanceOf[Int]),
      expectedVersion = raw.expectedVersion.asInstanceOf[String],
      versionMismatch = raw.versionMismatch.asInstanceOf[Boolean],
      stoppedByUser = raw.stoppedByUser.asInstanceOf[Boolean],
      restarts = raw.restarts.asInstanceOf[Int],
      lastError = optionalString(raw.lastError)
    )

  private def optionalString(value: js.Dynamic): Option[String] =
    Option(value).filterNot(js.isUndefined).map(_.asInstanceOf[String])
}
