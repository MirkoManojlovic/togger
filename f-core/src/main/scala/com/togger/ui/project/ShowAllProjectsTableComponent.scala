package com.togger.ui.project

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.FindProjectResp
import com.togger.dto.organization.FindOrganizationResp
import com.togger.markdown.TgMarkdown
import com.togger.common.TgJsonFailure
import com.togger.dto.DataImportResp
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, ListParams, TgRouter}
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.dataimportconfig.ImportResultsComponent
import com.togger.ui.modal.{FormForModal, ModalFactory}
import com.togger.ui.navbar.SidebarRefresh
import com.togger.ui.task.DataExportFeService
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.organization.{OrganizationServiceFe, TgIcons}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}

import java.util.UUID

case class ImportProjectForm(importPath: String = "")

class ShowAllProjectsTableComponent(
  projectService: ProjectService,
  organizationServiceFe: OrganizationServiceFe,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService,
  dataExportService: DataExportFeService,
  modalFactory: ModalFactory,
  sidebarRefresh: SidebarRefresh,
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

  //
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

  private def openImportProjectModal(): Unit = {
    val formState = Var(ImportProjectForm())
    val importPathInput = formState.tgInputManualErrorLPSig(
      inputAndUpdater = selectorAndUpdaterMacro(_.importPath),
      errors = EventStream.empty,
      labelValue = "Import path of the project (an absolute path to the exported project folder)",
      tgInputType = TgInputType.Text,
      debounceTimeMillis = 0
    )
    val hasErrors = EventStream.merge(
      EventStream.fromValue(true),
      formState.signal.map(_.importPath.trim.isEmpty).changes
    )
    modalFactory.formModal(
      FormForModal(div(cls("p-4"), importPathInput.htmlElement), hasErrors),
      close => {
        close()
        importProject(formState.now().importPath.trim)
      },
      title = "Import project"
    )
  }

  private def importProject(importPath: String): Unit =
    spinnerService
      .runWithSpinner(dataExportService.importNewProject(organizationId, importPath).flatMap {
        case Left(failure) => IO.pure(Left(failure))
        case Right(success) => loadPage().as(Right(success.body.get))
      })
      .map {
        case Left(failure: TgJsonFailure) => toast.error(failure)
        case Right(resp: DataImportResp) =>
          sidebarRefresh.request()
          toast.success(s"Project imported, ${resp.results.size} entity(ies) total")
          modalFactory.previewModal(div(cls("p-4"), new ImportResultsComponent(resp.results).element), "Import results")
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
            body = TgCard.HtmlCardContent(tgMarkdown.toLaminarDiv(organizationResp.description))
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
              div(
                cls("flex items-center"),
                iconButton(
                  "Import Project",
                  TgIcons.uploadIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  onClick.mapToUnit --> (_ => openImportProjectModal())
                ),
                iconButton(
                  "Create Project",
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(CreateProjectPage(organizationId))
                )
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
