package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.dto.{DataExportPathStatusResp, DataExportSyncStatusResp, DataExportTaskStatusResp}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import org.scalajs.dom.HTMLButtonElement

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

  def exportPathStatusCard(pathStatus: DataExportPathStatusResp, header: String) =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(div(header), TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            cls(tableWrapperCls),
            table(
              cls(tableCls),
              thead(
                cls(theadCls),
                tr(Seq("task id", "task name", "status", "details").map(createTh))
              ),
              tbody(
                pathStatus.taskStatuses
                  .sortBy(_.taskId)
                  .map { (taskStatus: DataExportTaskStatusResp) =>
                    tr(
                      cls(rowCls),
                      createTd(taskStatus.taskId.toString),
                      createTd(taskStatus.taskName.getOrElse("-"): String),
                      createTd(exportSyncStatusBadge(taskStatus.status)),
                      createTd(taskStatus.differences.mkString(", "))
                    )
                  }
              )
            )
          )
        )
      )
      .element

  private def exportSyncStatusBadge(status: DataExportSyncStatusResp) =
    TgBadge(status.entryName, exportSyncStatusBadgeColor(status)).element

}
