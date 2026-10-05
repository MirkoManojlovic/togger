package com.togger.ui.techstack

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.routes.Pages.{CreateTechStackPage, EditTechStackPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.techstack.TechStackCategory
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}

import java.util.UUID

case class TechStackFormState(
  id: UUID,
  name: String,
  description: String,
  category: TechStackCategory
)

class ShowAllTechStacksComponent(
  techStackService: TechStackFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(Seq.empty[TechStackFormState])

  spinnerService
    .runWithSpinner(
      techStackService
        .findAll()
        .map(response =>
          response.map { success =>
            val dtos = success.body.get
            stateFormVar.set(
              dtos.map(dto =>
                TechStackFormState(
                  id = dto.id,
                  name = dto.name,
                  description = dto.description,
                  category = dto.category
                )
              )
            )
          }
        )
    )
    .unsafeRunAndForget()

  val element = {
    div(
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Tech stacks"),
              iconButton(
                "Create Tech Stack",
                TgIcons.plusIcon(className = "w-4 h-4"),
                headerIconButtonCls,
                "mr-2",
                router.navigateTo(CreateTechStackPage)
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
                            createTd(dto.id.toString),
                            createTd(dto.name),
                            createTd(dto.description),
                            createTd(dto.category.categoryName),
                            createTd(
                              div(
                                iconButton(
                                  "Edit",
                                  TgIcons.editIcon(className = "w-4 h-4"),
                                  rowIconButtonCls,
                                  rowButtonMarginCls,
                                  onClick.mapToUnit --> (_ => router.route(EditTechStackPage(dto.id)))
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
