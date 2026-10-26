package com.togger.ui.dataimportconfig

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.TgJsonFailure
import com.togger.dto.{DataExportPathStatusResp, DataExportSyncStatusResp, DataImportResp}
import com.togger.ui.components.{TgTab, TgTabs}
import com.togger.ui.dataexportconfig.ExportCheckComponent
import com.togger.ui.modal.ModalFactory
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.DataExportFeService
import com.togger.ui.toast.Toast

import java.util.UUID

/** Every import ends in the import results modal, also when nothing could be imported. */
class ImportFlow(
  dataExportService: DataExportFeService,
  dataImportConfigService: DataImportConfigFeService,
  spinnerService: SpinnerService,
  modalFactory: ModalFactory,
  toast: Toast
)(implicit ioRuntime: IORuntime) {

  def importFrom(projectId: UUID, importPath: String): Unit =
    check(projectId, IO.pure(List(importPath).asRight[TgJsonFailure]))

  /** Loads from every import config of the project, not only the ones on the page on screen. */
  def importAll(projectId: UUID): Unit =
    check(projectId, dataImportConfigService.findAllByProjectId(projectId).map(_.map(_.body.get.map(_.importPath).distinct.toList)))

  /** Every import starts with a look at what it would bring in; only the paths that have something new are imported. */
  private def check(projectId: UUID, paths: IO[Either[TgJsonFailure, List[String]]]): Unit = {
    val program: IO[Either[TgJsonFailure, List[Either[TgJsonFailure, DataExportPathStatusResp]]]] =
      paths.flatMap(
        _.traverse(
          _.traverse(path => dataExportService.checkImportStatus(projectId, path).map(_.map(_.body.get.pathStatuses.head)))
        )
      )

    spinnerService
      .runWithSpinner(program)
      .map {
        case Left(failure) =>
          toast.error(failure)
          modalFactory.previewModal(div(cls("p-4 text-body"), "Could not load the import paths of this project"), "Import check")
        case Right(outcomes) =>
          outcomes.collect { case Left(failure) => failure }.foreach(toast.error)
          val statuses = outcomes.collect { case Right(status) => status }
          modalFactory.previewModal(checkContent(projectId, outcomes.size, statuses), "Import check")
      }
      .unsafeRunAndForget()
  }

  private def needsImport(status: DataExportSyncStatusResp): Boolean =
    status == DataExportSyncStatusResp.MissingFromDb || status == DataExportSyncStatusResp.OutOfSync

  private def checkContent(projectId: UUID, pathCount: Int, statuses: List[DataExportPathStatusResp]): HtmlElement =
    if (pathCount == 0) div(cls("p-4 text-body"), "No import paths configured for this project")
    else if (statuses.isEmpty) div(cls("p-4 text-body"), "None of the import paths could be checked")
    else {
      new ExportCheckComponent(
        statuses,
        onAll = pathsToImport => run(projectId, pathsToImport.map(_.exportPath)),
        onPath = path => run(projectId, List(path.exportPath)),
        actionLabel = "Import",
        statusTitle = "Import status",
        needsAction = needsImport(_)
      ).element
    }

  private def run(projectId: UUID, importPaths: List[String]): Unit = {
    val program: IO[(List[String], List[(String, Either[TgJsonFailure, DataImportResp])])] =
      importPaths
        .traverse(path => dataExportService.importData(projectId, path).map(resp => path -> resp.map(_.body.get)))
        .map(importPaths -> _)

    spinnerService
      .runWithSpinner(program)
      .map { case (importPaths, outcomes) =>
        val successes = outcomes.collect { case (_, Right(dto)) => dto }
        val failures = outcomes.collect { case (_, Left(failure)) => failure }
        failures.foreach(toast.error)
        if (failures.isEmpty && successes.nonEmpty)
          toast.success(s"Imported from ${successes.size} path(s), ${successes.map(_.results.size).sum} entity(ies) total")
        else if (failures.nonEmpty && successes.nonEmpty)
          toast.error(s"Imported from ${successes.size}/${importPaths.size} path(s); ${failures.size} failed")
        modalFactory.previewModal(content(importPaths, outcomes), "Import results")
      }
      .unsafeRunAndForget()
  }

  private def pathPanel(outcome: Either[TgJsonFailure, DataImportResp]): HtmlElement =
    outcome.fold(
      _ => div(cls("text-body"), "Importing from this path failed"),
      dto =>
        if (dto.results.isEmpty) div(cls("text-body"), "Nothing to import from this path")
        else new ImportResultsComponent(dto.results).element
    )

  private def content(importPaths: List[String], outcomes: List[(String, Either[TgJsonFailure, DataImportResp])]) =
    outcomes match {
      case Nil                        => div(cls("p-4 text-body"), "No import paths configured for this project")
      case ("", _) :: Nil             => div(cls("p-4 text-body"), "Could not load the import paths of this project")
      case (_, outcome) :: Nil        => div(cls("p-4"), pathPanel(outcome))
      case several =>
        val tabs = several.zipWithIndex.map { case ((path, outcome), index) =>
          TgTab(s"import-path-$index", path, pathPanel(outcome))
        }
        div(cls("p-4"), new TgTabs(tabs, Var(tabs.head.id)).element)
    }

}
