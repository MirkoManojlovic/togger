package com.togger.ui.project

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.FindProjectResp
import com.togger.dto.organization.FindOrganizationResp
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.organization.{OrganizationServiceFe, TgIcons}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}

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
                    iconButton(
                      "Edit Organization",
                      TgIcons.editIcon(className = "w-4 h-4"),
                      rowIconButtonCls,
                      rowButtonMarginCls,
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
              iconButton(
                "Create Project",
                TgIcons.plusIcon(className = "w-4 h-4"),
                headerIconButtonCls,
                "mr-2",
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
                                iconButton(
                                  "Edit",
                                  TgIcons.editIcon(className = "w-4 h-4"),
                                  rowIconButtonCls,
                                  rowButtonMarginCls,
                                  onClick.mapToUnit --> (_ => router.route(EditProjectPage(dto.id)))
                                ),
                                iconButton(
                                  "Tasks",
                                  TgIcons.hammerIcon(className = "w-4 h-4"),
                                  rowIconButtonCls,
                                  rowButtonMarginCls,
                                  onClick.mapToUnit --> (_ => router.route(ShowAllTasksPage(dto.id)))
                                ),
                                iconButton(
                                  "Task Dump/Load Configuration",
                                  TgIcons.downloadIcon(className = "w-4 h-4"),
                                  rowIconButtonCls,
                                  rowButtonMarginCls,
                                  onClick.mapToUnit --> (_ => router.route(ShowAllTaskConfigsPage(dto.id)))
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
