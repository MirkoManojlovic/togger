package com.togger.ui.timebox

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.common.TgResponseTypes.TgResp
import com.togger.dto.{
  FindMilestoneResp,
  FindTaskRespFull,
  FindTimeboxResp,
  SearchAccountDto,
  TimeboxVersionDto
}
import com.togger.markdown.TgMarkdown
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.apexchart.ApexChartComponent
import com.togger.ui.auth.TgCard
import com.togger.ui.components.{
  TgBadge,
  TgBadgeColor,
  TgProgressColor,
  TgProgressComponent,
  TgProgressLabel,
  TgProgressSize
}
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton}
import com.togger.ui.task.{TaskFeService, TgDateFormater}
import com.togger.ui.timebox.gantt.TimeboxReviewGanttComponent

import java.time.{DayOfWeek, ZoneOffset}
import java.util.UUID

/** Everything the review shows, read in one go. */
case class TimeboxReviewState(
  timebox: Option[FindTimeboxResp] = None,
  versions: Seq[TimeboxVersionDto] = Seq.empty,
  tasks: Seq[FindTaskRespFull] = Seq.empty,
  milestones: Seq[FindMilestoneResp] = Seq.empty,
  members: Seq[SearchAccountDto] = Seq.empty,
  workDays: Set[DayOfWeek] = Set.empty
)

