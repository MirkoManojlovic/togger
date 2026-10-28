package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.dto.FindMilestoneSummaryResp
import com.togger.markdown.TgMarkdown
import com.togger.ui.components.{TgBadge, TgBadgeColor, TgTab, TgTabs}
import com.togger.ui.task.TgDateFormater

/** The two tabs of a milestone: its status, and its summaries with one tab each. */
class MilestoneTabs(tgDateFormater: TgDateFormater, tgMarkdown: TgMarkdown) {

  private val StatusTab = "status"
  private val SummaryTab = "milestone-summary"

  private def dateBadge(label: String, date: java.time.LocalDate): HtmlElement =
    TgBadge(
      text = s"$label ${tgDateFormater.asAmericanDateWithDayAbrv(date)}",
      color = TgBadgeColor.Gray,
      bordered = true
    ).element

  private def summaryContent(summary: FindMilestoneSummaryResp): HtmlElement =
    div(
      div(
        cls("flex flex-wrap items-center gap-1.5 mb-2"),
        dateBadge("Start", summary.startDate),
        dateBadge("End", summary.endDate)
      ),
      Option.when(summary.summary.nonEmpty)(tgMarkdown.toLaminarDiv(summary.summary))
    )

  // One tab per summary, newest first; the selected tab survives the summaries being reloaded.
  private def summaryTabs(summaries: Seq[FindMilestoneSummaryResp], selectedVar: Var[String]): HtmlElement =
    if (summaries.isEmpty) div(cls("text-sm text-body"), "No summaries yet.")
    else
      new TgTabs(
        summaries.map { summary =>
          TgTab(
            id = summary.id.toString,
            label =
              s"${tgDateFormater.asAmericanDate(summary.startDate)} - ${tgDateFormater.asAmericanDate(summary.endDate)}",
            content = summaryContent(summary)
          )
        },
        selectedVar
      ).element

  def apply(status: HtmlElement, summaries: Signal[Seq[FindMilestoneSummaryResp]]): HtmlElement = {
    val selectedTabVar = Var(StatusTab)
    val selectedSummaryVar = Var("")
    new TgTabs(
      Seq(
        TgTab(id = StatusTab, label = "Status", content = status),
        TgTab(
          id = SummaryTab,
          label = "Milestone summary",
          content = div(child <-- summaries.map(summaryTabs(_, selectedSummaryVar)))
        )
      ),
      selectedTabVar
    ).element
  }
}
