package com.togger.ui.dataimportconfig

import com.raquo.laminar.api.L._
import com.togger.common.PageRequest
import com.togger.dto.{DataImportEntityResultResp, DataImportStatusResp}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.components.{TgBadge, TgBadgeColor, TgPaginationComponent}
import com.togger.ui.task.TaskConfigTableSupport

/** The entities an import went through: a summary of the statuses that doubles as a filter, then the entities a page
  * at a time.
  */
class ImportResultsComponent(results: Seq[DataImportEntityResultResp]) {

  import TaskConfigTableSupport._

  private val sortedResults = results.sortBy(r => (r.entityType.entryName, r.entityId))
  private val pageVar = Var((PageRequest.DefaultPage, PageRequest.DefaultSize))
  private val statusFilterVar = Var(Option.empty[DataImportStatusResp])

  /** The requested page clamped to the results on screen, with the slice of results it shows. */
  private val pageSignal: Signal[(PaginationState, Seq[DataImportEntityResultResp])] =
    pageVar.signal.combineWith(statusFilterVar.signal).map { case (page, size, statusFilter) =>
      val filtered = statusFilter.fold(sortedResults)(status => sortedResults.filter(_.status == status))
      val totalPages = math.max(1, (filtered.size + size - 1) / size)
      val current = math.min(page, totalPages)
      val slice = filtered.slice((current - 1) * size, current * size)
      (PaginationState(current, size, filtered.size.toLong, totalPages, slice.size), slice)
    }

  private def toggleStatusFilter(status: DataImportStatusResp): Unit = {
    statusFilterVar.update(current => if (current.contains(status)) None else Some(status))
    pageVar.update { case (_, size) => (PageRequest.DefaultPage, size) }
  }

  private val counts = sortedResults.groupBy(_.status).view.mapValues(_.size).toMap

  private def summary(activeFilter: Option[DataImportStatusResp]) =
    div(
      cls("flex flex-wrap items-center gap-2 mb-4 text-sm text-body"),
      span(cls("font-semibold text-heading"), s"Total rows: ${sortedResults.size}"),
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

  private val table =
    div(
      child <-- statusFilterVar.signal.map(summary),
      div(
        cls(tableWrapperCls),
        com.raquo.laminar.api.L.table(
          cls(tableCls),
          thead(
            cls(theadCls),
            tr(Seq("type", "id", "name", "status", "versions", "children", "error").map(createTh))
          ),
          tbody(children <-- pageSignal.map { case (_, slice) => slice.map(resultRow) })
        )
      ),
      TgPaginationComponent(
        pageSignal.map { case (state, _) => Some(state) },
        (page, size) => pageVar.set((page, size))
      )
    )

  val element =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Import results", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(table)
      )
      .element

}
