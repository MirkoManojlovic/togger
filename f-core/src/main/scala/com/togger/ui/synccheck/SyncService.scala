package com.togger.ui.synccheck

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.DataSyncResp
import com.togger.preview.PreviewConfig
import com.togger.ui.modal.ModalFactory
import com.togger.ui.task.DataExportFeService
import com.togger.ui.toast.Toast

/** The one sync of the navbar: imports then exports everything. A click shows the results in a modal; the automatic
  * sync (the sync-execute interval of the settings) runs the same sync without the modal and only reports a failure.
  */
class SyncService(
  dataExportService: DataExportFeService,
  syncPendingStore: SyncPendingStore,
  settings: SyncIntervalSettingsStore,
  modalFactory: ModalFactory,
  toast: Toast,
  previewConfig: PreviewConfig
)(implicit ioRuntime: IORuntime) {

  private val runningVar: Var[Boolean] = Var(false)

  val running: Signal[Boolean] = runningVar.signal

  /** There is nothing to sync in frontend preview mode, and only one sync runs at a time. */
  val canSync: Signal[Boolean] = running.map(isRunning => !isRunning && !previewConfig.enabled)

  val pending: Signal[Boolean] = syncPendingStore.anyPending

  val tooltip: Signal[String] = running.combineWith(syncPendingStore.anyPending).map {
    case _ if previewConfig.enabled => "Sync is not available in frontend preview mode"
    case (true, _)                  => "Sync is running"
    case (false, true)              => "Something is waiting to be synced - click to import and export"
    case (false, false)             => "Sync: import, then export"
  }

  /** Runs the automatic sync every interval of the settings for as long as the element it is bound to is mounted. */
  val autoSyncBinder: Binder[HtmlElement] =
    settings.intervalSeconds.distinct
      .flatMapSwitch(seconds => EventStream.periodic(seconds * 1000))
      .mapToUnit --> Observer[Unit](_ => autoSync())

  /** The click of the button: the results are shown in a modal. */
  def sync(): Unit = run { response =>
    toast.success(summary(response))
    modalFactory.previewModal(new SyncResultsComponent(response).element, "Sync results")
  }

  /** Skipped while another sync is running; success is silent, the button just goes back to normal. */
  private def autoSync(): Unit = run(_ => ())

  private def run(onSuccess: DataSyncResp => Unit): Unit =
    if (!runningVar.now() && !previewConfig.enabled) {
      runningVar.set(true)
      dataExportService
        .sync()
        .guarantee(IO(runningVar.set(false)))
        .map {
          case Left(failure) => toast.error(failure)
          case Right(success) =>
            syncPendingStore.refresh()
            onSuccess(success.body.get)
        }
        .unsafeRunAndForget()
    }

  private def summary(response: DataSyncResp): String = {
    val paths = response.projects.flatMap(_.imports).size
    s"Synced ${response.projects.size} project(s): imported from $paths path(s), then exported"
  }

}
