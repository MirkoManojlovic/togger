package com.togger.ui.task

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.routes.Pages.{CreateTaskStatusPage, EditTaskStatusPage, ShowAllTaskStatusesPage}
import com.togger.routes.{FrontendPage, ListParams, TgRouter}
import com.togger.task.DefaultTaskStatus
import com.togger.toast.ToastMessages
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{
  headerIconButtonCls,
  iconButton,
  rowButtonMarginCls,
  rowIconButtonCls,
  rowIconButtonDangerCls
}
import com.togger.ui.toast.Toast

import java.util.UUID

case class TaskStatusFormState(
  id: UUID,
  projectId: UUID,
  name: String,
  emoji: String,
  description: String
)

class ShowAllTaskStatusesComponent(
  taskStatusService: TaskStatusFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService,
  projectId: UUID,
  params: ListParams,
  val toast: Toast,
  modalFactory: ModalFactory
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  val stateFormVar = Var(Seq.empty[TaskStatusFormState])

  private val paginationVar = Var(Option.empty[PaginationState])

  private def showList(newParams: ListParams): Unit =
    router.route(ShowAllTaskStatusesPage(projectId, newParams))

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    taskStatusService.findByProjectId(projectId, params.pageRequest).map {
      case Left(failure) => toast.error(failure)
      case Right(success) =>
        val response = success.body.get
        if (response.items.isEmpty && response.totalNumberOfPages > 0 && params.page > response.totalNumberOfPages)
          showList(params.atPage(response.totalNumberOfPages.toInt, params.size))
        else {
          stateFormVar.set(
            response.items.map(dto =>
              TaskStatusFormState(
                id = dto.id,
                projectId = dto.projectId,
                name = dto.name,
                emoji = dto.emoji,
                description = dto.description
              )
            )
          )
          paginationVar.set(Some(PaginationState.fromResponse(response)))
        }
    }

  private def deleteTaskStatus(state: TaskStatusFormState): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete task status '${state.name}'?"),
      title = "Delete task status",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(taskStatusService.deleteTaskStatusById(state.id))
        .toastMessageCodeBlock("Task status deleted successfully") {
          spinnerService.runWithSpinner(loadPage()).unsafeRunAndForget()
        }
        .unsafeRunAndForget()
      close()
    }

  private def isDone(state: TaskStatusFormState): Boolean = DefaultTaskStatus.isTerminalName(state.name)

  spinnerService.runWithSpinner(loadPage()).unsafeRunAndForget()

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Task statuses"),
              iconButton(
                "Create Task Status",
                TgIcons.plusIcon(className = "w-4 h-4"),
                headerIconButtonCls,
                "mr-2",
                router.navigateTo(CreateTaskStatusPage(projectId))
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard.HtmlCardContent(
            div(
              child <-- stateFormVar.signal.map(dtos =>
                div(
                  cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
                  table(
                    cls("w-full text-sm text-left rtl:text-right text-body"),
                    thead(
                      cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
                      tr(
                        createTh("id"),
                        createTh("name"),
                        createTh("description"),
                        createTh("Action")
                      )
                    ),
                    tbody(
                      dtos
                        .zipWithIndex
                        .map { case (dto, index) =>
                          val rowCls = if (index % 2 == 0) {
                            "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"
                          } else {
                            "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"
                          }
                          tr(
                            cls(rowCls),
                            //                textAlign.center,
                            createTd(dto.id.toString),
                            createTd(s"${dto.emoji} ${dto.name}".trim),
                            createTd(dto.description),
                            createTd(
                              div(
                                iconButton(
                                  "Edit",
                                  TgIcons.editIcon(className = "w-4 h-4"),
                                  rowIconButtonCls,
                                  rowButtonMarginCls,
                                  onClick.mapToUnit --> (_ => router.route(EditTaskStatusPage(dto.projectId, dto.id)))
                                ),
                                Option.unless(isDone(dto))(
                                  iconButton(
                                    "Delete",
                                    TgIcons.trashIcon(className = "w-4 h-4"),
                                    rowIconButtonDangerCls,
                                    rowButtonMarginCls,
                                    onClick.mapToUnit --> (_ => deleteTaskStatus(dto))
                                  )
                                )
                              )
                            )
                          )
                        }
                    )
                  )
                )
              ),
              TgPaginationComponent(
                paginationVar.signal,
                (newPage, newSize) => showList(params.atPage(newPage, newSize))
              )
            )
          )
        )
        .element
    )
  }

}
