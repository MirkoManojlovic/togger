package com.togger.ui.dataimportconfig

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.PageRequest
import com.togger.dto.dataimportconfig.DataImportConfigResp
import com.togger.dto.{DataImportEntityResultResp, DataImportResp, DataImportStatusResp}
import com.togger.routes.Pages.{CreateDataImportConfigPage, EditDataImportConfigPage}
import com.togger.routes.{FrontendPage, ListParams, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.components.{TgBadge, TgBadgeColor}
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
  private val resultsVar = Var(Seq.empty[DataImportEntityResultResp])
  private val resultsPageVar = Var((PageRequest.DefaultPage, PageRequest.DefaultSize))
  private val statusFilterVar = Var(Option.empty[DataImportStatusResp])

  private val paginationVar = Var(Option.empty[PaginationState])

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    dataImportConfigService.findByProjectId(projectId, params.pageRequest).map {
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

  private def summaryMessage(dto: DataImportResp): String = {
    val counts = dto.results.groupBy(_.status).view.mapValues(_.size).toMap
    s"Imported ${dto.results.size} entity(ies): " +
      s"${counts.getOrElse(DataImportStatusResp.Inserted, 0)} inserted, " +
      s"${counts.getOrElse(DataImportStatusResp.Merged, 0)} merged, " +
      s"${counts.getOrElse(DataImportStatusResp.Unchanged, 0)} unchanged, " +
      s"${counts.getOrElse(DataImportStatusResp.Malformed, 0) + counts.getOrElse(DataImportStatusResp.Failed, 0)} failed"
  }

  private def showResults(results: Seq[DataImportEntityResultResp]): Unit = {
    resultsPageVar.update { case (_, size) => (1, size) }
    statusFilterVar.set(None)
    resultsVar.set(results.sortBy(r => (r.entityType.entryName, r.entityId)))
  }

  private def handleImportResponse(dto: DataImportResp): Unit = {
    showResults(dto.results)
    toast.success(summaryMessage(dto))
  }

  private def importFrom(importPath: String): Unit =
    spinnerService
      .runWithSpinner(dataExportService.importData(projectId, importPath))
      .toastJustErrors(())(handleImportResponse)
      .unsafeRunAndForget()

  /** "Import all" loads from every config of the project, not only the ones on the page on screen. */
  private def loadAll(): Unit = {
    val program: IO[List[Either[com.togger.common.TgJsonFailure, DataImportResp]]] =
      dataImportConfigService.findAllByProjectId(projectId).flatMap {
        case Left(failure) => IO.pure(List(failure.asLeft[DataImportResp]))
        case Right(success) =>
          val paths = success.body.get.map(_.importPath).distinct.toList
          paths.traverse(path => dataExportService.importData(projectId, path).map(_.map(_.body.get)))
      }

    spinnerService
      .runWithSpinner(program)
      .map { outcomes =>
        val successes = outcomes.collect { case Right(dto) => dto }
        showResults(successes.flatMap(_.results))
        outcomes.collect { case Left(failure) => failure }.foreach(toast.error)
        val totalEntities = successes.map(_.results.size).sum
        val failedCount = outcomes.count(_.isLeft)
        if (failedCount == 0) toast.success(s"Imported from ${successes.size} path(s), $totalEntities entity(ies) total")
        else toast.error(s"Imported from ${successes.size}/${outcomes.size} path(s); $failedCount failed")
      }
      .unsafeRunAndForget()
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
            onClick.mapToUnit --> (_ => router.route(EditDataImportConfigPage(dto.id)))
          ),
          iconButton(
            "Delete",
            TgIcons.trashIcon(className = "w-4 h-4"),
            rowIconButtonDangerCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ =>
              spinnerService
                .runWithSpinner(dataImportConfigService.deleteById(dto.id))
                .toastBackendMessageCodeBlock {
                  reload()
                }
                .unsafeRunAndForget()
            )
          ),
          iconButton(
            "Import",
            TgIcons.uploadIcon(className = "w-4 h-4"),
            rowIconButtonCls,
            lastRowButtonMarginCls,
            onClick.mapToUnit --> (_ => importFrom(dto.importPath))
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
        .element,
      div(
        cls("mt-4"),
        child.maybe <-- resultsVar.signal.map(results => if (results.isEmpty) None else Some(importResultsTable()))
      )
    )
  }

  /** The requested page clamped to the results on screen, with the slice of results it shows. */
  private val resultsPageSignal: Signal[(PaginationState, Seq[DataImportEntityResultResp])] =
    resultsVar.signal.combineWith(statusFilterVar.signal, resultsPageVar.signal).map {
      case (allResults, statusFilter, (page, size)) =>
      val results = statusFilter.fold(allResults)(status => allResults.filter(_.status == status))
      val totalPages = math.max(1, (results.size + size - 1) / size)
      val current = math.min(page, totalPages)
      val slice = results.slice((current - 1) * size, current * size)
      (PaginationState(current, size, results.size.toLong, totalPages, slice.size), slice)
    }

  private def importResultsTable() =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Import results", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            child <-- resultsVar.signal.combineWith(statusFilterVar.signal).map { case (results, filter) =>
              importSummary(results, filter)
            },
            div(
              cls(tableWrapperCls),
              table(
                cls(tableCls),
                thead(
                  cls(theadCls),
                  tr(Seq("type", "id", "name", "status", "versions", "children", "error").map(createTh))
                ),
                tbody(children <-- resultsPageSignal.map { case (_, slice) => slice.map(resultRow) })
              )
            ),
            TgPaginationComponent(resultsPageSignal.map { case (state, _) => Some(state) }, (page, size) => resultsPageVar.set((page, size)))
          )
        )
      )
      .element

  private def toggleStatusFilter(status: DataImportStatusResp): Unit = {
    statusFilterVar.update(current => if (current.contains(status)) None else Some(status))
    resultsPageVar.update { case (_, size) => (1, size) }
  }

  private def importSummary(results: Seq[DataImportEntityResultResp], activeFilter: Option[DataImportStatusResp]) = {
    val counts = results.groupBy(_.status).view.mapValues(_.size).toMap
    div(
      cls("flex flex-wrap items-center gap-2 mb-4 text-sm text-body"),
      span(cls("font-semibold text-heading"), s"Total rows: ${results.size}"),
      DataImportStatusResp.values.map { status =>
        val isActive = activeFilter.contains(status)
        button(
          typ("button"),
          cls(
            "inline-flex items-center gap-1 px-1 rounded-base focus:outline-none focus:ring-2 focus:ring-brand " +
              (if (isActive) "ring-2 ring-brand" else "opacity-80 hover:opacity-100")
          ),
          aria.pressed(isActive.toString),
          title(if (isActive) "Click to show all results" else s"Show only ${status.entryName} results"),
          importStatusBadge(status),
          counts.getOrElse(status, 0).toString,
          onClick --> (_ => toggleStatusFilter(status))
        )
      }
    )
  }

  private def resultRow(r: DataImportEntityResultResp) =
    tr(
      cls(rowCls),
      createTd(r.entityType.entryName),
      createTd(r.entityId.toString),
      createTd(r.entityName.getOrElse("-"): String),
      createTd(importStatusBadge(r.status)),
      createTd(r.versionsAdded.toString),
      createTd(r.childrenAdded.toString),
      createTd(r.error.getOrElse("-"): String)
    )

  private def importStatusBadgeColor(status: DataImportStatusResp): TgBadgeColor = status match {
    case DataImportStatusResp.Inserted  => TgBadgeColor.Green
    case DataImportStatusResp.Merged    => TgBadgeColor.Blue
    case DataImportStatusResp.Unchanged => TgBadgeColor.Dark
    case DataImportStatusResp.Malformed => TgBadgeColor.Red
    case DataImportStatusResp.Failed    => TgBadgeColor.Red
  }

  private def importStatusBadge(status: DataImportStatusResp) =
    TgBadge(status.entryName, importStatusBadgeColor(status)).element

}
