package com.togger.ui.project

import cats.effect.IO
import cats.syntax.all._
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.{FindMilestoneResp, FindProjectResp, FindTaskRespFull, FindTaskStatusResp, FindTimeboxResp}
import com.togger.markdown.TgMarkdown
import com.togger.milestone.MilestoneStatus
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.components.{TgBadge, TgBadgeColor, TgProgressColor, TgProgressComponent, TgProgressLabel, TgProgressSize}
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskFeService, TaskStatusFeService, TgDateFormater}
import com.togger.ui.timebox.{TimeboxFeService, TimeboxReviewStats}
import com.togger.dto.{MilestoneFilter, TaskFilter}

import java.util.UUID

class ProjectHomeComponent(
  projectService: ProjectService,
  milestoneService: MilestoneFeService,
  timeboxService: TimeboxFeService,
  taskService: TaskFeService,
  taskStatusService: TaskStatusFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  tgMarkdown: TgMarkdown,
  tgClock: TgClock,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) {

  private val projectVar = Var(Option.empty[FindProjectResp])
  private val milestonesVar = Var(Seq.empty[(FindMilestoneResp, Seq[FindTaskRespFull])])
  private val timeboxesVar = Var(Seq.empty[(FindTimeboxResp, Seq[FindTaskRespFull])])
  private val inFlightVar = Var(Seq.empty[(FindTaskStatusResp, Seq[FindTaskRespFull])])

  private def loaded[A](call: TgRespIO[A])(onSuccess: A => Unit): IO[Unit] =
    call.map {
      case Left(failure)  => println(failure)
      case Right(success) => onSuccess(success.body.get)
    }

  /** The tasks of each parent, loaded one parent after the other; a failure leaves the parent without tasks. */
  private def withTasks[P](parents: Seq[P])(tasksOf: P => TgRespIO[Seq[FindTaskRespFull]]): IO[Seq[(P, Seq[FindTaskRespFull])]] =
    parents.toList.traverse { parent =>
      tasksOf(parent).map(_.fold(_ => parent -> Seq.empty, success => parent -> success.body.getOrElse(Seq.empty)))
    }

  private def loadMilestones(): IO[Unit] =
    PaginatedLoader
      .loadAll(milestoneService.findByProjectId(projectId, MilestoneFilter(statuses = List(MilestoneStatus.InProgress)), _))
      .flatMap {
        case Left(failure) => IO.println(failure)
        case Right(success) =>
          withTasks(success.body.getOrElse(Seq.empty).sortBy(_.dueDate.toEpochDay))(milestone =>
            taskService.findAllByMilestone(projectId, milestone.id)
          ).map(milestonesVar.set)
      }

  private def loadTimeboxes(): IO[Unit] =
    PaginatedLoader.loadAll(timeboxService.findByProjectId(projectId, _)).flatMap {
      case Left(failure) => IO.println(failure)
      case Right(success) =>
        withTasks(ProjectHomeStats.activeTimeboxes(success.body.getOrElse(Seq.empty), tgClock.currentDate()))(
          timebox => taskService.findAllByTimebox(projectId, timebox.id)
        ).map(timeboxesVar.set)
    }

  private def loadInFlightTasks(): IO[Unit] =
    taskStatusService.findAllByProjectId(projectId).flatMap {
      case Left(failure) => IO.println(failure)
      case Right(success) =>
        val statuses = ProjectHomeStats.inFlightStatuses(success.body.getOrElse(Seq.empty))
        if (statuses.isEmpty) IO.unit
        else
          PaginatedLoader
            .loadAll(taskService.findByProjectId(projectId, TaskFilter(statusIds = statuses.map(_.id).toList), _))
            .flatMap {
              case Left(failure) => IO.println(failure)
              case Right(tasks) =>
                IO(inFlightVar.set(ProjectHomeStats.tasksByStatus(statuses, tasks.body.getOrElse(Seq.empty))))
            }
    }

  // One spinner for the whole page, so it stays until the last part is loaded.
  spinnerService
    .runWithSpinner(
      for {
        _ <- loaded(projectService.findById(projectId))(project => projectVar.set(Some(project)))
        _ <- loadMilestones()
        _ <- loadTimeboxes()
        _ <- loadInFlightTasks()
      } yield ()
    )
    .unsafeRunAndForget()

  private def card(title: String, content: HtmlElement): HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText(title, TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(content)
      )
      .element
      .amend(cls("mb-4"))

  private def progressBar(value: Int, max: Int, title: String): HtmlElement =
    TgProgressComponent(
      value = value,
      max = max.max(1),
      color = TgProgressColor.Success,
      size = TgProgressSize.Large,
      label = TgProgressLabel.Outside(title)
    ).element

  private def pageLink(text: String, page: FrontendPage): HtmlElement =
    a(cls("text-fg-brand hover:underline cursor-pointer"), text, router.navigateTo(page))

  private def emptyNote(text: String): HtmlElement = div(cls("text-body"), text)

  private def projectCard(project: FindProjectResp): HtmlElement =
    card(
      project.name,
      div(
        tgMarkdown.toLaminarDiv(project.description),
        Option.when(project.projectLabels.nonEmpty)(
          div(
            cls("flex flex-wrap gap-1 mt-2"),
            project.projectLabels
              .sortBy(_.name)
              .map(label => TgBadge(text = label.name, color = TgBadgeColor.Indigo, bordered = true).element)
          )
        ),
        Option.when(project.members.nonEmpty)(
          div(
            cls("mt-4"),
            div(cls("font-semibold text-heading mb-1"), "Members"),
            div(
              cls("flex flex-wrap gap-1"),
              project.members
                .sortBy(_.fullName)
                .map(member => TgBadge(text = member.fullName, color = TgBadgeColor.Gray, bordered = true).element)
            )
          )
        )
      )
    )

  private def milestoneRow(milestone: FindMilestoneResp, tasks: Seq[FindTaskRespFull]): HtmlElement = {
    val progress = TimeboxReviewStats.tasksProgress(tasks)
    div(
      cls("mb-4"),
      div(
        cls("flex flex-wrap items-center gap-2 mb-1"),
        pageLink(milestone.name, MilestoneReviewPage(projectId, milestone.id)),
        TgBadge(
          text = s"Due ${tgDateFormater.asAmericanDateWithDayAbrv(milestone.dueDate)}",
          color = TgBadgeColor.Gray,
          bordered = true
        ).element
      ),
      progressBar(progress.done, progress.total, s"${progress.left} of ${progress.total} tasks left to do")
    )
  }

  private def timeboxRow(timebox: FindTimeboxResp, tasks: Seq[FindTaskRespFull]): HtmlElement = {
    val version = timebox.latestVersion
    val days = TimeboxReviewStats.daysProgress(version.startDate, version.endDate, tgClock.currentDate())
    val progress = TimeboxReviewStats.tasksProgress(tasks)
    div(
      cls("mb-4"),
      div(
        cls("flex flex-wrap items-center gap-2 mb-1"),
        pageLink(
          s"${tgDateFormater.asAmericanDate(version.startDate)} - ${tgDateFormater.asAmericanDate(version.endDate)}",
          TimeboxReviewPage(projectId, timebox.id)
        ),
        version.description.filter(_.nonEmpty).map(description => span(cls("text-body"), description))
      ),
      div(
        cls("flex flex-col gap-4"),
        progressBar(progress.done, progress.total, s"${progress.left} of ${progress.total} tasks left to do"),
        progressBar(days.elapsed, days.total, s"${days.left} of ${days.total} days left")
      )
    )
  }

  private def inFlightGroup(status: FindTaskStatusResp, tasks: Seq[FindTaskRespFull]): HtmlElement =
    div(
      cls("mb-4"),
      div(
        cls("mb-1"),
        TgBadge(text = s"${status.emoji} ${status.name} (${tasks.size})".trim, color = TgBadgeColor.Gray, bordered = true).element
      ),
      ul(
        tasks.map(task =>
          li(
            cls("flex flex-wrap items-center gap-2"),
            pageLink(task.name, EditTaskPage(projectId, task.id)),
            task.assignedTo.map(assignee => span(cls("text-body"), assignee.fullName))
          )
        )
      )
    )

  private def listOrEmpty[A](items: Signal[Seq[A]], empty: String)(render: A => HtmlElement): Modifier[HtmlElement] =
    children <-- items.map(values => if (values.isEmpty) Seq(emptyNote(empty)) else values.map(render))

  val element: HtmlElement =
    div(
      child.maybe <-- projectVar.signal.map(_.map(projectCard)),
      card(
        "Active milestones",
        div(listOrEmpty(milestonesVar.signal, "No active milestones.")((milestoneRow _).tupled))
      ),
      card(
        "Active timeboxes",
        div(listOrEmpty(timeboxesVar.signal, "No active timeboxes.")((timeboxRow _).tupled))
      ),
      card(
        "Tasks in flight",
        div(listOrEmpty(inFlightVar.signal, "No tasks in progress, testing, waiting or blocked.")((inFlightGroup _).tupled))
      )
    )
}
