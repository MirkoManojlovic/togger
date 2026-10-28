package com.togger.ui.organization

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.organization.FindOrganizationResp
import com.togger.routes.Pages.{
  CreateOrganizationPage,
  EditOrganizationPage,
  ShowAllProjectLabelsPage,
  SearchProjectsByLabelPage,
  ShowAllOrganizationsPage,
  ShowProjectsForOrganizationIdTable
}
import com.togger.markdown.TgMarkdown
import com.togger.routes.{FrontendPage, ListParams, TgRouter}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}

class ShowAllOrganizationsComponent(
  organizationService: OrganizationServiceFe,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService,
  params: ListParams,
  toast: Toast,
  tgMarkdown: TgMarkdown
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(Seq.empty[FindOrganizationResp])

  private val paginationVar = Var(Option.empty[PaginationState])

  private def showList(newParams: ListParams): Unit =
    router.route(ShowAllOrganizationsPage(newParams))

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] =
    organizationService.findByAccountId(accountService.get.get.id, params.pageRequest).map {
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

  spinnerService.runWithSpinner(loadPage()).unsafeRunAndForget()

  private def organizationCard(dto: FindOrganizationResp): HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.html(
          div(
            cls("flex items-center justify-between"),
            a(
              cls("mr-2 font-medium text-fg-brand hover:underline"),
              router.navigateTo(ShowProjectsForOrganizationIdTable(dto.id)),
              s"${dto.icon} ${dto.name}".trim
            ),
            div(
              iconButton(
                "Edit Organization",
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
          ),
          TgCard.CardHeaderSize.H3
        ),
        body = TgCard.HtmlCardContent(
          div(
            tgMarkdown.toLaminarDiv(dto.description),
            Option.when(dto.members.nonEmpty)(
              div(
                cls("mt-4"),
                div(cls("font-semibold text-heading mb-1"), "Members"),
                div(
                  cls("flex flex-wrap gap-1"),
                  dto.members
                    .map(_.fullName)
                    .sorted
                    .map(name => TgBadge(text = name, color = TgBadgeColor.Gray, bordered = true).element)
                )
              )
            )
          )
        )
      )
      .element

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Organizations"),
              div(
                iconButton(
                  "Create Organization",
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(CreateOrganizationPage)
                ),
                iconButton(
                  "Search Projects",
                  TgIcons.eyeIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(SearchProjectsByLabelPage)
                ),
                iconButton(
                  "Project Labels",
                  TgIcons.tagIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(ShowAllProjectLabelsPage())
                )
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard.HtmlCardContent(
            div(
              child <-- stateFormVar.signal.map(dtos =>
                div(cls("flex flex-col gap-4"), dtos.map(organizationCard))
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
