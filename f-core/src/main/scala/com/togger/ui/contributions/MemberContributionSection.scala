package com.togger.ui.contributions

import com.raquo.laminar.api.L._
import com.togger.dto.{ContributionsDto, MemberContributionDto, MilestoneContributionsDto}
import com.togger.ui.contributions.MemberContributionViews.card
import com.togger.ui.task.TgDateFormater

import java.util.UUID

/** Everything one member contributed: over the whole project, then in each milestone they did something in. */
class MemberContributionSection(
  member: MemberContributionDto,
  team: Seq[MemberContributionDto],
  milestones: Seq[(MilestoneContributionsDto, MemberContributionDto)],
  tgDateFormater: TgDateFormater,
  isDark: Signal[Boolean]
) {

  // `team` is who the member shares the work with: the members of the project, or those of the milestone.
  private def contributions(stats: MemberContributionDto, team: Seq[MemberContributionDto]): HtmlElement =
    ScopeCharts.forMember(stats, team, isDark, tgDateFormater.asAmericanDate)

  private val milestoneSection: Option[HtmlElement] =
    Option.when(milestones.nonEmpty)(
      div(
        cls("flex flex-col gap-4"),
        h3(cls("text-lg font-semibold text-heading"), "By milestone"),
        milestones.map { case (milestone, stats) =>
          card(milestone.name, contributions(stats, milestone.members))
        }
      )
    )

  val element: HtmlElement =
    div(cls("flex flex-col gap-4"), contributions(member, team), milestoneSection)
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
