package com.togger.ui.taskdumpstatus

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.TaskDumpPathStatusResp
import com.togger.toast.ToastMessages
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskConfigTableSupport, TaskDumpFeService}
import com.togger.ui.toast.Toast

import java.util.UUID

class TaskDumpStatusComponent(
  taskDumpService: TaskDumpFeService,
  spinnerService: SpinnerService,
  val toast: Toast,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  import TaskConfigTableSupport._

  private val stateVar = Var(Seq.empty[TaskDumpPathStatusResp])

  spinnerService
    .runWithSpinner(taskDumpService.checkStatus(projectId))
    .toastJustErrorsUnit(resp => stateVar.set(resp.pathStatuses))
    .unsafeRunAndForget()

  private def pathCard(pathStatus: TaskDumpPathStatusResp) =
    dumpPathStatusCard(pathStatus, pathStatus.dumpPath)

  val element = {
    div(
      child <-- stateVar.signal.map(pathStatuses => div(pathStatuses.map(pathCard)))
    )
  }

}
