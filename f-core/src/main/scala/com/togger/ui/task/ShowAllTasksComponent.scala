package com.togger.ui.task
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.markdown.TgMarkdown
import com.togger.routes.Pages._
import com.togger.task.DefaultTaskStatus.{Backlog, Done, Todo, WontDo}
import com.togger.routes.{FrontendPage, TaskListParams, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.modal.ModalFactory
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonCls}
import com.togger.ui.toast.Toast

import java.util.UUID

class ShowAllTasksComponent(
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  milestoneService: MilestoneFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  tgClock: TgClock,
  tgMarkdown: TgMarkdown,
  projectId: UUID,
  params: TaskListParams,
  val toast: Toast,
  modalFactory: ModalFactory
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  private val lookups = new TaskListLookups(
    projectId,
    projectService,
    taskStatusFeService,
    taskLabelFeService,
    milestoneService
  )
  import lookups.projectVar

  private val paramsVar = Var(params)

  private def showList(newParams: TaskListParams): Unit =
    router.route(ShowAllTasksPage(projectId, newParams))

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
    defaultDeselectedStatuses = Set(Todo, Backlog, Done, WontDo)
  )

  val element = {
    div(
      table.bindings,
      child <-- projectVar.signal.map(projectResp =>
        TgCard
          .headerNoFooter(
            header = TgCard.CardHeader.html(
              div(
                div(
                  cls("flex items-center justify-between"),
                  div(cls("mr-2"), projectResp.name),
                  div(
                    iconButton(
                      "Edit Project",
                      TgIcons.editIcon(className = "w-4 h-4"),
                      rowIconButtonCls,
                      rowButtonMarginCls,
                      onClick.mapToUnit --> (_ => router.route(EditProjectPage(projectResp.id)))
                    )
                  )
                )
              )
            ),
            body = TgCard.HtmlCardContent(
              div(
                tgMarkdown.toLaminarDiv(projectResp.description),
                Option.when(projectResp.projectLabels.nonEmpty)(
                  div(
                    cls("flex flex-wrap gap-1 mt-2"),
                    projectResp.projectLabels.map(ts => TgBadge(text = ts.name, color = TgBadgeColor.Indigo, bordered = true).element)
                  )
                )
              )
            )
          )
          .element
          .amend(cls("mb-4"))
      ),
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Tasks")
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard
            .HtmlCardContent(
              table.element
            )
        )
        .element
    )
  }

}
