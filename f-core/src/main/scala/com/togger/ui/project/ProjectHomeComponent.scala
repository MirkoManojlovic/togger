package com.togger.ui.project

import cats.effect.IO
import cats.syntax.all._
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.{SearchAccountDto, FindMilestoneResp, FindMilestoneSummaryResp, FindProjectResp, FindTaskRespFull, FindTaskStatusResp, FindTimeboxResp}
import com.togger.markdown.TgMarkdown
import com.togger.milestone.MilestoneStatus
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgTab, TgTabs, TgBadge, TgBadgeColor}
import com.togger.ui.milestone.{MilestoneFeService, MilestoneSummaryFeService, MilestoneTabs, MilestoneTaskRows}
import com.togger.ui.modal.ModalFactory
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskStatusesState, TaskFeService, TaskPreviewComponent, TaskStatusFeService, TgDateFormater}
import com.togger.ui.timebox.gantt.TimeboxReviewGanttComponent
import com.togger.ui.timebox.{TimeboxFeService, TimeboxReviewStats}
import com.togger.dto.{MilestoneFilter, TaskFilter}

import java.time.DayOfWeek
import java.util.UUID

class ProjectHomeComponent(
  projectService: ProjectService,
  milestoneService: MilestoneFeService,
  milestoneSummaryService: MilestoneSummaryFeService,
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
  private val summariesVar = Var(Map.empty[UUID, Seq[FindMilestoneSummaryResp]])
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

  /** The summaries of each active milestone, newest period first; a failure leaves the milestone without summaries. */
  private def loadSummaries(): IO[Unit] =
    milestonesVar.now().map(_._1).toList.traverse { milestone =>
      milestoneSummaryService.findAllByMilestoneId(milestone.id).map { response =>
        val summaries = response.fold(_ => Seq.empty, _.body.getOrElse(Seq.empty))
        milestone.id -> summaries.sortBy(_.startDate.toEpochDay)(Ordering[Long].reverse)
      }
    }.map(loaded => summariesVar.set(loaded.toMap))

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
        _ <- loadSummaries()
        _ <- loadTimeboxes()
        _ <- loadInFlightTasks()
      } yield ()
    )
    .unsafeRunAndForget()

  private val milestoneTabs = new MilestoneTabs(tgDateFormater, tgMarkdown)

  private val projectInfoCard = new ProjectInfoCard(router, tgMarkdown)

  private def card(title: String, content: HtmlElement): HtmlElement =
    card(TgCard.CardHeader.plainText(title, TgCard.CardHeaderSize.H3), content)

  private def card(header: TgCard.CardHeader, content: HtmlElement): HtmlElement =
    TgCard
      .headerNoFooter(header = header, body = TgCard.HtmlCardContent(content))
      .element
      .amend(cls("mb-4"))

  private def pageLink(text: String, page: FrontendPage): HtmlElement =
    a(cls("text-fg-brand hover:underline cursor-pointer"), text, router.navigateTo(page))

  private def emptyNote(text: String): HtmlElement = div(cls("text-body"), text)


  private def milestoneRow(milestone: FindMilestoneResp, tasks: Seq[FindTaskRespFull]): HtmlElement = {
    val days = TimeboxReviewStats.daysProgress(milestone.startDate, milestone.dueDate, tgClock.currentDate())
    val statuses = TaskStatusesState(allStatusesVar.now()).taskStatusesSorted
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
      milestoneTabs(
        MilestoneTaskRows(tasks, statuses, days, milestone.dueDate, tgClock.currentDate(), workDays),
        summariesVar.signal.map(_.getOrElse(milestone.id, Seq.empty)),
        Val(workDays)
      )
    )
  }

  private def timeboxRow(timebox: FindTimeboxResp, tasks: Seq[FindTaskRespFull]): HtmlElement = {
    val version = timebox.latestVersion
    val gantt = new TimeboxReviewGanttComponent(
      Val(tasks),
      Val(workDays),
      Val(Some((version.startDate, version.endDate))),
      Val(projectVar.now().map(_.members.map(_.id)).getOrElse(Seq.empty)),
      Val(allMilestonesVar.now()),
      tgDateFormater,
      tgMarkdown,
      router,
      tgClock
    )
    div(
      cls("mb-4 p-4 border border-default-medium rounded-base"),
      div(
        cls("mb-3"),
        pageLink(
          s"${tgDateFormater.asAmericanDate(version.startDate)} - ${tgDateFormater.asAmericanDate(version.endDate)}",
          TimeboxReviewPage(projectId, timebox.id)
        ),
        version.description.filter(_.nonEmpty).map(description => tgMarkdown.toLaminarDiv(description).amend(cls("mt-1")))
      ),
      gantt.element,
      gantt.detailElement
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
      title = "Task preview",
      maxWidthCls = "max-w-[112rem]"
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

  private def sectionTitle(text: String, marginCls: String = "mb-2"): HtmlElement =
    div(cls("font-semibold text-heading"), cls(marginCls), text)

  private val sectionBorderCls = "p-4 border border-default-medium rounded-base"

  private def generalSection[A](title: String, items: Seq[A], empty: String, bordered: Boolean = false)(
    render: A => HtmlElement
  ): HtmlElement =
    div(
      cls("mb-4"),
      cls(if (bordered) sectionBorderCls else ""),
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
        cls(sectionBorderCls),
        sectionTitle("Active tasks", marginCls = "mb-4"),
        activeTasks(
          inFlight.flatMap(_._2),
          ActiveTasksConfig("No tasks in progress, testing, waiting or blocked.")
        )
      ),
      generalSection("Active timeboxes", timeboxes, "No active timeboxes.", bordered = true)((timeboxRow _).tupled),
      generalSection("Current work", milestones, "No current work.")((milestoneRow _).tupled)
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
      sectionTitle("Current work"),
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
      child.maybe <-- projectVar.signal.map(_.map(projectInfoCard.element(_, ProjectTitle.PlainText))),
      card(
        "Current work",
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
