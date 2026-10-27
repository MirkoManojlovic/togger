package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.markdown.TgMarkdown
import com.togger.dto.{FindMilestoneResp, FindMilestoneSummaryResp, FindTaskRespFull, FindTaskStatusResp}
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.routes.Pages.EditMilestonePage
import com.togger.routes.{FrontendPage, TaskListParams, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.{
  ChartColorBadgeColor,
  TgBadge,
  TgBadgeColor
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
  milestoneSummaryService: MilestoneSummaryFeService,
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

  private val tasksVar = Var(Seq.empty[FindTaskRespFull])

  private val summariesVar = Var(Seq.empty[FindMilestoneSummaryResp])

  // Newest period first.
  private def loadSummaries(): IO[Unit] =
    milestoneSummaryService
      .findAllByMilestoneId(milestoneId)
      .map(
        _.fold(
          error => println(error),
          success => summariesVar.set(success.body.get.sortBy(_.startDate.toEpochDay)(Ordering[Long].reverse))
        )
      )

  private def loadMilestone(): IO[Unit] =
    milestoneService
      .findMilestoneById(milestoneId)
      .map(_.fold(error => println(error), success => milestoneVar.set(success.body)))

  // The table is paged, so the stats read every task of the milestone on their own (bounded by PaginatedLoader).
  private def loadTasks(): IO[Unit] =
    taskService
      .findAllByMilestone(projectId, milestoneId)
      .map(_.fold(error => println(error), success => tasksVar.set(success.body.get)))

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
    lockedMilestoneId = Some(milestoneId),
    loadOnCreate = false
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
        cls("flex flex-wrap items-center gap-1.5 mb-4"),
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
            // the card header leaves 1.5rem below it, which is more than the badges need
            cls("-mt-3"),
            child.maybe <-- milestoneVar.signal
              .combineWith(lookups.projectVar.signal.map(_.workDays.map(DayOfWeek.of)))
              .map { case (milestone, workDays) => milestone.map(summary(_, workDays)) }
          )
        )
      )
      .element
      .amend(cls("mb-4"))

  private def summaryRow(summary: FindMilestoneSummaryResp): HtmlElement =
    div(
      cls("mb-4 pb-4 border-b border-default last:border-b-0 last:pb-0 last:mb-0"),
      div(
        cls("flex flex-wrap items-center gap-1.5 mb-2"),
        dateBadge("Start", summary.startDate),
        dateBadge("End", summary.endDate)
      ),
      Option.when(summary.summary.nonEmpty)(tgMarkdown.toLaminarDiv(summary.summary))
    )

  private val summariesCard: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Summaries", TgCard.CardHeaderSize.H4),
        body = TgCard.HtmlCardContent(
          div(
            children <-- summariesVar.signal.map(_.map(summaryRow)),
            child.maybe <-- summariesVar.signal.map(summaries =>
              Option.when(summaries.isEmpty)(div(cls("text-sm text-body"), "No summaries yet."))
            )
          )
        )
      )
      .element
      .amend(cls("w-full mb-4"))

  private val history = new SingleMilestoneHistoryComponent(
    taskService,
    milestoneService,
    milestoneDateService,
    projectService,
    router,
    tgDateFormater,
    tgClock,
    projectId,
    milestoneId
  )

  // One spinner for the whole page, so it stays until the last part is loaded.
  spinnerService
    .runWithSpinner(
      for {
        _ <- loadMilestone()
        _ <- loadTasks()
        _ <- loadSummaries()
        _ <- table.load()
        _ <- history.load()
      } yield ()
    )
    .unsafeRunAndForget()

  private def statsCard(
    milestone: FindMilestoneResp,
    tasks: Seq[FindTaskRespFull],
    statuses: Seq[FindTaskStatusResp]
  ): HtmlElement = {
    val days = TimeboxReviewStats.daysProgress(milestone.startDate, milestone.dueDate, tgClock.currentDate())
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Tasks", TgCard.CardHeaderSize.H4),
        body = TgCard.HtmlCardContent(MilestoneTaskRows(tasks, statuses, days))
      )
      .element
      .amend(cls("w-full mb-4"))
  }

  val element: HtmlElement =
    div(
      table.bindings,
      summaryCard,
      summariesCard,
      child.maybe <-- milestoneVar.signal
        .combineWith(tasksVar.signal, lookups.statusesVar.signal.map(_.taskStatusesSorted))
        .map { case (milestone, tasks, statuses) => milestone.map(statsCard(_, tasks, statuses)) },
      history.element.amend(cls("mb-4")),
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Tasks", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(table.element)
        )
        .element
    )
}
