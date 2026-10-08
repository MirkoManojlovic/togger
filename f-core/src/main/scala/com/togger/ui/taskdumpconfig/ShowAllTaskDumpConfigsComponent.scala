package com.togger.ui.taskdumpconfig

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto._
import com.togger.dto.taskdumpconfig.TaskDumpConfigResp
import com.togger.routes.Pages.{CreateTaskDumpConfigPage, EditTaskDumpConfigPage}
import com.togger.routes.{FrontendPage, ListParams, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskConfigTableSupport, TaskDumpFeService, TgDateFormater}
import com.togger.ui.toast.Toast

import java.util.UUID

class ShowAllTaskDumpConfigsComponent(
  taskDumpConfigService: TaskDumpConfigFeService,
  taskDumpService: TaskDumpFeService,
  tgDateFormater: TgDateFormater,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  val toast: Toast,
  projectId: UUID,
  params: ListParams,
  showPage: (Int, Int) => Unit
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  import TaskConfigTableSupport._

  private val stateFormVar = Var(Seq.empty[TaskDumpConfigResp])
  private val statusResultVar = Var(Seq.empty[TaskDumpPathStatusResp])

  private val paginationVar = Var(Option.empty[PaginationState])

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    taskDumpConfigService.findByProjectId(projectId, params.pageRequest).map {
      case Left(failure) => println(failure)
      case Right(success) =>
        val response = success.body.get
        if (response.items.isEmpty && response.totalNumberOfPages > 0 && params.page > response.totalNumberOfPages)
          showPage(response.totalNumberOfPages.toInt, params.size)
        else {
          stateFormVar.set(response.items)
          paginationVar.set(Some(PaginationState.fromResponse(response)))
        }
    }

  private def reload(): Unit =
    spinnerService.runWithSpinner(loadPage()).unsafeRunAndForget()

  reload()

  private def runAction[A](io: IO[A])(handle: IO[A] => IO[Unit]): Unit =
    handle(spinnerService.runWithSpinner(io)).unsafeRunAndForget()

  private def statusPanel(pathStatus: TaskDumpPathStatusResp) =
    dumpPathStatusCard(pathStatus, s"Dump status: ${pathStatus.dumpPath}")

  private def dumpAll(): Unit =
    runAction(taskDumpService.dumpTasks(projectId)) {
      _.toastMessageDto(dto =>
        s"Tasks dumped to ${dto.results.count(_.success)}/${dto.results.size} configured path(s)"
      )(_ => IO.unit)
    }

  private def checkAllStatuses(): Unit =
    runAction(taskDumpService.checkStatus(projectId)) {
      _.toastJustErrorsUnit(resp => statusResultVar.set(resp.pathStatuses))
    }

  private def deleteConfig(dto: TaskDumpConfigResp): Unit =
    runAction(taskDumpConfigService.deleteById(dto.id)) {
      _.toastBackendMessageCodeBlock {
        reload()
      }
    }

  private def dumpByConfig(dto: TaskDumpConfigResp): Unit =
    runAction(taskDumpService.dumpByConfig(dto.id)) {
      _.toastJustErrorsUnit { resp =>
        resp.results.headOption.flatMap(_.error) match {
          case Some(err) => toast.error(s"Dump to ${dto.dumpPath} failed: $err")
          case None      => toast.success(s"Dumped ${resp.taskCount} tasks to ${dto.dumpPath}")
        }
      }
    }

  private def checkStatusByConfig(dto: TaskDumpConfigResp): Unit =
    runAction(taskDumpService.checkStatusByConfig(dto.id)) {
      _.toastJustErrorsUnit(resp => statusResultVar.set(resp.pathStatuses))
    }

  private def configRow(dto: TaskDumpConfigResp) =
    tr(
      cls(rowCls),
      createTd(dto.name),
      createTd(dto.dumpPath),
      createTd(tgDateFormater.yearMonthDayTimeFormat(dto.updatedAt)),
      createTd(
        div(
          iconButton(
            "Edit",
            TgIcons.editIcon(className = "w-4 h-4"),
            rowIconButtonCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => router.route(EditTaskDumpConfigPage(dto.id)))
          ),
          iconButton(
            "Delete",
            TgIcons.trashIcon(className = "w-4 h-4"),
            rowIconButtonDangerCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => deleteConfig(dto))
          ),
          iconButton(
            "Dump",
            TgIcons.downloadIcon(className = "w-4 h-4"),
            rowIconButtonCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => dumpByConfig(dto))
          ),
          iconButton(
            "Check status",
            TgIcons.checkIcon(className = "w-4 h-4"),
            rowIconButtonCls,
            lastRowButtonMarginCls,
            onClick.mapToUnit --> (_ => checkStatusByConfig(dto))
          )
        )
      )
    )

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Task dump configs"),
              div(
                iconButton(
                  "Dump all",
                  TgIcons.downloadIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  headerButtonMarginCls,
                  onClick.mapToUnit --> (_ => dumpAll())
                ),
                iconButton(
                  "Check all statuses",
                  TgIcons.checkIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  headerButtonMarginCls,
                  onClick.mapToUnit --> (_ => checkAllStatuses())
                ),
                iconButton(
                  "Create",
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "",
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
                  cls(tableWrapperCls),
                  table(
                    cls(tableCls),
                    thead(
                      cls(theadCls),
                      tr(Seq("name", "dump path", "dumped at", "Action").map(createTh))
                    ),
                    tbody(dtos.map(configRow))
                  )
                )
              ),
              TgPaginationComponent(paginationVar.signal, showPage)
            )
          )
        )
        .element,
      div(cls("mt-4")),
      children <-- statusResultVar.signal.map(_.map(statusPanel))
    )
  }

}
