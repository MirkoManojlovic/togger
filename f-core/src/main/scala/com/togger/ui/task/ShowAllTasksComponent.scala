package com.togger.ui.task
import cats.effect.unsafe.IORuntime
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{FindProjectResp, FindTaskRespFull}
import com.togger.routes.Pages.{CreateTaskPage, EditOrganizationPage, EditProjectPage, EditTaskPage, ShowAllTaskStatusesPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLTableCellElement

class ShowAllTasksComponent(
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  dateFormater: TgDateFormater,
  spinnerService: SpinnerService,
  accountService: AccountService,
  projectId: Long
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(Seq.empty[FindTaskRespFull])
  val statusesVar = Var(TaskStatusesState()) // TODO: don't need this
  private val projectVar: Var[FindProjectResp] = Var(
    FindProjectResp(
      id = -1,
      organizationId = -1,
      name = "",
      description = "",
      members = Seq.empty
    )
  )

  spinnerService
    .runWithSpinner {

      val projectCall =
        projectService
          .findById(projectId)
          .map(
            _.fold(
              e => println(e),
              success => projectVar.set(success.body.get)
            )
          )

      val taskStatusCall = taskStatusFeService
        .findByProjectId(projectId)
        .map(response => response.map(success => statusesVar.set(TaskStatusesState(success.body.get))))

      val taskCall = taskService
        .findByProjectId(projectId)
        .map(response => response.map(success => stateFormVar.set(success.body.get)))

      projectCall.flatMap(_ => taskStatusCall).flatMap(_ => taskCall)
    }
    .unsafeRunAndForget()


  def createTh(content: String) =
    th(cls("px-6 py-3 font-medium"), content)

  def createTd(content: Modifier[ReactiveHtmlElement[HTMLTableCellElement]]) =
    td(cls("px-6 py-4"), content)

  val element = {
    div(
      child <-- projectVar.signal.map(projectResp =>
        TgCard
          .headerNoFooter(
            header = TgCard.CardHeader.html(
              div(
                a(
                  cls("font-medium text-fg-brand hover:underline"),
                  projectResp.name,
                  router.navigateTo(EditProjectPage(projectResp.id))
                )
              )
            ),
            body = TgCard.HtmlCardContent(div(projectResp.description))
          )
          .element
          .amend(cls("mb-4"))
      ),
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Tasks"),
              div(
                cls("flex"),
//                dumpComponent.element.amend(cls("mb-4 w-xs mr-4")),
                TgHtmlButton
                  .extraSmall("Create Task")
                  .amend(
                    cls("mr-2"),
                    router.navigateTo(CreateTaskPage(projectId))
                  ),
                TgHtmlButton
                  .extraSmall("Show Task Statuses")
                  .amend(
                    cls("mr-2"),
                    router.navigateTo(ShowAllTaskStatusesPage(projectId))
                  )
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
                        createTh("Id"),
                        createTh("Name"),
                        createTh("Description"),
                        createTh("Assignee"),
//                        createTh("created_by"),
//                        createTh("created_at"),
//                        createTh("updated_at"),
                        createTh("Status"),
                        createTh("Action")
                      )
                    ),
                    tbody(
                      dtos
                        .sortBy(_.taskStatus.id)
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
                            createTd(dto.id),
                            createTd(dto.name),
                            createTd(dto.description),
//                            createTd(dto.assignedTo.map(_.fullName).getOrElse("No one")),
                            createTd {
                              val mirko = dto.assignedTo.map(_.fullName).getOrElse("No")
                              mirko
                            },
//                            createTd(dto.createdBy.fullName),
//                            createTd(dateFormater.asEuropeanDate(dto.createdAt.toLocalDate())),
//                            createTd(dateFormater.asEuropeanDate(dto.updatedAt.toLocalDate())),
                            createTd(statusesVar.now().idToName(dto.taskStatus.id)),
                            createTd(
                              div(
                                button(
                                  `type`("button"),
                                  TgIcons.editIcon(className = "w-4 h-4 me-1.5 -ms-0.5"),
                                  "Edit",
                                  cls(
                                    "inline-flex items-center text-white bg-brand hover:bg-brand-strong box-border border border-transparent focus:ring-4 focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base text-sm px-3 py-2 focus:outline-none mr-2"
                                  ),
                                  onClick.mapToUnit --> (_ => router.route(EditTaskPage(dto.projectId, dto.id)))
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
