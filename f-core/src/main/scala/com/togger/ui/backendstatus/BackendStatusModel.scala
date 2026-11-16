package com.togger.ui.backendstatus

/** The two processes the status page shows; `id` is what the Tauri commands take as `app`. */
sealed abstract class StatusApp(val id: String, val label: String)
object StatusApp {
  case object Backend extends StatusApp("backend", "Backend")
  case object Mcp extends StatusApp("mcp", "MCP server")

  val all: List[StatusApp] = List(Backend, Mcp)
}

sealed trait AppState
object AppState {
  case object Checking extends AppState
  case class Running(version: String) extends AppState
  case object Down extends AppState

  /** The desktop app is not there to ask (web), so the state of the process is not known. */
  case object Unknown extends AppState
}

/** What the Tauri side reports for one app (`processes.rs`, `AppStatus`). */
case class ControlStatus(
  app: String,
  running: Boolean,
  version: Option[String],
  pid: Option[Int],
  expectedVersion: String,
  versionMismatch: Boolean,
  stoppedByUser: Boolean,
  restarts: Int,
  lastError: Option[String]
)

case class AppStatusView(
  app: StatusApp,
  state: AppState,
  pid: Option[Int],
  expectedVersion: Option[String],
  versionMismatch: Boolean,
  stoppedByUser: Boolean,
  restarts: Int,
  lastError: Option[String]
)

object BackendStatusModel {

  /** The result of the public `GET /health` of the backend: `None` while it is not checked yet. */
  type WebHealth = Option[Option[String]]

  /** The Tauri status wins when there is one (it knows both apps); in a browser only the backend can be asked. */
  def view(app: StatusApp, control: Option[ControlStatus], webHealth: WebHealth): AppStatusView =
    control match {
      case Some(status) =>
        AppStatusView(
          app,
          stateOf(status.running, status.version),
          status.pid.filter(_ => status.running),
          Some(status.expectedVersion),
          status.versionMismatch,
          status.stoppedByUser,
          status.restarts,
          status.lastError
        )
      case None =>
        val state = (app, webHealth) match {
          case (StatusApp.Backend, None)          => AppState.Checking
          case (StatusApp.Backend, Some(Some(v))) => AppState.Running(v)
          case (StatusApp.Backend, Some(None))    => AppState.Down
          case _                                  => AppState.Unknown
        }
        AppStatusView(
          app,
          state,
          None,
          None,
          versionMismatch = false,
          stoppedByUser = false,
          restarts = 0,
          lastError = None
        )
    }

  private def stateOf(running: Boolean, version: Option[String]): AppState =
    version.filter(_ => running).fold[AppState](AppState.Down)(AppState.Running(_))

  def statusText(view: AppStatusView): String =
    view.state match {
      case AppState.Checking                   => "Checking..."
      case AppState.Unknown                    => "Only the Togger desktop app can tell"
      case AppState.Running(_)                 => "Running"
      case AppState.Down if view.stoppedByUser => "Stopped"
      case AppState.Down                       => "Not running"
    }

  def versionText(view: AppStatusView): String =
    view.state match {
      case AppState.Running(version) => version
      case _                         => "-"
    }
}
