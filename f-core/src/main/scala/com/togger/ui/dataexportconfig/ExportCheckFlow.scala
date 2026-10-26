package com.togger.ui.dataexportconfig

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.{DataExportResp, DataExportStatusResp}
import com.togger.ui.modal.ModalFactory
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.DataExportFeService
import com.togger.ui.toast.Toast

/** Every export starts with a look at what it would change: the check opens in a modal, and its export button runs the
  * export and swaps the modal for the results.
  */
class ExportCheckFlow(
  dataExportService: DataExportFeService,
  spinnerService: SpinnerService,
  modalFactory: ModalFactory,
  toast: Toast
)(implicit
  ioRuntime: IORuntime
) {

  def start(check: TgRespIO[DataExportStatusResp], export: TgRespIO[DataExportResp]): Unit =
    spinnerService
      .runWithSpinner(check)
      .flatMap(
        _.fold(
          error => IO(toast.error(error)),
          success => IO(showCheck(success.body.get, export))
        )
      )
      .unsafeRunAndForget()

  private def showCheck(status: DataExportStatusResp, export: TgRespIO[DataExportResp]): Unit =
    showModal("Export check")(
      new ExportCheckComponent(
        status.pathStatuses,
        onAll = _ => runExport(export),
        onPath = path =>
          path.exportConfigId match {
            case Some(configId) => runExport(dataExportService.exportByConfig(configId))
            case None           => toast.error(s"Cannot export to ${path.exportPath}: its export config is unknown")
          }
      ).element
    )

  private def runExport(export: TgRespIO[DataExportResp]): Unit =
    spinnerService
      .runWithSpinner(export)
      .flatMap(
        _.fold(
          error => IO(toast.error(error)),
          success =>
            IO {
              val dto = success.body.get
              toast.success(s"Data exported to ${dto.results.count(_.success)}/${dto.results.size} configured path(s)")
              showModal("Export results")(new ExportResultsComponent(dto).element)
            }
        )
      )
      .unsafeRunAndForget()

  /** An error building the modal would be lost in the IO that runs it, so it is shown as a toast. */
  private def showModal(title: String)(content: => com.raquo.laminar.api.L.HtmlElement): Unit =
    scala.util.Try(modalFactory.previewModal(content, title)).failed.foreach { error =>
      toast.error(s"Could not show the ${title.toLowerCase}: ${error.getMessage}")
    }

}
