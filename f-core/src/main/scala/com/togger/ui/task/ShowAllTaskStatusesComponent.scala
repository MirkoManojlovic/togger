package com.togger.ui.task

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.routes.Pages.{CreateTaskStatusPage, EditTaskStatusPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}
import org.scalajs.dom.HTMLTableCellElement

import java.util.UUID

case class TaskStatusFormState(
  id: UUID,
  projectId: UUID,
  name: String,
  description: String
)

class ShowAllTaskStatusesComponent(
  taskStatusService: TaskStatusFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(Seq.empty[TaskStatusFormState])

  spinnerService
    .runWithSpinner(
      taskStatusService
        .findByProjectId(projectId)
        .map(response =>
          response.map { success =>
            val dtos = success.body.get
            stateFormVar.set(
              dtos.map(dto =>
                TaskStatusFormState(
                  id = dto.id,
                  projectId = dto.projectId,
                  name = dto.name,
                  description = dto.description
                )
              )
            )

          }
        )
    )
    .unsafeRunAndForget()

  def createTh(content: String) =
    th(cls("px-6 py-3 font-medium"), content)

  def createTd(content: Modifier[ReactiveHtmlElement[HTMLTableCellElement]]) =
    td(cls("px-6 py-4"), content)

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
                        .sortBy(_.id)
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
                            createTd(dto.name),
                            createTd(dto.description),
                            createTd(
                              div(
                                iconButton(
                                  "Edit",
                                  TgIcons.editIcon(className = "w-4 h-4"),
                                  rowIconButtonCls,
                                  rowButtonMarginCls,
                                  onClick.mapToUnit --> (_ => router.route(EditTaskStatusPage(dto.projectId, dto.id)))
                                )
                              )
                            )
                          )
                        }
                    )
                  )
                )
              )
            )
          )
        )
        .element
    )
  }

}
