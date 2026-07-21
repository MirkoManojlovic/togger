package com.togger.ui.project

import cats.effect.unsafe.IORuntime
import com.togger.components.button.TgHtmlButton
import com.togger.dto.FindProjectResp
import com.togger.dto.organization.FindOrganizationResp
import com.togger.routes.Pages.{CreateProjectPage, EditOrganizationPage, EditProjectPage, ShowAllTasksPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.organization.{OrganizationServiceFe, TgIcons}
import com.togger.ui.spinner.SpinnerService
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLTableCellElement

import java.util.UUID

class ShowAllProjectsTableComponent(
  projectService: ProjectService,
  organizationServiceFe: OrganizationServiceFe,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService,
  organizationId: UUID
)(implicit
  ioRuntime: IORuntime
) {

  private val stateFormVar = Var(Seq.empty[FindProjectResp])
  private val organizationVar: Var[FindOrganizationResp] = Var(
    FindOrganizationResp(
      id = UUID.randomUUID(),
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
        .map(response => response.map(success => stateFormVar.set(success.body.get)))

      organizationCall.flatMap(_ => projectCall)
    }
    .unsafeRunAndForget()

  def createTh(content: String) =
    th(cls("px-6 py-3 font-medium"), content)

  def createTd(content: Modifier[ReactiveHtmlElement[HTMLTableCellElement]]) =
    td(cls("px-6 py-4"), content)

  val element = {
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
      ),
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Projects"),
              TgHtmlButton
                .extraSmall("Create")
                .amend(
                  router.navigateTo(CreateProjectPage(organizationId))
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
                                    "inline-flex items-center text-white bg-brand hover:bg-brand-strong box-border border border-transparent focus:ring-4 focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base text-sm px-3 py-2 focus:outline-none"
                                  ),
                                  cls("mr-2"),
                                  onClick.mapToUnit --> (_ => router.route(EditProjectPage(dto.id)))
                                ),
                                button(
                                  `type`("button"),
                                  TgIcons.hammerIcon("w-4 h-4 me-1.5 -ms-0.5"),
                                  "Tasks",
                                  cls(
                                    "inline-flex items-center text-fg-brand bg-neutral-primary border border-brand hover:bg-brand hover:text-white focus:ring-4 focus:ring-brand-subtle font-medium leading-5 rounded-base text-xs px-3 py-2 focus:outline-none"
                                  ),
                                  onClick.mapToUnit --> (_ => router.route(ShowAllTasksPage(dto.id)))
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
        .amend(cls("mb-4"))
    )
  }

}
