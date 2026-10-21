package com.togger.ui.contributions

import com.raquo.laminar.api.L._
import com.togger.dto.{MemberContributionDto, TaskAttributesDto}
import com.togger.ui.apexchart.{ApexChartComponent, ApexChartOptions}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.{TgProgressColor, TgProgressComponent, TgProgressLabel, TgProgressSize}
import com.togger.ui.logentry.LogEntryMemberBadge

/** The pieces the general part and the milestone cards of the contributions page are made of. */
object MemberContributionViews {

  def card(title: String, content: HtmlElement): HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText(title, TgCard.CardHeaderSize.H4),
        body = TgCard.HtmlCardContent(content)
      )
      .element
      .amend(cls("w-full"))

  /** A chart in a card, or a line saying there is nothing to draw. `extraCls` is for the place the card takes in its
    * grid.
    */
  def chartCard(
    title: String,
    options: ApexChartOptions,
    isDark: Signal[Boolean],
    extraCls: String = ""
  ): HtmlElement =
    card(
      title,
      if (ContributionCharts.hasData(options)) new ApexChartComponent(Val(options), isDark, options.height).element
      else p(cls("text-body"), "Nothing to show for this range")
    ).amend(cls(extraCls))

  private def memberTaskCharts(member: MemberContributionDto, isDark: Signal[Boolean]): HtmlElement =
    div(
      cls("grid grid-cols-1 lg:grid-cols-3 gap-4"),
      chartCard(s"Tasks by status: ${member.memberName}", ContributionCharts.tasksByStatus(member.tasks), isDark),
      chartCard(s"Tasks by priority: ${member.memberName}", ContributionCharts.tasksByPriority(member.tasks), isDark),
      chartCard(s"Tasks by label: ${member.memberName}", ContributionCharts.tasksByLabel(member.tasks), isDark)
    )

  /** The tasks of a scope by their status, priority and label, side by side, then a row of the same for each member
    * the tasks are assigned to. The tasks of nobody are only in the first row, and a member without tasks has no row.
    */
  def taskAttributeCharts(
    tasks: TaskAttributesDto,
    members: Seq[MemberContributionDto],
    isDark: Signal[Boolean]
  ): HtmlElement =
    div(
      cls("flex flex-col gap-4"),
      div(
        cls("grid grid-cols-1 lg:grid-cols-3 gap-4"),
        chartCard("Tasks by status", ContributionCharts.tasksByStatus(tasks), isDark),
        chartCard("Tasks by priority", ContributionCharts.tasksByPriority(tasks), isDark),
        chartCard("Tasks by label", ContributionCharts.tasksByLabel(tasks), isDark)
      ),
      members.filter(_.tasks.byStatus.nonEmpty).map(memberTaskCharts(_, isDark))
    )

  private def stat(label: String, value: String): HtmlElement =
    div(
      div(cls("text-sm text-body"), label),
      div(cls("text-lg font-semibold text-heading"), value)
    )

  def totalEntries(member: MemberContributionDto): Int = member.entriesByStatus.map(_.count).sum

  private def memberTile(member: MemberContributionDto): HtmlElement =
    div(
      cls("bg-neutral-primary-soft border border-default rounded-base shadow-xs p-4"),
      div(cls("mb-3"), LogEntryMemberBadge(member.memberName).getOrElse(span(member.memberName))),
      div(
        cls("grid grid-cols-3 gap-3 mb-3"),
        stat("Log entries", totalEntries(member).toString),
        stat("Tasks assigned", member.tasksAssigned.toString),
        stat("Tasks closed", member.tasksClosed.toString)
      ),
      TgProgressComponent(
        value = member.activeDays.min(member.workDaysInRange),
        max = member.workDaysInRange.max(1),
        color = TgProgressColor.Brand,
        size = TgProgressSize.Large,
        label = TgProgressLabel.Outside(s"Active ${member.activeDays} of ${member.workDaysInRange} work days")
      ).element
    )

  def memberTiles(members: Seq[MemberContributionDto]): HtmlElement =
    if (members.isEmpty) p(cls("text-body"), "Nobody did anything here")
    else div(cls("grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4"), members.map(memberTile))
}
