package com.togger.ui.project

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.PageRequest
import com.togger.dto.{FindProjectLabelResp, FindProjectResp}
import com.togger.routes.Pages.EditProjectPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTh}
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.components.{
  DropdownMeta,
  TgBadge,
  TgBadgeColor,
  TgPaginationComponent,
  TgSelectDropdownMultiSelectFilter,
  TgSelectDropdownOption
}
import com.togger.ui.organization.TgIcons
import com.togger.ui.projectlabel.ProjectLabelFeService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonCls}

import java.util.UUID

class SearchProjectsByLabelComponent(
  projectService: ProjectService,
  projectLabelService: ProjectLabelFeService,
  spinnerService: SpinnerService,
  router: TgRouter[FrontendPage]
)(implicit
  ioRuntime: IORuntime
) {

  private val projectLabelsVar = Var(Seq.empty[FindProjectLabelResp])
  private val selectedLabelIds = Var(Set.empty[String])
  private val resultsVar = Var(Seq.empty[FindProjectResp])
  private val hasSearchedVar = Var(false)
  private val paginationVar = Var(Option.empty[PaginationState])

  // The labels the shown page was searched with, so another page of the same search can be read. The labels are not
  // in the URL, so neither is the page.
  private var searchedLabelIds = Seq.empty[UUID]

  spinnerService
    .runWithSpinner {
      projectLabelService
        .findAll()
        .map(_.fold(e => println(e), success => projectLabelsVar.set(success.body.get)))
    }
    .unsafeRunAndForget()

  private def searchPage(labelIds: Seq[UUID], page: PageRequest): Unit = {
    hasSearchedVar.set(true)

    if (labelIds.isEmpty) {
      resultsVar.set(Seq.empty)
      paginationVar.set(None)
    } else {
      spinnerService
        .runWithSpinner(projectService.searchByLabels(labelIds, page))
        .map(
          _.fold(
            e => println(e),
            success => {
              val response = success.body.get
              searchedLabelIds = labelIds
              resultsVar.set(response.items)
              paginationVar.set(Some(PaginationState.fromResponse(response)))
            }
          )
        )
        .unsafeRunAndForget()
    }
  }

  private def runSearch(): Unit = {
    val size = paginationVar.now().map(_.size).getOrElse(PageRequest.DefaultSize)
    searchPage(selectedLabelIds.now().map(UUID.fromString).toSeq, PageRequest(PageRequest.DefaultPage, size))
  }

  private val searchSubmitter = Observer[Unit](_ => runSearch())

  private def labelFilterElement(projectLabels: Seq[FindProjectLabelResp]): HtmlElement = {
    val options = projectLabels
      .sortBy(_.name)
      .map(label =>
        TgSelectDropdownOption(
          value = label.id.toString,
          meta = DropdownMeta(label = label.name, description = ""),
          render = () => span(label.name)
        )
      )
    TgSelectDropdownMultiSelectFilter(
      inputSignal = selectedLabelIds.signal,
      inputStateUpdater = selectedLabelIds.writer,
      labelValue = "Select project labels",
      options = options
    ).htmlElement
  }

  private def resultRow(dto: FindProjectResp) =
    tr(
      cls("odd:bg-neutral-primary even:bg-neutral-secondary-soft border-b border-default"),
      createTd(s"${dto.icon} ${dto.name}"),
      createTd(dto.description),
      createTd(
        div(
          cls("flex flex-wrap gap-1"),
          dto.projectLabels.map(label =>
            TgBadge(text = label.name, color = TgBadgeColor.Indigo, bordered = true).element
          )
        )
      ),
      createTd(
        div(
          cls("flex flex-wrap gap-1"),
          dto.members.map(member =>
            TgBadge(
              text = s"${member.firstName} ${member.lastName}",
              color = TgBadgeColor.Alternative,
              bordered = true
            ).element
          )
        )
      ),
      createTd(
        iconButton(
          "Edit",
          TgIcons.editIcon(className = "w-4 h-4"),
          rowIconButtonCls,
          rowButtonMarginCls,
          onClick.mapToUnit --> (_ => router.route(EditProjectPage(dto.id)))
        )
      )
    )

  private def resultsElement(hasSearched: Boolean, results: Seq[FindProjectResp]): HtmlElement =
    if (!hasSearched) {
      div(cls("text-body"), "Pick one or more labels and press Search.")
    } else if (results.isEmpty) {
      div(cls("text-body"), "No projects match all selected labels.")
    } else {
      div(
        cls("relative overflow-x-auto bg-neutral-primary-soft shadow-xs rounded-base border border-default"),
        table(
          cls("w-full text-sm text-left rtl:text-right text-body"),
          thead(
            cls("text-sm text-body bg-neutral-secondary-soft border-b rounded-base border-default"),
            tr(
              createTh("name"),
              createTh("description"),
              createTh("Project labels"),
              createTh("Members"),
              createTh("Action")
            )
          ),
          tbody(results.map(resultRow))
        )
      )
    }

  val element = div(
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Search projects by label", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          form(
            onSubmit.preventDefault.mapToUnit --> searchSubmitter,
            child <-- projectLabelsVar.signal.map(labelFilterElement).map(_.amend(cls("mb-4"))),
            div(
              cls("flex justify-end"),
              button(`type`("submit"), cls(SearchProjectsByLabelComponent.searchButtonCls), "Search")
            )
          )
        )
      )
      .element
      .amend(cls("mb-4")),
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Results", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            child <-- hasSearchedVar.signal.combineWith(resultsVar.signal).map { case (hasSearched, results) =>
              resultsElement(hasSearched, results)
            },
            TgPaginationComponent(
              paginationVar.signal,
              (newPage, newSize) => searchPage(searchedLabelIds, PageRequest(newPage, newSize))
            )
          )
        )
      )
      .element
      .amend(cls("mb-4"))
  )

}

object SearchProjectsByLabelComponent {
  // Same look as TgHtmlButton.tailwindStandardButton, but content-sized and in normal flow rather than
  // float.right + w-full, so it stays inside the card instead of overflowing past its bottom border.
  private val searchButtonCls =
    "inline-flex items-center justify-center text-white bg-brand hover:bg-brand-strong box-border border " +
      "border-transparent focus:ring-4 focus:ring-brand-medium shadow-xs font-medium leading-5 rounded-base " +
      "text-sm px-4 py-2.5 focus:outline-none"
}
