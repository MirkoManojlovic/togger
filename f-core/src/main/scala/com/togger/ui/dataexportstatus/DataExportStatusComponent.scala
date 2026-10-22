package com.togger.ui.dataexportstatus

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.DataExportPathStatusResp
import com.togger.toast.ToastMessages
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskConfigTableSupport, DataExportFeService}
import com.togger.ui.toast.Toast

import java.util.UUID

class DataExportStatusComponent(
  dataExportService: DataExportFeService,
  spinnerService: SpinnerService,
  val toast: Toast,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  import TaskConfigTableSupport._

  private val stateVar = Var(Seq.empty[DataExportPathStatusResp])

  spinnerService
    .runWithSpinner(dataExportService.checkStatus(projectId))
    .toastJustErrorsUnit(resp => stateVar.set(resp.pathStatuses))
    .unsafeRunAndForget()

  private def pathCard(pathStatus: DataExportPathStatusResp) =
    exportPathStatusCard(pathStatus, pathStatus.exportPath)

  val element = {
    div(
      child <-- stateVar.signal.map(pathStatuses => div(pathStatuses.map(pathCard)))
    )
  }

}
