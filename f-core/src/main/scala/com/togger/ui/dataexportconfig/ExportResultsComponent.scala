package com.togger.ui.dataexportconfig

import com.raquo.laminar.api.L._
import com.togger.dto.{DataExportPathResultResp, DataExportResp}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.task.TaskConfigTableSupport

/** What an export did: whether each configured path was written, and how many entities of each type it holds. */
class ExportResultsComponent(response: DataExportResp) {

  import TaskConfigTableSupport._

  private def pathBadge(result: DataExportPathResultResp) =
    if (result.success) TgBadge("Exported", TgBadgeColor.Green).element
    else TgBadge("Failed", TgBadgeColor.Red).element

  private def pathRow(result: DataExportPathResultResp) =
    tr(
      cls(rowCls),
      createTd(result.exportPath),
      createTd(pathBadge(result)),
      createTd(result.error.getOrElse("-"): String)
    )

  private def countRow(entityType: String, count: Int) =
    tr(cls(rowCls), createTd(entityType), createTd(count.toString))

  private def resultTable(headers: Seq[String], rows: Seq[HtmlElement]) =
    div(
      cls(tableWrapperCls),
      table(
        cls(tableCls),
        thead(cls(theadCls), tr(headers.map(createTh))),
        tbody(rows)
      )
    )

  private def card(title: String, content: HtmlElement) =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText(title, TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(content)
      )
      .element

  val element =
    div(
      cls("flex flex-col gap-4"),
      card(
        s"Export paths: ${response.results.count(_.success)}/${response.results.size} exported",
        resultTable(Seq("path", "status", "error"), response.results.map(pathRow))
      ),
      card(
        s"Exported entities: ${response.entityCounts.map(_.count).sum}",
        resultTable(
          Seq("type", "count"),
          response.entityCounts.sortBy(_.entityType.entryName).map(c => countRow(c.entityType.entryName, c.count))
        )
      )
    )

}
