package com.togger.ui.contributions

import com.raquo.laminar.api.L._
import com.togger.dto.{ContributionsDto, MemberContributionDto, MilestoneContributionsDto}
import com.togger.ui.contributions.MemberContributionViews.{card, chartCard, memberTiles, taskAttributeCharts}
import com.togger.ui.task.TgDateFormater

import java.util.UUID

/** Everything one member contributed: over the whole project, then in each milestone they did something in. */
class MemberContributionSection(
  member: MemberContributionDto,
  milestones: Seq[(MilestoneContributionsDto, MemberContributionDto)],
  tgDateFormater: TgDateFormater,
  isDark: Signal[Boolean]
) {

  // The member's tile, their log entries and their tasks; a scope of one member, so no per member rows.
  private def contributions(stats: MemberContributionDto): HtmlElement =
    div(
      cls("flex flex-col gap-4"),
      memberTiles(Seq(stats)),
      div(
        cls("grid grid-cols-1 lg:grid-cols-2 gap-4"),
        chartCard("Log entries by status", ContributionCharts.entriesByStatus(Seq(stats)), isDark),
        chartCard(
          "Log entries per day",
          ContributionCharts.dailyTrend(Seq(stats), tgDateFormater.asEuropeanDate, ContributionCharts.ChartHeight),
          isDark
        )
      ),
      taskAttributeCharts(stats.tasks, Seq.empty, isDark)
    )

  private val milestoneSection: Option[HtmlElement] =
    Option.when(milestones.nonEmpty)(
      div(
        cls("flex flex-col gap-4"),
        h3(cls("text-lg font-semibold text-heading"), "By milestone"),
        milestones.map { case (milestone, stats) => card(milestone.name, contributions(stats)) }
      )
    )

  val element: HtmlElement =
    div(cls("flex flex-col gap-4"), contributions(member), milestoneSection)
}

object MemberContributionSection {

  /** The milestones a member did something in, with what they contributed to each, in the order of the milestones. */
  def milestonesOf(
    contributions: ContributionsDto,
    memberId: UUID
  ): Seq[(MilestoneContributionsDto, MemberContributionDto)] =
    contributions.milestones.flatMap(milestone =>
      milestone.members.find(_.memberId == memberId).map(milestone -> _)
    )
}
