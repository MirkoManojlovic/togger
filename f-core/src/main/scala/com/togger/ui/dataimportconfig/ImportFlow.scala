package com.togger.ui.dataimportconfig

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.TgJsonFailure
import com.togger.dto.DataImportResp
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
    run(projectId, IO.pure(List(importPath).asRight[TgJsonFailure]))

  /** Loads from every import config of the project, not only the ones on the page on screen. */
  def importAll(projectId: UUID): Unit =
    run(projectId, dataImportConfigService.findAllByProjectId(projectId).map(_.map(_.body.get.map(_.importPath).distinct.toList)))

  private def run(projectId: UUID, paths: IO[Either[TgJsonFailure, List[String]]]): Unit = {
    val program: IO[(Int, List[DataImportResp], List[TgJsonFailure])] =
      paths.flatMap {
        case Left(failure) => IO.pure((0, Nil, List(failure)))
        case Right(importPaths) =>
          importPaths
            .traverse(path => dataExportService.importData(projectId, path).map(_.map(_.body.get)))
            .map(outcomes => (outcomes.size, outcomes.collect { case Right(dto) => dto }, outcomes.collect { case Left(f) => f }))
      }

    spinnerService
      .runWithSpinner(program)
      .map { case (pathCount, successes, failures) =>
        failures.foreach(toast.error)
        if (failures.isEmpty && successes.nonEmpty)
          toast.success(s"Imported from ${successes.size} path(s), ${successes.map(_.results.size).sum} entity(ies) total")
        else if (failures.nonEmpty && successes.nonEmpty)
          toast.error(s"Imported from ${successes.size}/$pathCount path(s); ${failures.size} failed")
        modalFactory.previewModal(content(pathCount, successes.flatMap(_.results.toList), failures.size), "Import results")
      }
      .unsafeRunAndForget()
  }

  private def content(pathCount: Int, results: List[com.togger.dto.DataImportEntityResultResp], failedCount: Int) =
    if (results.nonEmpty) div(cls("p-4"), new ImportResultsComponent(results).element)
    else
      div(
        cls("p-4 text-body"),
        if (failedCount > 0) s"Nothing was imported: $failedCount of $pathCount import path(s) failed"
        else if (pathCount == 0) "No import paths configured for this project"
        else "Nothing to import from the configured path(s)"
      )

}
