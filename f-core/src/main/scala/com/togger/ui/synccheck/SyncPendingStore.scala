package com.togger.ui.synccheck

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.ui.task.DataExportFeService

import java.util.UUID

case class ProjectSyncPending(exportPending: Boolean, importPending: Boolean)

/** Asks the backend, every interval of the settings, which projects have something to export or import. */
class SyncPendingStore(dataExportService: DataExportFeService, settings: SyncCheckSettingsStore)(implicit
  ioRuntime: IORuntime
) {

  private val pendingVar: Var[Map[UUID, ProjectSyncPending]] = Var(Map.empty)

  private val refreshRequests = new EventBus[Unit]

  private var checking = false

  def exportPending(projectId: UUID): Signal[Boolean] =
    pendingVar.signal.map(_.get(projectId).exists(_.exportPending)).distinct

  def importPending(projectId: UUID): Signal[Boolean] =
    pendingVar.signal.map(_.get(projectId).exists(_.importPending)).distinct

  /** Checks again right away, e.g. after an export or import. */
  def refresh(): Unit = refreshRequests.emit(())

  /** Keeps checking the given projects for as long as the element it is bound to is mounted. */
  def binder(projectIds: Signal[Seq[UUID]]): Binder[HtmlElement] = {
    val ticks = settings.intervalSeconds.distinct.flatMapSwitch(seconds => EventStream.periodic(seconds * 1000))
    EventStream
      .merge(ticks.mapToUnit, refreshRequests.events, projectIds.distinct.changes.mapToUnit)
      .withCurrentValueOf(projectIds) --> Observer[Seq[UUID]](check)
  }

  private def check(projectIds: Seq[UUID]): Unit =
    if (!checking && projectIds.nonEmpty) {
      checking = true
      projectIds.toList
        .traverse(id => checkProject(id).map(id -> _))
        .map(results => pendingVar.update(old => old ++ results.collect { case (id, Some(pending)) => id -> pending }))
        .guarantee(IO { checking = false })
        .unsafeRunAndForget()
    }

  /** A failed check keeps the last known state instead of clearing the circles. */
  private def checkProject(projectId: UUID): IO[Option[ProjectSyncPending]] =
    for {
      exportResult <- dataExportService.exportPending(projectId)
      importResult <- dataExportService.importPending(projectId)
    } yield for {
      exported <- exportResult.toOption
      imported <- importResult.toOption
    } yield ProjectSyncPending(exported.body.get.pending, imported.body.get.pending)

}
