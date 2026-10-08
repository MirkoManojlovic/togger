package com.togger.ui.projectlabel

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.{PaginatedResponse, TgJsonFailure, TgJsonSuccess}
import com.togger.dto._
import com.togger.routes.Pages.{
  CreateProjectLabelPage,
  EditProjectLabelPage,
  ShowAllProjectLabelCategoriesPage,
  ShowAllProjectLabelsPage
}
import com.togger.routes.{FrontendPage, ListParams, TgRouter}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}

import java.util.UUID

case class ProjectLabelFormState(
  id: UUID,
  name: String,
  description: String,
  categoryId: UUID,
  categoryName: String
)

class ShowAllProjectLabelsComponent(
  projectLabelService: ProjectLabelFeService,
  projectLabelCategoryService: ProjectLabelCategoryFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService,
  params: ListParams
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(Seq.empty[ProjectLabelFormState])

  private val paginationVar = Var(Option.empty[PaginationState])

  private def showList(newParams: ListParams): Unit =
    router.route(ShowAllProjectLabelsPage(newParams))

  /** Reads the page the URL asks for. A page past the end steps back to the last page. */
  private def loadPage(): IO[Unit] = {
    val labelsCall: IO[Either[TgJsonFailure, TgJsonSuccess[PaginatedResponse[FindProjectLabelResp]]]] =
      projectLabelService.findAll(params.pageRequest)
    // Only the names of the categories are needed, so every category is read (bounded by PaginatedLoader).
    val categoriesCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindProjectLabelCategoryResp]]]] =
      projectLabelCategoryService.findEvery()

    (labelsCall, categoriesCall).parMapN { (projectLabelsEither, categoriesEither) =>
      for {
        projectLabelsSuccess <- projectLabelsEither
        categoriesSuccess <- categoriesEither
      } yield {
        val response = projectLabelsSuccess.body.get
        if (response.items.isEmpty && response.totalNumberOfPages > 0 && params.page > response.totalNumberOfPages)
          showList(params.atPage(response.totalNumberOfPages.toInt, params.size))
        else {
          val categoryNameById = categoriesSuccess.body.get.map(c => c.id -> c.name).toMap
          stateFormVar.set(
            response.items.map(dto =>
              ProjectLabelFormState(
                id = dto.id,
                name = dto.name,
                description = dto.description,
                categoryId = dto.categoryId,
                categoryName = categoryNameById.getOrElse(dto.categoryId, "Unknown")
              )
            )
          )
          paginationVar.set(Some(PaginationState.fromResponse(response)))
        }
      }
    }.void
  }

  spinnerService.runWithSpinner(loadPage()).unsafeRunAndForget()

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Project labels"),
              div(
                cls("flex"),
                iconButton(
                  "Project label categories",
                  TgIcons.listIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(ShowAllProjectLabelCategoriesPage())
                ),
                iconButton(
                  "Create Project Label",
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(CreateProjectLabelPage)
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
                        createTh("id"),
                        createTh("name"),
                        createTh("description"),
                        createTh("category"),
                        createTh("Action")
                      )
                    ),
                    tbody(
                      dtos
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
                            createTd(dto.name),
                            createTd(dto.description),
                            createTd(dto.categoryName),
                            createTd(
                              div(
                                iconButton(
                                  "Edit",
                                  TgIcons.editIcon(className = "w-4 h-4"),
                                  rowIconButtonCls,
                                  rowButtonMarginCls,
                                  onClick.mapToUnit --> (_ => router.route(EditProjectLabelPage(dto.id)))
                                )
                              )
                            )
                          )
                        }
                    )
                  )
                )
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
