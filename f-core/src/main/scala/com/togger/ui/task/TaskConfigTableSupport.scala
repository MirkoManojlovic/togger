package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.common.PageRequest
import com.togger.dto.{
  DataExportEntityStatusResp,
  DataExportFieldDiffResp,
  DataExportPathStatusResp,
  DataExportSyncStatusResp
}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.components.{TgBadge, TgBadgeColor, TgPaginationComponent}
import com.togger.ui.organization.TgIcons
import org.scalajs.dom
import org.scalajs.dom.HTMLButtonElement

import scala.scalajs.js

object TaskConfigTableSupport {

  val headerIconButtonCls =
    "inline-flex items-center justify-center w-8 h-8 text-fg-brand bg-neutral-primary border border-brand hover:bg-brand hover:text-white focus:ring-4 focus:ring-brand-subtle rounded-base focus:outline-none"

  val rowIconButtonCls =
    "inline-flex items-center justify-center w-8 h-8 text-white bg-brand hover:bg-brand-strong box-border border border-transparent focus:ring-4 focus:ring-brand-medium shadow-xs rounded-base focus:outline-none"

  val rowIconButtonDangerCls =
    "inline-flex items-center justify-center w-8 h-8 text-white bg-red-600 hover:bg-red-700 box-border border border-transparent focus:ring-4 focus:ring-red-300 shadow-xs rounded-base focus:outline-none"

  val headerButtonMarginCls = "mr-2"
  val rowButtonMarginCls = "mr-2 mt-1 mb-1"
  val lastRowButtonMarginCls = "mt-1 mb-1"

  val tableWrapperCls =
    "relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"
  val tableCls = "w-full text-sm text-left rtl:text-right text-body"
  val theadCls = "text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"
  val rowCls = "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"

  def iconButton(
    label: String,
    icon: Modifier.Base,
    btnCls: String,
    marginCls: String,
    action: Modifier[ReactiveHtmlElement[HTMLButtonElement]]
  ) =
    button(
      `type`("button"),
      title(label),
      icon,
      cls(btnCls),
      cls(marginCls),
      action
    )

  private def exportSyncStatusBadgeColor(status: DataExportSyncStatusResp): TgBadgeColor = status match {
    case DataExportSyncStatusResp.InSync          => TgBadgeColor.Green
    case DataExportSyncStatusResp.OutOfSync       => TgBadgeColor.Yellow
    case DataExportSyncStatusResp.MissingFromExport => TgBadgeColor.Red
    case DataExportSyncStatusResp.MissingFromDb   => TgBadgeColor.Red
    case DataExportSyncStatusResp.Malformed       => TgBadgeColor.Red
  }

  /** One export path with its entities: a summary of the statuses that doubles as a filter, then the entity rows a
    * page at a time.
    */
  private def copyPathButton(path: String) = {
    val copiedVar = Var(false)
    div(
      cls("relative"),
      button(
        typ("button"),
        cls("text-body hover:text-fg-brand focus:outline-none focus:ring-2 focus:ring-brand rounded-base"),
        title("Copy the path to the clipboard"),
        aria.label("Copy the path to the clipboard"),
        TgIcons.copyIcon("w-6 h-6"),
        onClick --> { _ =>
          dom.window.navigator.clipboard.writeText(path)
          copiedVar.set(true)
          js.timers.setTimeout(CopiedMessageMillis)(copiedVar.set(false))
        }
      ),
      child <-- copiedVar.signal.map { copied =>
        if (copied)
          span(
            cls("absolute right-0 top-full mt-1 text-xs font-normal text-body whitespace-nowrap"),
            role("status"),
            "Copied to clipboard"
          )
        else emptyNode
      }
    )
  }

  private val CopiedMessageMillis = 2000.0

  private def expandButton(expandedVar: Var[Boolean]) =
    button(
      typ("button"),
      cls("text-body hover:text-fg-brand focus:outline-none focus:ring-2 focus:ring-brand rounded-base"),
      title <-- expandedVar.signal.map(if (_) "Hide the differences" else "Show the differences"),
      aria.expanded <-- expandedVar.signal,
      child <-- expandedVar.signal.map { expanded =>
        if (expanded) TgIcons.chevronDownIcon("w-4 h-4") else TgIcons.chevronRightIcon("w-4 h-4")
      },
      onClick --> (_ => expandedVar.update(!_))
    )

