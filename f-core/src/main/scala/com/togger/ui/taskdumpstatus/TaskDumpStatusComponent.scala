package com.togger.ui.taskdumpstatus

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.dto.{TaskDumpPathStatusResp, TaskDumpSyncStatusResp, TaskDumpTaskStatusResp}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskDumpFeService
import com.togger.ui.toast.Toast
import org.scalajs.dom.HTMLTableCellElement

import java.util.UUID

class TaskDumpStatusComponent(
  taskDumpService: TaskDumpFeService,
  spinnerService: SpinnerService,
  val toast: Toast,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  private val stateVar = Var(Seq.empty[TaskDumpPathStatusResp])

  spinnerService
    .runWithSpinner(taskDumpService.checkStatus(projectId))
    .toastJustErrorsUnit(resp => stateVar.set(resp.pathStatuses))
    .unsafeRunAndForget()

  def createTh(content: String) =
    th(cls("px-6 py-3 font-medium"), content)

  def createTd(content: Modifier[ReactiveHtmlElement[HTMLTableCellElement]]) =
    td(cls("px-6 py-4"), content)

  private def statusBadgeCls(status: TaskDumpSyncStatusResp): String = status match {
    case TaskDumpSyncStatusResp.InSync          => "bg-green-100 text-green-800"
    case TaskDumpSyncStatusResp.OutOfSync       => "bg-yellow-100 text-yellow-800"
    case TaskDumpSyncStatusResp.MissingFromDump => "bg-red-100 text-red-800"
    case TaskDumpSyncStatusResp.MissingFromDb   => "bg-red-100 text-red-800"
    case TaskDumpSyncStatusResp.Malformed       => "bg-red-100 text-red-800"
  }

  private def statusBadge(status: TaskDumpSyncStatusResp) =
    span(
      cls(s"px-2 py-1 rounded-base text-xs font-medium ${statusBadgeCls(status)}"),
      status.entryName
    )

  private def pathCard(pathStatus: TaskDumpPathStatusResp) =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(div(pathStatus.dumpPath), TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
            table(
              cls("w-full text-sm text-left rtl:text-right text-body"),
              thead(
                cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                tr(
                  createTh("task id"),
                  createTh("status"),
                  createTh("details")
                )
              ),
              tbody(
                pathStatus.taskStatuses
                  .sortBy(_.taskId)
                  .map { (taskStatus: TaskDumpTaskStatusResp) =>
                    tr(
                      cls("odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"),
                      createTd(taskStatus.taskId.toString),
                      createTd(statusBadge(taskStatus.status)),
                      createTd(taskStatus.differences.mkString(", "))
                    )
                  }
              )
            )
          )
        )
      )
      .element

  val element = {
    div(
      child <-- stateVar.signal.map(pathStatuses => div(pathStatuses.map(pathCard)))
    )
  }

}
