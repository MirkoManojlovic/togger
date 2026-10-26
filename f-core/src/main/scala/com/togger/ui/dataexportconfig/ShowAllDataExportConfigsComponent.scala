package com.togger.ui.dataexportconfig

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto._
import com.togger.dto.dataexportconfig.DataExportConfigResp
import com.togger.routes.Pages.{CreateDataExportConfigPage, EditDataExportConfigPage}
import com.togger.routes.{FrontendPage, ListParams, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskConfigTableSupport, DataExportFeService, TgDateFormater}
import com.togger.ui.toast.Toast

import java.util.UUID

class ShowAllDataExportConfigsComponent(
  dataExportConfigService: DataExportConfigFeService,
  dataExportService: DataExportFeService,
  tgDateFormater: TgDateFormater,
  router: TgRouter[FrontendPage],
  modalFactory: ModalFactory,
  spinnerService: SpinnerService,
  val toast: Toast,
  projectId: UUID,
  params: ListParams,
  showPage: (Int, Int) => Unit
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  import TaskConfigTableSupport._

  private val stateFormVar = Var(Seq.empty[DataExportConfigResp])
  private val statusResultVar = Var(Seq.empty[DataExportPathStatusResp])

  private val paginationVar = Var(Option.empty[PaginationState])

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    dataExportConfigService.findByProjectId(projectId, params.pageRequest).map {
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

  private def statusPanel(pathStatus: DataExportPathStatusResp) =
    exportPathStatusCard(pathStatus, s"Export status: ${pathStatus.exportPath}")

  private def exportAll(): Unit =
    runAction(dataExportService.exportData(projectId)) {
      _.toastMessageDto(dto =>
        s"Data exported to ${dto.results.count(_.success)}/${dto.results.size} configured path(s)"
      )(_ => IO.unit)
    }

  private def checkAllStatuses(): Unit =
    runAction(dataExportService.checkStatus(projectId)) {
      _.toastJustErrorsUnit(resp => statusResultVar.set(resp.pathStatuses))
    }

  private def deleteConfig(dto: DataExportConfigResp): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete data export config '${dto.name}'?"),
      title = "Delete data export config",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      runAction(dataExportConfigService.deleteById(dto.id)) {
        _.toastBackendMessageCodeBlock {
          reload()
        }
      }
      close()
    }

  private def exportByConfig(dto: DataExportConfigResp): Unit =
    runAction(dataExportService.exportByConfig(dto.id)) {
      _.toastJustErrorsUnit { resp =>
        resp.results.headOption.flatMap(_.error) match {
          case Some(err) => toast.error(s"Export to ${dto.exportPath} failed: $err")
          case None      => toast.success(s"Exported ${resp.entityCounts.map(_.count).sum} entities to ${dto.exportPath}")
        }
      }
    }

  private def checkStatusByConfig(dto: DataExportConfigResp): Unit =
    runAction(dataExportService.checkStatusByConfig(dto.id)) {
      _.toastJustErrorsUnit(resp => statusResultVar.set(resp.pathStatuses))
    }

  private def configRow(dto: DataExportConfigResp) =
    tr(
      cls(rowCls),
      createTd(dto.name),
      createTd(dto.exportPath),
      createTd(tgDateFormater.yearMonthDayTimeFormat(dto.updatedAt)),
      createTd(
        div(
          iconButton(
            "Edit",
            TgIcons.editIcon(className = "w-4 h-4"),
            rowIconButtonCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => router.route(EditDataExportConfigPage(projectId, dto.id)))
          ),
          iconButton(
            "Delete",
            TgIcons.trashIcon(className = "w-4 h-4"),
            rowIconButtonDangerCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => deleteConfig(dto))
          ),
          iconButton(
            "Export",
            TgIcons.downloadIcon(className = "w-4 h-4"),
            rowIconButtonCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => exportByConfig(dto))
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
              div("Export data configs"),
              div(
                iconButton(
                  "Export all",
                  TgIcons.downloadIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  headerButtonMarginCls,
                  onClick.mapToUnit --> (_ => exportAll())
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
                  router.navigateTo(CreateDataExportConfigPage(projectId))
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
                      tr(Seq("name", "export path", "exported at", "Action").map(createTh))
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
