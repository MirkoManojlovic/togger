package com.togger.ui.project

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.FindProjectResp
import com.togger.dto.organization.FindOrganizationResp
import com.togger.markdown.TgMarkdown
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, ListParams, TgRouter}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.organization.{OrganizationServiceFe, TgIcons}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}

import java.util.UUID

class ShowAllProjectsTableComponent(
  projectService: ProjectService,
  organizationServiceFe: OrganizationServiceFe,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService,
  tgMarkdown: TgMarkdown,
  organizationId: UUID,
  params: ListParams,
  toast: Toast
)(implicit
  ioRuntime: IORuntime
) {

  private val stateFormVar = Var(Seq.empty[FindProjectResp])
  private val organizationVar: Var[FindOrganizationResp] = Var(
    FindOrganizationResp(
      id = UUID.randomUUID(),
      name = "",
      description = "",
      icon = "",
      members = Seq.empty
    )
  )

  private val paginationVar = Var(Option.empty[PaginationState])

  private def showList(newParams: ListParams): Unit =
    router.route(ShowProjectsForOrganizationIdTable(organizationId, newParams))

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    projectService.findByOrganizationId(organizationId, params.pageRequest).map {
      case Left(failure) => println(failure)
      case Right(success) =>
        val response = success.body.get
        if (response.items.isEmpty && response.totalNumberOfPages > 0 && params.page > response.totalNumberOfPages)
          showList(params.atPage(response.totalNumberOfPages.toInt, params.size))
        else {
          stateFormVar.set(response.items)
          paginationVar.set(Some(PaginationState.fromResponse(response)))
        }
    }

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

      organizationCall.flatMap(_ => loadPage())
    }
    .unsafeRunAndForget()

  private val projectInfoCard = new ProjectInfoCard(router, tgMarkdown)

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
                div(cls("flex flex-col gap-4"), dtos.map(projectInfoCard.element(_)))
              ),
              TgPaginationComponent(
                paginationVar.signal,
                (newPage, newSize) => showList(params.atPage(newPage, newSize))
              )
            )
          )
        )
        .element
        .amend(cls("mb-4"))
    )
  }

}
