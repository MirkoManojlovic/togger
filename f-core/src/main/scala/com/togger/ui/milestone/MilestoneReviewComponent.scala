package com.togger.ui.milestone

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.FindMilestoneResp
import com.togger.routes.Pages.EditMilestonePage
import com.togger.routes.{FrontendPage, MilestoneHistoryParams, TaskListParams, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.{ChartColorBadgeColor, TgBadge, TgBadgeColor}
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
import com.togger.ui.toast.Toast

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

  private def summary(milestone: FindMilestoneResp): HtmlElement =
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
        milestone.endDate.map(dateBadge("End", _, TgBadgeColor.Success))
      ),
      Option.when(milestone.description.nonEmpty)(div(cls("text-sm text-body"), milestone.description))
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
        body = TgCard.HtmlCardContent(div(child.maybe <-- milestoneVar.signal.map(_.map(summary))))
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

  val element: HtmlElement =
    div(
      table.bindings,
      summaryCard,
      history.element.amend(cls("mb-4")),
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Tasks", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(table.element)
        )
        .element
    )
}
