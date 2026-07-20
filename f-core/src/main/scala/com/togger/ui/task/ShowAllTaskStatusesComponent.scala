package com.togger.ui.task

import cats.effect.unsafe.IORuntime
import com.togger.components.button.TgHtmlButton
import com.togger.routes.Pages.{CreateOrganizationPage, CreateTaskStatusPage, EditTaskStatusPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLTableCellElement

import java.util.UUID

case class TaskStatusFormState(
  id: UUID,
  projectId: Long,
  name: String,
  description: String
)

class ShowAllTaskStatusesComponent(
  taskStatusService: TaskStatusFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService,
  projectId: Long
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
              TgHtmlButton
                .extraSmall("Create")
                .amend(
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
                                button(
                                  `type`("button"),
                                  TgIcons.editIcon(className = "w-4 h-4 me-1.5 -ms-0.5"),
                                  "Edit",
                                  cls(
                                    "inline-flex items-center text-white bg-brand hover:bg-brand-strong box-border border border-transparent focus:ring-4 focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base text-sm px-3 py-2 focus:outline-none mr-2"
                                  ),
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
