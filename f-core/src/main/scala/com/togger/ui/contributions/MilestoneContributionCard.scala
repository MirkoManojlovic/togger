package com.togger.ui.contributions

import com.raquo.laminar.api.L._
import com.togger.dto.MilestoneContributionsDto
import com.togger.markdown.TgMarkdown
import com.togger.ui.auth.TgCard
import com.togger.ui.components.{
  ChartColorBadgeColor,
  TgBadge,
  TgBadgeColor,
  TgProgressColor,
  TgProgressComponent,
  TgProgressLabel,
  TgProgressSize
}
import com.togger.ui.task.TgDateFormater

import java.time.LocalDate

/** The contributions to one milestone, or to the tasks that have none: its progress, its members and their charts. */
class MilestoneContributionCard(
  milestone: MilestoneContributionsDto,
  tgDateFormater: TgDateFormater,
  tgMarkdown: TgMarkdown,
  isDark: Signal[Boolean]
) {

  // every class a whole literal, which Tailwind needs to find them
  private val descriptionCls =
    "mt-3 rounded-base border-l-4 border-brand bg-neutral-secondary-medium px-4 py-3 " +
      "text-base font-normal text-body"

  private val description: Option[String] = milestone.description.map(_.trim).filter(_.nonEmpty)

  private val header: HtmlElement =
    div(
      div(
        cls("flex flex-wrap items-center gap-3"),
        span(milestone.name),
        milestone.status.map[HtmlElement](status =>
          TgBadge(text = status.statusName, color = ChartColorBadgeColor.of(status), bordered = true).element
        ),
        MilestoneContributionCard
          .dateLabels(milestone, tgDateFormater.asAmericanDate)
          .map[HtmlElement](label => TgBadge(text = label, color = TgBadgeColor.Gray, bordered = true).element)
      ),
      // set apart from the name and the badges by a bar and a tint, so the description is read as its own block
      description.map[HtmlElement](text =>
        div(
          cls(descriptionCls),
          tgMarkdown.toLaminarDiv(text)
        )
      )
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

object MilestoneContributionCard {

  /** The start and the due date of a milestone, each for a badge; one that is not known has none. */
  def dateLabels(milestone: MilestoneContributionsDto, format: LocalDate => String): Seq[String] =
    milestone.startDate.map(start => s"Start: ${format(start)}").toSeq ++
      milestone.dueDate.map(due => s"Due: ${format(due)}")
}
