package com.togger.ui.contributions

import com.raquo.laminar.api.L._
import com.togger.dto.MilestoneContributionsDto
import com.togger.ui.auth.TgCard
import com.togger.ui.components.{
  ChartColorBadgeColor,
  TgBadge,
  TgProgressColor,
  TgProgressComponent,
  TgProgressLabel,
  TgProgressSize
}
import com.togger.ui.task.TgDateFormater

/** The contributions to one milestone, or to the tasks that have none: its progress, its members and their charts. */
class MilestoneContributionCard(
  milestone: MilestoneContributionsDto,
  tgDateFormater: TgDateFormater,
  isDark: Signal[Boolean]
) {

  private val dates: Option[String] =
    milestone.startDate.zip(milestone.dueDate).map { case (start, due) =>
      s"${tgDateFormater.asEuropeanDate(start)} - ${tgDateFormater.asEuropeanDate(due)}"
    }

  private val header: HtmlElement =
    div(
      cls("flex flex-wrap items-center gap-3"),
      span(milestone.name),
      milestone.status.map[HtmlElement](status =>
        TgBadge(text = status.statusName, color = ChartColorBadgeColor.of(status), bordered = true).element
      ),
      dates.map[HtmlElement](text => span(cls("text-sm font-normal text-body"), text))
    )

  private val progress: HtmlElement =
    TgProgressComponent(
      value = milestone.closedTasks,
      max = milestone.totalTasks.max(1),
      color = TgProgressColor.Success,
      size = TgProgressSize.Large,
      label = TgProgressLabel.Outside(s"${milestone.closedTasks} of ${milestone.totalTasks} tasks ended")
    ).element

  private val body: HtmlElement =
    div(
      cls("flex flex-col gap-4"),
      progress,
      ScopeCharts.forTeam(milestone.members, milestone.tasks, isDark, tgDateFormater.asEuropeanDate)
    )

  val element: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(header, TgCard.CardHeaderSize.H4),
        body = TgCard.HtmlCardContent(body)
      )
      .element
      .amend(cls("w-full"))
}
