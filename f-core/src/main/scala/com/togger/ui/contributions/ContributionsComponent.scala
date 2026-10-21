package com.togger.ui.contributions

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.ContributionsDto
import com.togger.routes.Pages.ShowContributionsPage
import com.togger.routes.{ContributionsParams, FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.contributions.MemberContributionViews.{card, chartCard, memberTiles}
import com.togger.ui.milestone.{DateFilterComponent, DateFilterMode}
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
  private val paramsVar = Var(params)

  private def load(current: ContributionsParams): IO[Unit] =
    contributionsService
      .findByProjectId(projectId, current.filter)
      .toastJustErrors(Option.empty[ContributionsDto])(Some(_))
      .map(contributionsVar.set)

  spinnerService.runWithSpinner(load(params)).unsafeRunAndForget()

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
    initialMode = if (params == ContributionsParams.default) DateFilterMode.Month else DateFilterMode.Range,
    initialMonths = Some(Set.empty),
    initialStart = params.from,
    initialStop = params.to
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
          isDark
        ),
        chartCard("Tasks assigned and closed", ContributionCharts.tasksAssignedAndClosed(members), isDark),
        chartCard("Active days", ContributionCharts.activeDays(members), isDark)
      )
    )
  }

  private def milestonesSection(contributions: ContributionsDto): HtmlElement =
    div(
      cls("flex flex-col gap-4"),
      contributions.milestones.map(milestone =>
        new MilestoneContributionCard(milestone, tgDateFormater, isDark).element
      )
    )

  private def page(contributions: ContributionsDto): HtmlElement =
    if (contributions.general.isEmpty) p(cls("text-body"), "This project has no members")
    else
      div(
        cls("flex flex-col gap-4"),
        card("General", generalSection(contributions)),
        if (contributions.milestones.isEmpty) emptyNode else milestonesSection(contributions)
      )

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
