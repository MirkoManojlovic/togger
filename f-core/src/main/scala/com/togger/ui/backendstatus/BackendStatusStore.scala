package com.togger.ui.backendstatus

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Failure
import scala.util.Success

/** The state of the backend and the MCP server for the status page. */
class BackendStatusStore(healthService: BackendHealthFeService, control: BackendControl)(implicit runtime: IORuntime) {

  private val controlVar = Var[Option[List[ControlStatus]]](None)
  private val webHealthVar = Var[BackendStatusModel.WebHealth](None)
  private val busyVar = Var(false)
  private val errorVar = Var[Option[String]](None)

  val isControllable: Boolean = control.isAvailable
  val busy: Signal[Boolean] = busyVar.signal
  val error: Signal[Option[String]] = errorVar.signal

  val views: Signal[List[AppStatusView]] =
    controlVar.signal.combineWith(webHealthVar.signal).map { case (controlStatuses, webHealth) =>
      StatusApp.all.map { app =>
        BackendStatusModel.view(app, controlStatuses.flatMap(_.find(_.app == app.id)), webHealth)
      }
    }

  def refresh(): Unit = {
    if (control.isAvailable) report(control.status())
    else refreshWebHealth()
  }

  def start(app: Option[StatusApp]): Unit = act(control.start(app))

  def stop(app: Option[StatusApp]): Unit = act(control.stop(app))

  def restart(app: Option[StatusApp]): Unit = act(control.restart(app))

  private def act(call: => Future[List[ControlStatus]]): Unit =
    if (!busyVar.now()) {
      busyVar.set(true)
      call.onComplete { result =>
        busyVar.set(false)
        update(result)
      }
    }

  private def report(call: => Future[List[ControlStatus]]): Unit = call.onComplete(update)

  private def update(result: scala.util.Try[List[ControlStatus]]): Unit =
    result match {
      case Success(statuses) =>
        errorVar.set(None)
        controlVar.set(Some(statuses))
      case Failure(error) => errorVar.set(Some(Option(error.getMessage).getOrElse(error.toString)))
    }

  private def refreshWebHealth(): Unit =
    healthService
      .health()
      .map(result => webHealthVar.set(Some(result.toOption.flatMap(_.body).map(_.version))))
      .unsafeRunAndForget()
}
