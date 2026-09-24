package com.togger.ui.organization

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.organization.FindOrganizationResp
import com.togger.routes.Pages.{CreateOrganizationPage, EditOrganizationPage, ShowProjectsForOrganizationIdTable}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}

class ShowAllOrganizationsComponent(
  organizationService: OrganizationServiceFe,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(Seq.empty[FindOrganizationResp])

  spinnerService
    .runWithSpinner(
      organizationService
        .findByAccountId(accountService.get.get.id)
        .map(response => response.map(success => stateFormVar.set(success.body.get)))
    )
    .unsafeRunAndForget()

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Organizations"),
              iconButton(
                "Create Organization",
                TgIcons.plusIcon(className = "w-4 h-4"),
                headerIconButtonCls,
                "mr-2",
                router.navigateTo(CreateOrganizationPage)
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
                        createTh("Name"),
                        createTh("Description"),
                        createTh("Action")
                      )
                    ),
                    tbody(
                      dtos
                        .sortBy(_.id.toString)
                        .zipWithIndex
                        .map { case (dto, index) =>
                          val rowCls = if (index % 2 == 0) {
                            "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"
                          } else {
                            "odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"
                          }

                          tr(
                            cls(rowCls),
                            createTd(dto.id.toString),
                            createTd(s"${dto.icon} ${dto.name}"),
                            createTd(dto.description),
                            createTd(
                              div(
                                iconButton(
                                  "Edit",
                                  TgIcons.editIcon(className = "w-4 h-4"),
                                  rowIconButtonCls,
                                  rowButtonMarginCls,
                                  onClick.mapToUnit --> (_ => router.route(EditOrganizationPage(dto.id)))
                                ),
                                iconButton(
                                  "Projects",
                                  TgIcons.briefcaseIcon(className = "w-4 h-4"),
                                  rowIconButtonCls,
                                  rowButtonMarginCls,
                                  onClick.mapToUnit --> (_ => router.route(ShowProjectsForOrganizationIdTable(dto.id)))
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
