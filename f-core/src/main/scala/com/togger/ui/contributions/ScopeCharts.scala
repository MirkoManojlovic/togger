package com.togger.ui.contributions

import com.raquo.laminar.api.L._
import com.togger.dto.{MemberContributionDto, TaskAttributesDto}
import com.togger.ui.contributions.MemberContributionViews.{chartCard, memberTiles, taskAttributeCharts}

import java.time.LocalDate

/** What a scope shows, in one order wherever the scope is: the project, a milestone or one member.
  *
  *   1. the numbers of the members
  *   1. the log entries by status, then the share of the work
  *   1. the log entries per day
  *   1. the tasks assigned and closed, then the active days (only a team compares members)
  *   1. the tasks by status, priority and label, then the same for each member
  *   1. the log entries per day of each member
  */
object ScopeCharts {

  /** The scope of a team, a project or a milestone. */
  def forTeam(
    members: Seq[MemberContributionDto],
    tasks: TaskAttributesDto,
    isDark: Signal[Boolean],
    formatDate: LocalDate => String
  ): HtmlElement =
    div(
      cls("flex flex-col gap-4"),
      memberTiles(members),
      div(
        cls("grid grid-cols-1 lg:grid-cols-2 gap-4"),
        chartCard("Log entries by status", ContributionCharts.entriesByStatus(members), isDark),
        chartCard("Share of the work", ContributionCharts.teamShare(members), isDark),
        chartCard(
          "Log entries per day",
          ContributionCharts.dailyTrend(members, formatDate),
          isDark,
          extraCls = "lg:col-span-2"
        ),
        chartCard("Tasks assigned and closed", ContributionCharts.tasksAssignedAndClosed(members), isDark),
        chartCard("Active days", ContributionCharts.activeDays(members), isDark)
      ),
      taskAttributeCharts(tasks, members, isDark),
      div(
        cls("grid grid-cols-1 lg:grid-cols-2 gap-4"),
        members.map(member =>
          chartCard(
            s"Log entries per day: ${member.memberName}",
            ContributionCharts.dailyTrend(Seq(member), formatDate, ContributionCharts.ChartHeight),
            isDark
          )
        )
      )
    )

  /** The scope of one member, the same order with what only a team compares left out. The share of the work is the
    * member's among `team`, the members of the project or of the milestone.
    */
  def forMember(
    member: MemberContributionDto,
    team: Seq[MemberContributionDto],
    isDark: Signal[Boolean],
    formatDate: LocalDate => String
  ): HtmlElement =
    div(
      cls("flex flex-col gap-4"),
      memberTiles(Seq(member)),
      div(
        cls("grid grid-cols-1 lg:grid-cols-2 gap-4"),
        chartCard("Log entries by status", ContributionCharts.entriesByStatus(Seq(member)), isDark),
        chartCard("Share of the work", ContributionCharts.teamShare(team), isDark),
        chartCard(
          "Log entries per day",
          ContributionCharts.dailyTrend(Seq(member), formatDate),
          isDark,
          extraCls = "lg:col-span-2"
        )
      ),
      taskAttributeCharts(member.tasks, Seq.empty, isDark)
    )
}
