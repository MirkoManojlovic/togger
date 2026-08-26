package com.togger.ui.taskdumpconfig

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.dto.taskdumpconfig.TaskDumpConfigResp
import com.togger.dto._
import com.togger.routes.Pages.{CreateTaskDumpConfigPage, EditTaskDumpConfigPage, ShowAllTaskConfigsPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskDumpFeService, TgDateFormater}
import com.togger.ui.toast.Toast
import org.scalajs.dom.HTMLTableCellElement

import java.util.UUID

class ShowAllTaskDumpConfigsComponent(
  taskDumpConfigService: TaskDumpConfigFeService,
  taskDumpService: TaskDumpFeService,
  tgDateFormater: TgDateFormater,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  val toast: Toast,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  private val stateFormVar = Var(Seq.empty[TaskDumpConfigResp])
  private val statusResultVar = Var(Seq.empty[TaskDumpPathStatusResp])
  private val loadResultsVar = Var(Seq.empty[TaskLoadTaskResultResp])

  spinnerService
    .runWithSpinner(
      taskDumpConfigService
        .findByProjectId(projectId)
        .map(response => response.map(success => stateFormVar.set(success.body.get)))
    )
    .unsafeRunAndForget()

  def createTh(content: String) =
    th(cls("px-2 py-3 font-medium"), content)

  def createTd(content: Modifier[ReactiveHtmlElement[HTMLTableCellElement]]) =
    td(cls("px-2 py-4"), content)

  private val headerIconButtonCls =
    "inline-flex items-center justify-center w-8 h-8 text-fg-brand bg-neutral-primary border border-brand hover:bg-brand hover:text-white focus:ring-4 focus:ring-brand-subtle rounded-base focus:outline-none"

  private val rowIconButtonCls =
    "inline-flex items-center justify-center w-8 h-8 text-white bg-brand hover:bg-brand-strong box-border border border-transparent focus:ring-4 focus:ring-brand-medium shadow-xs rounded-base focus:outline-none"

  private val rowIconButtonDangerCls =
    "inline-flex items-center justify-center w-8 h-8 text-white bg-red-600 hover:bg-red-700 box-border border border-transparent focus:ring-4 focus:ring-red-300 shadow-xs rounded-base focus:outline-none"

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

  private def statusPanel(pathStatus: TaskDumpPathStatusResp) =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(div(s"Dump status: ${pathStatus.dumpPath}"), TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
            table(
              cls("w-full text-sm text-left rtl:text-right text-body"),
              thead(
                cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                tr(
                  createTh("task id"),
                  createTh("task name"),
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
                      createTd(taskStatus.taskName.getOrElse("-"): String),
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

  private def loadStatusBadgeCls(status: TaskLoadStatusResp): String = status match {
    case TaskLoadStatusResp.Inserted  => "bg-green-100 text-green-800"
    case TaskLoadStatusResp.Merged    => "bg-blue-100 text-blue-800"
    case TaskLoadStatusResp.Unchanged => "bg-gray-100 text-gray-800"
    case TaskLoadStatusResp.Malformed => "bg-red-100 text-red-800"
    case TaskLoadStatusResp.Failed    => "bg-red-100 text-red-800"
  }

  private def loadStatusBadge(status: TaskLoadStatusResp) =
    span(
      cls(s"px-2 py-1 rounded-base text-xs font-medium ${loadStatusBadgeCls(status)}"),
      status.entryName
    )

  private def loadResultsTable(results: Seq[TaskLoadTaskResultResp]) =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Load results", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
            table(
              cls("w-full text-sm text-left rtl:text-right text-body"),
              thead(
                cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                tr(
                  createTh("task id"),
                  createTh("task name"),
                  createTh("status"),
                  createTh("versions"),
                  createTh("links"),
                  createTh("log entries"),
                  createTh("error")
                )
              ),
              tbody(
                results
                  .sortBy(_.taskId)
                  .map { (r: TaskLoadTaskResultResp) =>
                    tr(
                      cls("odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"),
                      createTd(r.taskId.toString),
                      createTd(r.taskName.getOrElse("-"): String),
                      createTd(loadStatusBadge(r.status)),
                      createTd(r.versionsAdded.toString),
                      createTd(r.linksAdded.toString),
                      createTd(r.logEntriesAdded.toString),
                      createTd(r.error.getOrElse("-"): String)
                    )
                  }
              )
            )
          )
        )
      )
      .element

  private def dumpAll(): Unit =
    spinnerService
      .runWithSpinner(taskDumpService.dumpTasks(projectId))
      .toastMessageDto(dto =>
        s"Tasks dumped to ${dto.results.count(_.success)}/${dto.results.size} configured path(s)"
      )(_ => IO.unit)
      .unsafeRunAndForget()

  private def checkAllStatuses(): Unit =
    spinnerService
      .runWithSpinner(taskDumpService.checkStatus(projectId))
      .toastJustErrorsUnit(resp => statusResultVar.set(resp.pathStatuses))
      .unsafeRunAndForget()

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Task dump configs"),
              div(
                button(
                  `type`("button"),
                  title("Dump all"),
                  TgIcons.downloadIcon(className = "w-4 h-4"),
                  cls(headerIconButtonCls),
                  cls("mr-2"),
                  onClick.mapToUnit --> (_ => dumpAll())
                ),
                button(
                  `type`("button"),
                  title("Check all statuses"),
                  TgIcons.checkIcon(className = "w-4 h-4"),
                  cls(headerIconButtonCls),
                  cls("mr-2"),
                  onClick.mapToUnit --> (_ => checkAllStatuses())
                ),
                button(
                  `type`("button"),
                  title("Create"),
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  cls(headerIconButtonCls),
                  router.navigateTo(CreateTaskDumpConfigPage(projectId))
                )
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard.HtmlCardContent(
            div(
              child <-- stateFormVar.signal.map(dtos =>
                div(
                  cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
                  table(
                    cls("w-full text-sm text-left rtl:text-right text-body"),
                    thead(
                      cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                      tr(
//                        createTh("id"),
                        createTh("name"),
                        createTh("dump path"),
                        createTh("dumped at"),
                        createTh("Action")
                      )
                    ),
                    tbody(
                      dtos
                        .sortBy(_.id)
                        .zipWithIndex
                        .map { case (dto, index) =>
                          val rowCls = if (index % 2 == 0) {
                            "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"
                          } else {
                            "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"
                          }

                          tr(
                            cls(rowCls),
//                            createTd(dto.id.toString),
                            createTd(dto.name),
                            createTd(dto.dumpPath),
                            createTd(tgDateFormater.yearMonthDayTimeFormat(dto.updatedAt)),
                            createTd(
                              div(
                                button(
                                  `type`("button"),
                                  title("Edit"),
                                  TgIcons.editIcon(className = "w-4 h-4"),
                                  cls(rowIconButtonCls),
                                  cls("mr-2 mt-1 mb-1"),
                                  onClick.mapToUnit --> (_ => router.route(EditTaskDumpConfigPage(dto.id)))
                                ),
                                button(
                                  `type`("button"),
                                  title("Delete"),
                                  TgIcons.trashIcon(className = "w-4 h-4"),
                                  cls(rowIconButtonDangerCls),
                                  cls("mr-2 mt-1 mb-1"),
                                  onClick.mapToUnit --> (_ =>
                                    spinnerService
                                      .runWithSpinner(taskDumpConfigService.deleteById(dto.id))
                                      .toastBackendMessageCodeBlock {
                                        router.route(ShowAllTaskConfigsPage(projectId))
                                      }
                                      .unsafeRunAndForget()
                                  )
                                ),
                                button(
                                  `type`("button"),
                                  title("Dump"),
                                  TgIcons.downloadIcon(className = "w-4 h-4"),
                                  cls(rowIconButtonCls),
                                  cls("mr-2 mt-1 mb-1"),
                                  onClick.mapToUnit --> (_ =>
                                    spinnerService
                                      .runWithSpinner(taskDumpService.dumpByConfig(dto.id))
                                      .toastJustErrorsUnit { resp =>
                                        resp.results.headOption.flatMap(_.error) match {
                                          case Some(err) => toast.error(s"Dump to ${dto.dumpPath} failed: $err")
                                          case None =>
                                            toast.success(s"Dumped ${resp.taskCount} tasks to ${dto.dumpPath}")
                                        }
                                      }
                                      .unsafeRunAndForget()
                                  )
                                ),
                                button(
                                  `type`("button"),
                                  title("Check status"),
                                  TgIcons.checkIcon(className = "w-4 h-4"),
                                  cls(rowIconButtonCls),
                                  cls("mt-1 mb-1"),
                                  onClick.mapToUnit --> (_ =>
                                    spinnerService
                                      .runWithSpinner(taskDumpService.checkStatusByConfig(dto.id))
                                      .toastJustErrorsUnit(resp => statusResultVar.set(resp.pathStatuses))
                                      .unsafeRunAndForget()
                                  )
                                )
                              )
                            )
                          )
                        }
                    )
                  )
                )
              )
            )
          )
        )
        .element,
      div(cls("mt-4")),
      children <-- statusResultVar.signal.map(_.map(statusPanel)),
      div(
        cls("mt-4"),
        child.maybe <-- loadResultsVar.signal.map(results =>
          if (results.isEmpty) None else Some(loadResultsTable(results))
        )
      )
    )
  }

}
