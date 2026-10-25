package com.togger.ui.project

import cats.effect.IO
import cats.syntax.all._
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.{SearchAccountDto, FindMilestoneResp, FindProjectResp, FindTaskRespFull, FindTaskStatusResp, FindTimeboxResp}
import com.togger.markdown.TgMarkdown
import com.togger.milestone.MilestoneStatus
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgTab, TgTabs, TgBadge, TgBadgeColor, TgProgressColor, TgProgressComponent, TgProgressLabel, TgProgressSize}
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.modal.ModalFactory
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskStatusesState, TaskFeService, TaskPreviewComponent, TaskStatusFeService, TgDateFormater}
import com.togger.ui.timebox.{TimeboxFeService, TimeboxReviewStats}
import com.togger.dto.{MilestoneFilter, TaskFilter}

import java.time.DayOfWeek
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
  modalFactory: ModalFactory,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) {

  private val projectVar = Var(Option.empty[FindProjectResp])
  private val milestonesVar = Var(Seq.empty[(FindMilestoneResp, Seq[FindTaskRespFull])])
  private val allMilestonesVar = Var(Seq.empty[FindMilestoneResp])
  private val timeboxesVar = Var(Seq.empty[(FindTimeboxResp, Seq[FindTaskRespFull])])
  private val allStatusesVar = Var(Seq.empty[FindTaskStatusResp])
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

  /** Every milestone of the project, to name the milestone of a task that is not in an active one. */
  private def loadAllMilestones(): IO[Unit] =
    PaginatedLoader.loadAll(milestoneService.findByProjectId(projectId, MilestoneFilter.empty, _)).flatMap {
      case Left(failure)  => IO.println(failure)
      case Right(success) => IO(allMilestonesVar.set(success.body.getOrElse(Seq.empty)))
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
        allStatusesVar.set(success.body.getOrElse(Seq.empty))
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
        _ <- loadAllMilestones()
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

  private def stat(label: String, value: Int, color: TgBadgeColor = TgBadgeColor.Gray): HtmlElement =
    div(
      cls("flex flex-col items-center gap-1 min-w-16"),
      span(cls("text-xl font-semibold text-heading"), value.toString),
      TgBadge(text = label, color = color, bordered = true).element
    )

  private def milestoneRow(milestone: FindMilestoneResp, tasks: Seq[FindTaskRespFull]): HtmlElement = {
    val progress = TimeboxReviewStats.tasksProgress(tasks)
    val days = TimeboxReviewStats.daysProgress(milestone.startDate, milestone.dueDate, tgClock.currentDate())
    val statuses = TaskStatusesState(allStatusesVar.now()).taskStatusesSorted
      .map(status => status -> tasks.count(_.taskStatus.id == status.id))
      .filter(_._2 > 0)
    div(
      cls("mb-4 p-4 border border-default-medium rounded-base"),
      div(
        cls("flex flex-wrap items-center gap-2 mb-3"),
        pageLink(milestone.name, MilestoneReviewPage(projectId, milestone.id)),
        TgBadge(
          text = s"Due ${tgDateFormater.asAmericanDateWithDayAbrv(milestone.dueDate)}",
          color = TgBadgeColor.Gray,
          bordered = true
        ).element
      ),
      div(
        cls("flex flex-col md:flex-row md:items-center gap-4"),
        div(
          cls("flex flex-col gap-4 md:w-1/2 p-3 border border-default rounded-base"),
          progressBar(progress.done, progress.total, s"${progress.left} of ${progress.total} tasks left to do"),
          progressBar(days.elapsed, days.total, s"${days.left} of ${days.total} days left until the due date")
        ),
        div(
          cls("flex flex-col gap-3 md:w-1/2 p-3 border border-default rounded-base"),
          div(
            cls("flex flex-wrap items-start justify-around gap-3"),
            stat("Total", tasks.size),
            stat("Done", progress.done, TgBadgeColor.Success),
            stat("Left", progress.left, TgBadgeColor.Blue)
          ),
          Option.when(statuses.nonEmpty)(
            div(
              cls("flex flex-wrap items-start justify-around gap-3"),
              statuses.map { case (status, count) => stat(status.badgeText, count, status.badgeColor) }
            )
          )
        )
      )
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

  private def workDays: Set[DayOfWeek] =
    projectVar
      .now()
      .map(_.workDays.map(DayOfWeek.of))
      .filter(_.nonEmpty)
      .getOrElse(WorkDayCalendar.DefaultWorkDays)

  private val selectedTabVar = Var(ProjectHomeComponent.GeneralTabId)

  private def previewTask(task: FindTaskRespFull): Unit =
    modalFactory.previewModal(
      new TaskPreviewComponent(task, workDays, router, tgDateFormater, tgMarkdown, tgClock, showName = true, allMilestonesVar.now()).element,
      title = "Task preview"
    )

  private def activeTasks(tasks: Seq[FindTaskRespFull], config: ActiveTasksConfig): HtmlElement =
    new ActiveTasksComponent(
      router,
      tgDateFormater,
      tgClock,
      projectId,
      allMilestonesVar.now(),
      workDays,
      tasks,
      config,
      previewTask
    ).element

  private def sectionTitle(text: String): HtmlElement = div(cls("font-semibold text-heading mb-2"), text)

  private def generalSection[A](title: String, items: Seq[A], empty: String)(render: A => HtmlElement): HtmlElement =
    div(
      cls("mb-4"),
      sectionTitle(title),
      if (items.isEmpty) Seq(emptyNote(empty)) else items.map(render)
    )

  private def generalPanel(
    milestones: Seq[(FindMilestoneResp, Seq[FindTaskRespFull])],
    timeboxes: Seq[(FindTimeboxResp, Seq[FindTaskRespFull])],
    inFlight: Seq[(FindTaskStatusResp, Seq[FindTaskRespFull])]
  ): HtmlElement =
    div(
      div(
        cls("mb-4"),
        sectionTitle("Tasks"),
        activeTasks(
          inFlight.flatMap(_._2),
          ActiveTasksConfig("No tasks in progress, testing, waiting or blocked.")
        )
      ),
      generalSection("Active timeboxes", timeboxes, "No active timeboxes.")((timeboxRow _).tupled),
      generalSection("Active milestones", milestones, "No active milestones.")((milestoneRow _).tupled)
    )

  private def memberPanel(
    member: SearchAccountDto,
    milestones: Seq[(FindMilestoneResp, Seq[FindTaskRespFull])],
    inFlight: Seq[(FindTaskStatusResp, Seq[FindTaskRespFull])]
  ): HtmlElement = {
    val memberMilestones = ProjectHomeStats.milestonesOf(member, milestones)
    div(
      sectionTitle("Working on"),
      div(
        cls("mb-4"),
        activeTasks(
          ProjectHomeStats.inFlightOf(member, inFlight).flatMap(_._2),
          ActiveTasksConfig("Not working on any task.", showMember = false)
        )
      ),
      sectionTitle("Active milestones"),
      div(
        if (memberMilestones.isEmpty) Seq(emptyNote("Not part of any active milestone."))
        else memberMilestones.map((milestoneRow _).tupled)
      )
    )
  }

  private def milestonePanel(milestone: FindMilestoneResp, tasks: Seq[FindTaskRespFull]): HtmlElement =
    div(
      sectionTitle("Active tasks"),
      div(
        cls("mb-4"),
        activeTasks(tasks, ActiveTasksConfig("No active tasks.", showMilestone = false))
      ),
      milestoneRow(milestone, tasks)
    )

  private def memberTabId(member: SearchAccountDto): String = s"member-${member.id}"

  private def milestoneTabId(milestone: FindMilestoneResp): String = s"milestone-${milestone.id}"

  // The general and the member tabs in one row, the milestones under them; both rows share the selection, so it is
  // put back on the general tab when a reload takes the selected tab away.
  private def activityTabs(
    members: Seq[SearchAccountDto],
    milestones: Seq[(FindMilestoneResp, Seq[FindTaskRespFull])],
    timeboxes: Seq[(FindTimeboxResp, Seq[FindTaskRespFull])],
    inFlight: Seq[(FindTaskStatusResp, Seq[FindTaskRespFull])]
  ): HtmlElement = {
    val memberTabList =
      TgTab(ProjectHomeComponent.GeneralTabId, "General", generalPanel(milestones, timeboxes, inFlight)) +:
        members.sortBy(_.fullName).map { member =>
          TgTab(
            memberTabId(member),
            member.fullName,
            memberPanel(member, milestones, inFlight),
            icon = Some(TgIcons.userIcon)
          )
        }
    val milestoneTabList = milestones.map { case (milestone, tasks) =>
      TgTab(milestoneTabId(milestone), milestone.name, milestonePanel(milestone, tasks))
    }
    val memberTabs = new TgTabs(memberTabList, selectedTabVar)
    val milestoneRowTabs = Option.when(milestoneTabList.nonEmpty)(new TgTabs(milestoneTabList, selectedTabVar))
    val selectableIds = (memberTabList ++ milestoneTabList).map(_.id).toSet
    div(
      onMountCallback(_ =>
        if (!selectableIds.contains(selectedTabVar.now())) selectedTabVar.set(ProjectHomeComponent.GeneralTabId)
      ),
      memberTabs.tabBar,
      milestoneRowTabs.map(row => div(div(cls("mt-3 mb-1 text-sm text-body"), "Milestones"), row.tabBar)),
      memberTabs.panels,
      milestoneRowTabs.toSeq.flatMap(_.panels)
    )
  }

  val element: HtmlElement =
    div(
      child.maybe <-- projectVar.signal.map(_.map(projectCard)),
      card(
        "Active milestones",
        div(
          child <-- projectVar.signal
            .combineWith(
              milestonesVar.signal,
              timeboxesVar.signal,
              inFlightVar.signal,
              allMilestonesVar.signal,
              allStatusesVar.signal
            )
            .map { case (project, milestones, timeboxes, inFlight, _, _) =>
              activityTabs(project.map(_.members).getOrElse(Seq.empty), milestones, timeboxes, inFlight)
            }
        )
      )
    )
}

object ProjectHomeComponent {
  val GeneralTabId = "general"
}
