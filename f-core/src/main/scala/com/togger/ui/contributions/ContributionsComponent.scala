package com.togger.ui.contributions

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.{ContributionsDto, MemberContributionDto, MilestoneContributionsDto}
import com.togger.routes.Pages.ShowContributionsPage
import com.togger.routes.{ContributionsParams, FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.{TgTab, TgTabs}
import com.togger.ui.contributions.ContributionsComponent.GeneralTabId
import com.togger.ui.contributions.MemberContributionViews.{chartCard, memberTiles, taskAttributeCharts}
import com.togger.ui.milestone.{DateFilterComponent, DateFilterMode}
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TgDateFormater
import com.togger.ui.toast.Toast
import org.scalajs.dom

import java.util.UUID

/** What every member contributed to the project, then to each of its milestones, over a range of days. */
class ContributionsComponent(
  contributionsService: ContributionsFeService,
  tgDateFormater: TgDateFormater,
  tgClock: TgClock,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  isDark: Signal[Boolean],
  projectId: UUID,
  params: ContributionsParams,
  val toast: Toast
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  private val contributionsVar = Var(Option.empty[ContributionsDto])
  private val initialParams = ContributionsFilterMapping.withDefaultRange(params, tgClock.currentDate())
  private val paramsVar = Var(initialParams)
  private val selectedTabVar = Var(ContributionsComponent.GeneralTabId)

  private def load(current: ContributionsParams): IO[Unit] =
    contributionsService
      .findByProjectId(projectId, current.filter)
      .toastJustErrors(Option.empty[ContributionsDto])(Some(_))
      .map(contributionsVar.set)

  if (initialParams != params)
    dom.window.history
      .replaceState(null, "", router.absoluteUrlForPage(ShowContributionsPage(projectId, initialParams)))

  spinnerService.runWithSpinner(load(initialParams)).unsafeRunAndForget()

  /** Puts the range in the address bar without going through the router, so the page is not rebuilt, and reads the
    * contributions of the new range.
    */
  private def showRange(newParams: ContributionsParams): Unit =
    if (newParams != paramsVar.now()) {
      paramsVar.set(newParams)
      dom.window.history.replaceState(null, "", router.absoluteUrlForPage(ShowContributionsPage(projectId, newParams)))
      spinnerService.runWithSpinner(load(newParams)).unsafeRunAndForget()
    }

  private val dateFilterComponent = new DateFilterComponent(
    Val(ContributionsFilterMapping.monthOptions(tgClock.currentDate())),
    tgDateFormater,
    tgClock,
    initialMode = if (initialParams == ContributionsParams.default) DateFilterMode.Month else DateFilterMode.Range,
    initialMonths = Some(Set.empty),
    initialStart = initialParams.from,
    initialStop = initialParams.to
  )

  private def generalSection(contributions: ContributionsDto): HtmlElement = {
    val members = contributions.general
    div(
      memberTiles(members).amend(cls("mb-4")),
      div(
        cls("grid grid-cols-1 lg:grid-cols-2 gap-4"),
        chartCard("Log entries by status", ContributionCharts.entriesByStatus(members), isDark),
        chartCard("Share of the work", ContributionCharts.teamShare(members), isDark),
        chartCard(
          "Log entries per day",
          ContributionCharts.dailyTrend(members, tgDateFormater.asEuropeanDate),
          isDark,
          extraCls = "lg:col-span-2"
        ),
        chartCard("Tasks assigned and closed", ContributionCharts.tasksAssignedAndClosed(members), isDark),
        chartCard("Active days", ContributionCharts.activeDays(members), isDark)
      ),
      taskAttributeCharts(contributions.generalTasks, members, isDark).amend(cls("mt-4")),
      div(
        cls("grid grid-cols-1 lg:grid-cols-2 gap-4 mt-4"),
        members.map(member =>
          chartCard(
            s"Log entries per day: ${member.memberName}",
            ContributionCharts.dailyTrend(Seq(member), tgDateFormater.asEuropeanDate, ContributionCharts.ChartHeight),
            isDark
          )
        )
      )
    )
  }

  private def milestoneTabId(milestone: MilestoneContributionsDto): String =
    milestone.milestoneId.fold("no-milestone")(id => s"milestone-$id")

  // A tab's charts are built when the tab is opened, not with the page: ApexCharts measures its box on mount, which
  // a hidden panel does not have, and a project with many milestones would draw every chart before any is seen.
  private def whenSelected(tabId: String)(build: => HtmlElement): HtmlElement =
    div(child <-- selectedTabVar.signal.map(selected => if (selected == tabId) build else emptyNode))

  private def memberTabId(member: MemberContributionDto): String = s"member-${member.memberId}"

  // The general tab, the milestones, then a tab with a user icon for each member with all they contributed.
  private def tabsOf(contributions: ContributionsDto): Seq[TgTab] =
    TgTab(GeneralTabId, "General", whenSelected(GeneralTabId)(generalSection(contributions))) +:
      (contributions.milestones.map { milestone =>
        val tabId = milestoneTabId(milestone)
        TgTab(
          tabId,
          milestone.name,
          whenSelected(tabId)(new MilestoneContributionCard(milestone, tgDateFormater, isDark).element)
        )
      } ++ contributions.general.map { member =>
        val tabId = memberTabId(member)
        TgTab(
          tabId,
          member.memberName,
          whenSelected(tabId)(
            new MemberContributionSection(
              member,
              MemberContributionSection.milestonesOf(contributions, member.memberId),
              tgDateFormater,
              isDark
            ).element
          ),
          icon = Some(TgIcons.userIcon)
        )
      })

  private def page(contributions: ContributionsDto): HtmlElement =
    if (contributions.general.isEmpty) p(cls("text-body"), "This project has no members")
    else new TgTabs(tabsOf(contributions), selectedTabVar).element

  val element: HtmlElement =
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Contributions", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(dateFilterComponent.element)
        )
        .element
        .amend(cls("mb-4")),
      dateFilterComponent.activeFilter.map(ContributionsFilterMapping.paramsOf) --> Observer[ContributionsParams](
        showRange
      ),
      child <-- contributionsVar.signal.map(_.fold[HtmlElement](div())(page))
    )
}

object ContributionsComponent {
  val GeneralTabId = "general"
}
