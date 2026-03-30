package com.togger.ui.project

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.toTraverseOps
import com.togger.dto.organization.FindOrganizationResp
import com.togger.dto.{FindProjectResp, FindTaskRespFull, SearchAccountDto}
import com.togger.routes.Pages.{EditOrganizationPage, EditProjectPage, EditTaskPage, ShowAllTasksPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.organization.{OrganizationServiceFe, TgIcons}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskFeService, TgDateFormater}
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLTableCellElement

import java.time.{LocalDate, ZoneOffset}

case class ShowAllProjectsState(
  info: Seq[ProjectInfo] = Seq.empty
)

case class ProjectInfo(
  project: FindProjectResp,
  tasks: Seq[FindTaskRespFull]
)

class ShowAllProjectsStatusComponent(
  projectService: ProjectService,
  organizationServiceFe: OrganizationServiceFe,
  taskFeService: TaskFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService,
  tgDateFormater: TgDateFormater,
  organizationId: Long
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(ShowAllProjectsState())
  private val organizationVar: Var[FindOrganizationResp] = Var(
    FindOrganizationResp(
      id = -1,
      name = "",
      description = "",
      members = Seq.empty
    )
  )

  spinnerService
    .runWithSpinner {

      val organizationCall =
        organizationServiceFe
          .findById(organizationId)
          .map(
            _.fold(
              e => println(e),
              success => organizationVar.set(success.body.get)
            )
          )

      val projectCall = projectService
        .findByOrganizationId(organizationId)
//        .map(response => response.map(success => stateFormVar.set(success.body.get))¬)
        .flatMap { projectResponse =>
          projectResponse.fold(
            e => IO(println(e)),
            success => {
              val projectDtos = success.body.get
              projectDtos
                .map { projectDto =>
                  taskFeService
                    .findByProjectId(projectDto.id)
                    .map(
                      _.fold(
                        e => {
                          println(e)
                          ProjectInfo(projectDto, Seq.empty)
                        },
                        success => {
                          val taskDtos = success.body.get
                          ProjectInfo(projectDto, taskDtos)
                        }
                      )
                    )
                }
                .toList
                .sequence
                .map(projectInfos => stateFormVar.set(ShowAllProjectsState(projectInfos)))
            }
          )
        }
      organizationCall.flatMap(_ => projectCall)
    }
    .unsafeRunAndForget()

  def createTh(content: String) =
    th(cls("px-6 py-3 font-medium"), content)

  def createTd(content: Modifier[ReactiveHtmlElement[HTMLTableCellElement]]) =
    td(cls("px-6 py-4"), content)

  val element = {
    div(
      child <-- stateFormVar.signal.map(dtos =>
        div(
          div(
            child <-- organizationVar.signal.map(organizationResp =>
              TgCard
                .headerNoFooter(
                  header = TgCard.CardHeader.html(
                    div(
                      div(
                        cls("flex items-center justify-between"),
                        div(cls("mr-2"), organizationResp.name),
                        div(
                          button(
                            `type`("button"),
                            TgIcons.editIcon(className = "w-4 h-4"),
                            cls(
                              "inline-flex items-center text-white bg-brand hover:bg-brand-strong box-border border border-transparent focus:ring-4 focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base text-sm px-3 py-2 focus:outline-none"
                            ),
                            cls("mr-2"),
                            onClick.mapToUnit --> (_ => router.route(EditOrganizationPage(organizationResp.id)))
                          )
                        )
                      )
                    )
                  ),
                  body = TgCard.HtmlCardContent(div(organizationResp.description))
                )
                .element
                .amend(cls("mb-4"))
            )
          ),
          dtos.info.map { dto =>
            val projectCard = TgCard
              .headerNoFooter(
                header = TgCard.CardHeader.html(
                  div(
                    cls("border-1 border-neutral-200 rounded-md bg-neutral-200"),
                    cls("flex items-center justify-between"),
                    div(
                      cls("mr-2"),
                      cls("pl-6 pr-6 pt-2 pb-2"),
//                      cls("grow"),
                      dto.project.name
                    ),
                    div(
                      button(
                        `type`("button"),
                        TgIcons.editIcon(className = "w-4 h-4"),
                        cls(
                          "inline-flex items-center text-white bg-brand hover:bg-brand-strong box-border border border-transparent focus:ring-4 focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base text-sm px-3 py-2 focus:outline-none"
                        ),
                        cls("mr-2"),
                        onClick.mapToUnit --> (_ => router.route(EditProjectPage(dto.project.id)))
                      ),
                      button(
                        `type`("button"),
                        TgIcons.hammerIcon("w-4 h-4"),
                        cls(
                          "inline-flex items-center text-fg-brand bg-neutral-primary border border-brand hover:bg-brand hover:text-white focus:ring-4 focus:ring-brand-subtle font-medium leading-5 rounded-base text-xs px-3 py-2 focus:outline-none"
                        ),
                        onClick.mapToUnit --> (_ => router.route(ShowAllTasksPage(dto.project.id)))
                      )
                    )
                  ),
                  TgCard.CardHeaderSize.H3
                ),
                body = TgCard.HtmlCardContent(createTaskCard(dto))
              )

            projectCard.element.amend(cls("mb-4"))

          }
        )
      )
    )
  }

  private def createTaskCard(dto: ProjectInfo) = {
    val unassignedDto = SearchAccountDto(
      id = -1,
      firstName = "Not",
      lastName = "assigned",
      email = "notassinged@email.com",
      accountTypeId = -1
    )
    val groupedTasksByAssignee = dto.tasks.groupBy(_.assignedTo.getOrElse(unassignedDto))

    val elements = groupedTasksByAssignee.map { case (accountDto, tasks) =>
      div(
        h5(
          cls("ext-xl font-bold text-heading"),
          cls("mb-2"),
          accountDto.fullName
        ),
        div(tasks.map { task =>
          val taskCard = TgCard
            .headerNoFooter(
              header = TgCard.CardHeader
                .html(
                  div(
                    cls("flex", "justify-between"),
                    a(
                      cls("font-medium text-fg-brand hover:underline"),
                      cls("mr-2"),
                      task.name,
                      router.navigateTo(EditTaskPage(task.projectId, task.id))
                    ),
                    div(
                      task.taskStatus.name
                    )

                    //                              s"${task.name} - ${task.taskStatus.name} - ${task.assignedTo.map(_.fullName).getOrElse("Not assigned")} "
                  ),
                  TgCard.CardHeaderSize.H3
                ),
              body = TgCard.HtmlCardContent(
                div(
                  div(
                    cls("mb-4"),
                    b("Description: "),
                    task.description
                  ),
                  div(
                    marginBottom.rem(1),
                    display.flex,
                    task.logEntries
                      .sortBy(_.createdAt)
                      .take(10)
                      .map(logEntry =>
                        div(
                          cls("border-1 border-gray-200 rounded-md"),
                          cls("mr-2"),
                          textAlign.center, {
                            val (day, date) = tgDateFormater.asAmericanDate(
                              LocalDate.from(logEntry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate)
                            )
                            div(
                              cls("bg-gray-100 rounded-t-md"),
                              cls("p-1"),
                              p(date),
                              p(b(day))
                            )
                          },
                          div(
                            cls("p-1"),
                            logEntry.status.color
                          )
                        )
                      )
                  ),
                  div(
                    p(b("Latest status")),
                    div {
                      if (task.logEntries.isEmpty) p("No status update")
                      else
                        task.logEntries.sortBy(_.createdAt).reverse.head.textExternal.split("\n").map(a => p(a)).toSeq
                    }
                  )
                )
              )
            )
          taskCard.element.amend(cls("mb-4"))
        })
      )

    }.toSeq

    div(elements)
  }

}
