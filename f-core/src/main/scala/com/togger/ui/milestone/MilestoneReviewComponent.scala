package com.togger.ui.milestone

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.markdown.TgMarkdown
import com.togger.dto.{FindMilestoneResp, FindTaskRespFull, FindTaskStatusResp}
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.routes.Pages.EditMilestonePage
import com.togger.routes.{FrontendPage, MilestoneHistoryParams, TaskListParams, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.{
  ChartColorBadgeColor,
  TgBadge,
  TgBadgeColor,
  TgProgressColor,
  TgProgressComponent,
  TgProgressLabel,
  TgProgressSize
}
import com.togger.ui.modal.ModalFactory
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonCls}
import com.togger.ui.task.{
  TaskFeService,
  TaskLabelFeService,
  TaskListLookups,
  TaskListTable,
  TaskStatusFeService,
  TgDateFormater
}
import com.togger.ui.timebox.TimeboxReviewStats
import com.togger.ui.toast.Toast

import java.time.DayOfWeek
import java.util.UUID

/** A milestone with all of its tasks, in the same table the task list shows. */
class MilestoneReviewComponent(
  milestoneService: MilestoneFeService,
  milestoneDateService: MilestoneDateFeService,
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  tgMarkdown: TgMarkdown,
  tgClock: TgClock,
  projectId: UUID,
  milestoneId: UUID,
  toast: Toast,
  modalFactory: ModalFactory
)(implicit ioRuntime: IORuntime) {

  private val milestoneVar = Var(Option.empty[FindMilestoneResp])

  spinnerService
    .runWithSpinner(milestoneService.findMilestoneById(milestoneId))
    .map(_.fold(error => println(error), success => milestoneVar.set(success.body)))
    .unsafeRunAndForget()

  private val tasksVar = Var(Seq.empty[FindTaskRespFull])

  // The table is paged, so the stats read every task of the milestone on their own (bounded by PaginatedLoader).
  private def loadTasks(): Unit =
    spinnerService
      .runWithSpinner(taskService.findAllByMilestone(projectId, milestoneId))
      .map(_.fold(error => println(error), success => tasksVar.set(success.body.get)))
      .unsafeRunAndForget()

  loadTasks()

  private val lookups =
    new TaskListLookups(projectId, projectService, taskStatusFeService, taskLabelFeService, milestoneService)

  // The table is paged, searched and filtered here, so its params are kept in a Var rather than in the URL.
  private val paramsVar = Var(TaskListParams.default)

  private def showList(newParams: TaskListParams): Unit = {
    paramsVar.set(newParams)
    spinnerService.runWithSpinner(table.loadPage()).unsafeRunAndForget()
  }

  private val table = new TaskListTable(
    taskService,
    lookups,
    router,
    spinnerService,
    tgDateFormater,
    tgClock,
    projectId,
    paramsVar,
    showList,
    toast,
    modalFactory,
    defaultDeselectedStatuses = Set.empty,
    lockedMilestoneId = Some(milestoneId)
  )

  private def dateBadge(label: String, date: java.time.LocalDate, color: TgBadgeColor = TgBadgeColor.Gray) =
    TgBadge(text = s"$label ${tgDateFormater.asAmericanDateWithDayAbrv(date)}", color = color, bordered = true).element

  /** The work days left until the due date, or "Overdue" once it has passed. */
  private def daysLeftBadge(milestone: FindMilestoneResp, workDays: Set[DayOfWeek]): HtmlElement = {
    val today = tgClock.currentDate()
    val (text, color) =
      if (milestone.dueDate.isBefore(today)) ("Overdue", TgBadgeColor.Red)
      else (s"${WorkDayCalendar.countWorkDays(today, milestone.dueDate, workDays)} days left", TgBadgeColor.Gray)
    TgBadge(text = text, color = color, bordered = true).element
  }

  private def summary(milestone: FindMilestoneResp, workDays: Set[DayOfWeek]): HtmlElement =
    div(
      div(
        cls("flex flex-wrap items-center gap-1.5 mb-2"),
        TgBadge(
          text = milestone.status.toStringWithEmoji,
          color = ChartColorBadgeColor.of(milestone.status),
          bordered = true
        ).element,
        dateBadge("Start", milestone.startDate),
        dateBadge("Due", milestone.dueDate),
        milestone.endDate.fold(daysLeftBadge(milestone, workDays))(dateBadge("End", _, TgBadgeColor.Success))
      ),
      Option.when(milestone.description.nonEmpty)(tgMarkdown.toLaminarDiv(milestone.description))
    )

  private val summaryCard: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(
          div(
            cls("flex items-center justify-between"),
            div(cls("mr-2"), child.text <-- milestoneVar.signal.map(_.fold("Milestone")(_.name))),
            iconButton(
              "Edit milestone",
              TgIcons.editIcon(className = "w-4 h-4"),
              rowIconButtonCls,
              rowButtonMarginCls,
              onClick.mapToUnit --> (_ => router.route(EditMilestonePage(projectId, milestoneId)))
            )
          )
        ),
        body = TgCard.HtmlCardContent(
          div(
            child.maybe <-- milestoneVar.signal
              .combineWith(lookups.projectVar.signal.map(_.workDays.map(DayOfWeek.of)))
              .map { case (milestone, workDays) => milestone.map(summary(_, workDays)) }
          )
        )
      )
      .element
      .amend(cls("mb-4"))

  private val history = new MilestoneHistoryComponent(
    taskService,
    milestoneService,
    milestoneDateService,
    projectService,
    router,
    spinnerService,
    tgDateFormater,
    tgClock,
    projectId,
    MilestoneHistoryParams.default,
    lockedMilestoneId = Some(milestoneId)
  )

  private def progressBar(value: Int, max: Int, title: String): HtmlElement =
    TgProgressComponent(
      value = value,
      max = max.max(1),
      color = TgProgressColor.Success,
      size = TgProgressSize.Large,
      label = TgProgressLabel.Outside(title)
    ).element

  private def statCard(title: String, content: HtmlElement): HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText(title, TgCard.CardHeaderSize.H4),
        body = TgCard.HtmlCardContent(content)
      )
      .element
      .amend(cls("w-full"))

  private def stat(label: String, value: Int, color: TgBadgeColor = TgBadgeColor.Gray): HtmlElement =
    div(
      cls("flex flex-col items-center gap-1 min-w-20"),
      span(cls("text-2xl font-semibold text-heading"), value.toString),
      TgBadge(text = label, color = color, bordered = true).element
    )

  private def statsRow(
    milestone: FindMilestoneResp,
    tasks: Seq[FindTaskRespFull],
    statuses: Seq[FindTaskStatusResp]
  ): HtmlElement = {
    val today = tgClock.currentDate()
    val days = TimeboxReviewStats.daysProgress(milestone.startDate, milestone.dueDate, today)
    val progress = TimeboxReviewStats.tasksProgress(tasks)
    div(
      cls("grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4"),
      statCard(
        "Progress",
        div(
          cls("flex flex-col gap-4"),
          progressBar(progress.done, progress.total, s"${progress.left} of ${progress.total} tasks left to do"),
          progressBar(days.elapsed, days.total, s"${days.left} of ${days.total} days left until the due date")
        )
      ),
      statCard(
        "Tasks",
        div(
          cls("flex flex-col gap-4"),
          div(
            cls("flex flex-wrap items-start justify-around gap-4"),
            stat("Total", tasks.size),
            stat("Done", progress.done, TgBadgeColor.Success),
            stat("Left", progress.left, TgBadgeColor.Blue)
          ),
          div(
            cls("flex flex-wrap items-start justify-around gap-4"),
            statuses.map(status => stat(status.badgeText, tasks.count(_.taskStatus.id == status.id), status.badgeColor))
          )
        )
      )
    )
  }

  val element: HtmlElement =
    div(
      table.bindings,
      summaryCard,
      child.maybe <-- milestoneVar.signal
        .combineWith(tasksVar.signal, lookups.statusesVar.signal.map(_.taskStatusesSorted))
        .map { case (milestone, tasks, statuses) => milestone.map(statsRow(_, tasks, statuses)) },
      history.element.amend(cls("mb-4")),
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Tasks", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(table.element)
        )
        .element
    )
}
