package com.togger.ui.dataimportconfig

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.dto.dataimportconfig.DataImportConfigResp
import com.togger.dto.{DataImportEntityResultResp, DataImportResp, DataImportStatusResp}
import com.togger.routes.Pages.{CreateDataImportConfigPage, EditDataImportConfigPage}
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

class ShowAllDataImportConfigsComponent(
  dataImportConfigService: DataImportConfigFeService,
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

  private val stateFormVar = Var(Seq.empty[DataImportConfigResp])

  private val paginationVar = Var(Option.empty[PaginationState])

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    dataImportConfigService.findByProjectId(projectId, params.pageRequest).map {
      case Left(failure) => toast.error(failure)
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

  private val importFlow = new ImportFlow(dataExportService, dataImportConfigService, spinnerService, modalFactory, toast)

  private def importFrom(importPath: String, name: String): Unit = importFlow.importFrom(projectId, importPath, Some(name))

  private def loadAll(): Unit = importFlow.importAll(projectId)

  private def deleteConfig(dto: DataImportConfigResp): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete data import config '${dto.name}'?"),
      title = "Delete data import config",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(dataImportConfigService.deleteById(dto.id))
        .toastBackendMessageCodeBlock {
          reload()
        }
        .unsafeRunAndForget()
      close()
    }

  private def configRow(dto: DataImportConfigResp) =
    tr(
      cls(rowCls),
      createTd(dto.name),
      createTd(dto.importPath),
      createTd(tgDateFormater.yearMonthDayTimeFormat(dto.updatedAt)),
      createTd(
        div(
          iconButton(
            "Edit",
            TgIcons.editIcon(className = "w-4 h-4"),
            rowIconButtonCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => router.route(EditDataImportConfigPage(projectId, dto.id)))
          ),
          iconButton(
            "Delete",
            TgIcons.trashIcon(className = "w-4 h-4"),
            rowIconButtonDangerCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => deleteConfig(dto))
          ),
          iconButton(
            "Import",
            TgIcons.uploadIcon(className = "w-4 h-4"),
            rowIconButtonCls,
            lastRowButtonMarginCls,
            onClick.mapToUnit --> (_ => importFrom(dto.importPath, dto.name))
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
              div("Import data configs"),
              div(
                iconButton(
                  "Import all",
                  TgIcons.uploadIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  headerButtonMarginCls,
                  onClick.mapToUnit --> (_ => loadAll())
                ),
                iconButton(
                  "Create",
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "",
                  router.navigateTo(CreateDataImportConfigPage(projectId))
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
                      tr(Seq("name", "import path", "imported at", "Action").map(createTh))
                    ),
                    tbody(dtos.map(configRow))
                  )
                )
              ),
              TgPaginationComponent(paginationVar.signal, showPage)
            )
          )
        )
        .element
    )
  }

}