  private def fieldDiffsRow(fieldDiffs: List[DataExportFieldDiffResp], expanded: Signal[Boolean]) =
    tr(
      cls(rowCls),
      hidden <-- expanded.map(!_),
      td(
        colSpan(5),
        cls("px-6 py-2"),
        div(
          cls(tableWrapperCls),
          table(
            cls(tableCls),
            thead(cls(theadCls), tr(Seq("field", "disk", "database").map(createTh))),
            tbody(
              fieldDiffs.map { diff =>
                tr(
                  cls("border-b border-default"),
                  td(cls("px-6 py-2 font-mono break-all"), diff.field),
                  fieldDiffValue(diff.disk),
                  fieldDiffValue(diff.database)
                )
              }
            )
          )
        )
      )
    )

  private def fieldDiffValue(value: Option[String]) =
    td(
      cls("px-6 py-2 font-mono break-all whitespace-pre-wrap"),
      value.fold[Modifier[HtmlElement]](span(cls("text-body opacity-60"), "-"))(v => v)
    )

  def exportPathStatusCard(pathStatus: DataExportPathStatusResp, header: String) = {
    val allStatuses = pathStatus.entityStatuses.sortBy(s => (s.entityType.entryName, s.entityId))
    val pageVar = Var((PageRequest.DefaultPage, PageRequest.DefaultSize))
    val statusFilterVar = Var(Option.empty[DataExportSyncStatusResp])

    val pageSignal: Signal[(PaginationState, Seq[DataExportEntityStatusResp])] =
      pageVar.signal.combineWith(statusFilterVar.signal).map { case (page, size, statusFilter) =>
        val statuses = statusFilter.fold(allStatuses)(status => allStatuses.filter(_.status == status))
        val totalPages = math.max(1, (statuses.size + size - 1) / size)
        val current = math.min(page, totalPages)
        val slice = statuses.slice((current - 1) * size, current * size)
        (PaginationState(current, size, statuses.size.toLong, totalPages, slice.size), slice)
      }

    def toggleStatusFilter(status: DataExportSyncStatusResp): Unit = {
      statusFilterVar.update(current => if (current.contains(status)) None else Some(status))
      pageVar.update { case (_, size) => (PageRequest.DefaultPage, size) }
    }

    val counts = allStatuses.groupBy(_.status).view.mapValues(_.size).toMap

    def summary(activeFilter: Option[DataExportSyncStatusResp]) =
      div(
        cls("flex flex-wrap items-center gap-2 mb-4 text-sm text-body"),
        span(cls("font-semibold text-heading"), s"Total rows: ${allStatuses.size}"),
        DataExportSyncStatusResp.values.map { status =>
          val isActive = activeFilter.contains(status)
          button(
            typ("button"),
            cls(
              "inline-flex items-center gap-1 px-1 rounded-base focus:outline-none focus:ring-2 focus:ring-brand " +
                (if (isActive) "ring-2 ring-brand" else "opacity-80 hover:opacity-100")
            ),
            aria.pressed(isActive.toString),
            title(if (isActive) "Click to show all results" else s"Show only ${status.entryName} results"),
            exportSyncStatusBadge(status),
            counts.getOrElse(status, 0).toString,
            onClick --> (_ => toggleStatusFilter(status))
          )
        }
      )

    def entityRows(entityStatus: DataExportEntityStatusResp): Seq[HtmlElement] = {
      val expandedVar = Var(false)
      val hasDiffs = entityStatus.fieldDiffs.nonEmpty
      val entityRow = tr(
        cls(rowCls),
        createTd(entityStatus.entityType.entryName),
        createTd(entityStatus.entityId.toString),
        createTd(entityStatus.entityName.getOrElse("-"): String),
        createTd(exportSyncStatusBadge(entityStatus.status)),
        createTd(
          div(
            cls("flex items-center gap-2"),
            Option.when(hasDiffs)(expandButton(expandedVar)),
            entityStatus.differences.mkString(", ")
          )
        )
      )
      if (hasDiffs) Seq(entityRow, fieldDiffsRow(entityStatus.fieldDiffs, expandedVar.signal)) else Seq(entityRow)
    }

    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(
          div(cls("flex items-center justify-between gap-2"), span(header), copyPathButton(pathStatus.exportPath)),
          TgCard.CardHeaderSize.H3
        ),
        body = TgCard.HtmlCardContent(
          div(
            child <-- statusFilterVar.signal.map(summary),
            div(
              cls(tableWrapperCls),
              table(
                cls(tableCls),
                thead(
                  cls(theadCls),
                  tr(Seq("type", "id", "name", "status", "details").map(createTh))
                ),
                tbody(children <-- pageSignal.map { case (_, slice) => slice.flatMap(entityRows) })
              )
            ),
            TgPaginationComponent(
              pageSignal.map { case (state, _) => Some(state) },
              (page, size) => pageVar.set((page, size))
            )
          )
        )
      )
      .element
  }

  private def exportSyncStatusBadge(status: DataExportSyncStatusResp) =
    TgBadge(status.entryName, exportSyncStatusBadgeColor(status)).element

}