/** Read only stats of a timebox: its progress, its change comments and its tasks, in their current state, as pies. */
class TimeboxReviewComponent(
  timeboxService: TimeboxFeService,
  taskService: TaskFeService,
  milestoneService: MilestoneFeService,
  projectService: ProjectService,
  tgDateFormater: TgDateFormater,
  tgMarkdown: TgMarkdown,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgClock: TgClock,
  isDark: Signal[Boolean],
  projectId: UUID,
  timeboxId: UUID
)(implicit
  ioRuntime: IORuntime
) {

  private val stateVar = Var(TimeboxReviewState())

  private def bodyOf[A](response: TgResp[A], default: A): A =
    response.toOption.flatMap(_.body).getOrElse(default)

  private def load(): IO[Unit] =
    for {
      timebox <- timeboxService.findById(timeboxId)
      versions <- timeboxService.listAllVersions(timeboxId)
      tasks <- taskService.findAllByTimebox(projectId, timeboxId)
      milestones <- milestoneService.findAllByProjectId(projectId)
      members <- projectService.findAllMembers(projectId)
      project <- projectService.findById(projectId)
    } yield stateVar.set(
      TimeboxReviewState(
        timebox = timebox.toOption.flatMap(_.body),
        versions = bodyOf(versions, Seq.empty),
        tasks = bodyOf(tasks, Seq.empty),
        milestones = bodyOf(milestones, Seq.empty),
        members = bodyOf(members, Seq.empty),
        workDays = project.toOption.flatMap(_.body).map(_.workDays.map(DayOfWeek.of)).getOrElse(Set.empty)
      )
    )

  spinnerService.runWithSpinner(load()).unsafeRunAndForget()

  private def card(title: String, content: HtmlElement) =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText(title, TgCard.CardHeaderSize.H4),
        body = TgCard.HtmlCardContent(content)
      )
      .element
      .amend(cls("w-full"))

  private def progressRow(state: TimeboxReviewState) = {
    val timebox = state.timebox.map(_.latestVersion)
    val days = timebox.map(version =>
      TimeboxReviewStats.daysProgress(version.startDate, version.endDate, tgClock.currentDate())
    )
    val tasks = TimeboxReviewStats.tasksProgress(state.tasks)
    div(
      cls("grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4"),
      card(
        "Days left",
        div(days.map[HtmlElement](d => progressBar(d.elapsed, d.total, s"${d.left} of ${d.total} days left")).getOrElse(p(cls("text-body"), "No dates")))
      ),
      card(
        "Tasks left to do",
        div(progressBar(tasks.done, tasks.total, s"${tasks.left} of ${tasks.total} tasks left to do"))
      )
    )
  }

  private def progressBar(value: Int, max: Int, title: String) =
    TgProgressComponent(
      value = value,
      max = max.max(1),
      color = TgProgressColor.Success,
      size = TgProgressSize.Large,
      label = TgProgressLabel.Outside(title)
    ).element

  private def datesCard(state: TimeboxReviewState) =
    card(
      "Timebox",
      div(
        state.timebox.map(_.latestVersion).map[HtmlElement](version =>
          div(
            p(
              cls("mb-4 font-medium text-heading"),
              s"${tgDateFormater.asEuropeanDate(version.startDate)} - ${tgDateFormater.asEuropeanDate(version.endDate)}"
            ),
            version.description.map[HtmlElement](tgMarkdown.toLaminarDiv).getOrElse(p(cls("text-body"), "No description"))
          )
        ).getOrElse(p(cls("text-body"), "No timebox"))
      )
    )

  private def changeCommentsCard(state: TimeboxReviewState) = {
    val commented = state.versions.filter(_.changeComment.trim.nonEmpty)
    card(
      "Change comments",
      div(
        if (commented.isEmpty) p(cls("text-body"), "No change comments")
        else
          commented.map(version =>
            div(
              cls("mb-2"),
              div(
                cls("flex items-center gap-2 mb-2"),
                TgBadge(
                  text = tgDateFormater.asAmericanDateWithDayAbrv(version.createdAt.atOffset(ZoneOffset.UTC).toLocalDate),
                  bordered = true,
                  color = TgBadgeColor.Gray
                ).element
              ),
              tgMarkdown.toLaminarDiv(version.changeComment)
            )
          )
      )
    )
  }

  private def descriptionRow(state: TimeboxReviewState) =
    div(cls("grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4"), datesCard(state), changeCommentsCard(state))

  private def ganttRow(state: TimeboxReviewState) = {
    val gantt = new TimeboxReviewGanttComponent(
      Val(state.tasks),
      Val(state.workDays),
      Val(state.timebox.map(_.latestVersion).map(version => (version.startDate, version.endDate))),
      Val(state.members.map(_.id)),
      Val(state.milestones),
      tgDateFormater,
      tgMarkdown,
      router,
      tgClock
    )
    div(gantt.element, gantt.detailElement)
  }

  private def pieCard(pie: ReviewPie) =
    div(
      cls("bg-neutral-primary-soft border border-default rounded-base shadow-xs p-4 md:p-6"),
      h5(cls("text-heading text-lg font-semibold mb-4"), pie.title),
      new ApexChartComponent(Val(pie.options), isDark).element
    )

  private def pieGrid(pies: Seq[ReviewPie], columnsCls: String) =
    div(cls("grid grid-cols-1 gap-4"), cls(columnsCls), pies.map(pieCard))

  private def overallRow(state: TimeboxReviewState) =
    div(
      cls("mb-4"),
      pieGrid(
        TimeboxReviewStats.pies(TimeboxReviewStats.OverallGroupings, state.tasks, state.milestones),
        "md:grid-cols-2 xl:grid-cols-3"
      )
    )

  private def memberSection(member: SearchAccountDto, state: TimeboxReviewState) = {
    val memberTasks = TimeboxReviewStats.tasksOf(member, state.tasks)
    div(
      cls("mb-6"),
      h5(cls("text-heading text-xl font-bold mb-2"), member.fullName),
      if (memberTasks.isEmpty) p(cls("text-body"), "No tasks")
      else
        pieGrid(
          TimeboxReviewStats.pies(TimeboxReviewStats.MemberGroupings, memberTasks, state.milestones),
          "md:grid-cols-2 xl:grid-cols-4"
        )
    )
  }

  private def membersRow(state: TimeboxReviewState) =
    div(state.members.map(memberSection(_, state)))

  val element =
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Timebox review"),
              iconButton(
                "Timeboxes",
                TgIcons.listIcon(className = "w-4 h-4"),
                headerIconButtonCls,
                "mr-2",
                router.navigateTo(ShowTimeboxesPage(projectId))
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard.HtmlCardContent(
            div(
              child <-- stateVar.signal.map(state =>
                div(progressRow(state), descriptionRow(state), ganttRow(state), overallRow(state), membersRow(state))
              )
            )
          )
        )
        .element
    )

}
