package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.togger.dto.{FindMilestoneResp, FindProjectResp}
import com.togger.milestone.MilestoneStatus
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, MilestoneListParams, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TgHtmlTable.{createTd, createTdOverflow}
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.components.{
  MultiSelectItem,
  TableFilterComponent,
  TableSearchComponent,
  TgHtmlTable,
  TgPaginationComponent
}
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}
import com.togger.ui.task.TgDateFormater

import java.util.UUID

class ShowMilestonesComponent(
  milestoneService: MilestoneFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  projectId: UUID,
  params: MilestoneListParams
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(Seq.empty[FindMilestoneResp])

  private val paginationVar = Var(Option.empty[PaginationState])

  private def showList(newParams: MilestoneListParams): Unit =
    router.route(ShowMilestonesPage(projectId, newParams))

  val searchTermVar = Var(params.search)
  private val searchBox = TableSearchComponent(
    inputSignal = searchTermVar.signal,
    inputStateUpdater = searchTermVar.updater[String]((_, newSearchTerm) => newSearchTerm),
    placeholderValue = "Search by name, description"
  )

  private val navigateOnSearch: Modifier[HtmlElement] =
    searchTermVar.signal.changes --> Observer[String] { term =>
      if (term.trim != params.search) showList(params.copy(search = term.trim).firstPage)
    }

  private val statusFilterItems =
    MilestoneStatus.values.map(status =>
      MultiSelectItem(
        id = status.value.toString,
        name = status.toStringWithEmoji,
        isSelected = params.selectedStatuses.contains(status.value.toString)
      )
    )
  private val statusSelectionVar = Var(Set.empty[String])
  private val statusFilter =
    new TableFilterComponent(Val(statusFilterItems), label = "Filter by status", selectedIdsVar = statusSelectionVar)

  // The ticks the URL itself implies (the widget seeds them when it is built) are not a change.
  private val navigateOnStatusChange: Modifier[HtmlElement] =
    statusSelectionVar.signal.changes --> Observer[Set[String]] { picked =>
      if (picked != params.selectedStatuses)
        showList(params.copy(statuses = MilestoneListFilterMapping.toParamSelection(picked)).firstPage)
    }

  /** Reads the page the URL asks for. A page past the end (its milestones were filtered away) steps back to the last
    * page.
    */
  private def loadPage(): IO[Unit] =
    milestoneService
      .findByProjectId(projectId, MilestoneListFilterMapping.toMilestoneFilter(params), params.pageRequest)
      .map {
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

  private val projectVar: Var[FindProjectResp] = Var(
    FindProjectResp(
      id = UUID.randomUUID(),
      organizationId = UUID.randomUUID(),
      name = "",
      description = "",
      icon = "",
      workDays = Set.empty,
      members = Seq.empty
    )
  )

  spinnerService
    .runWithSpinner {

      val projectCall =
        projectService
          .findById(projectId)
          .map(
            _.fold(
              e => println(e),
              success => projectVar.set(success.body.get)
            )
          )

      projectCall.flatMap(_ => loadPage())
    }
    .unsafeRunAndForget()

  val element = {
    div(
      child <-- projectVar.signal.map(projectResp =>
        TgCard
          .headerNoFooter(
            header = TgCard.CardHeader.html(
              div(
                div(
                  cls("flex items-center justify-between"),
                  div(cls("mr-2"), projectResp.name),
                  div(
                    iconButton(
                      "Edit Project",
                      TgIcons.editIcon(className = "w-4 h-4"),
                      rowIconButtonCls,
                      rowButtonMarginCls,
                      onClick.mapToUnit --> (_ => router.route(EditProjectPage(projectResp.id)))
                    )
                  )
                )
              )
            ),
            body = TgCard.HtmlCardContent(div(projectResp.description))
          )
          .element
          .amend(cls("mb-4"))
      ),
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(
            div(
              cls("flex justify-between"),
              div("Milestones"),
              div(
                cls("flex"),
                iconButton(
                  "History",
                  TgIcons.statusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(ShowMilestoneHistoryPage(projectId))
                ),
                iconButton(
                  "Create Milestone",
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(CreateMilestonePage(projectId))
                )
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard
            .HtmlCardContent(
              div(
                div(cls("flex justify-between mb-4"), searchBox.element, statusFilter.element),
                navigateOnSearch,
                navigateOnStatusChange,
                child <-- stateFormVar.signal.map { milestones =>
                  TgHtmlTable.tableElement(
                    Seq("Id", "Name", "Description", "Status", "Start date", "Due date", "End date", "Action")
                  )(
                    milestones
                      .map { dto =>
                        Seq(
                          createTd(dto.id.toString),
                          createTd(dto.name),
                          createTdOverflow(dto.description),
                          createTd(dto.status.toStringWithEmoji),
                          createTd(tgDateFormater.asEuropeanDate(dto.startDate)),
                          createTd(tgDateFormater.asEuropeanDate(dto.dueDate)),
                          createTd(dto.endDate.fold("-")(date => tgDateFormater.asEuropeanDate(date))),
                          createTd(
                            div(
                              iconButton(
                                "Edit",
                                TgIcons.editIcon(className = "w-4 h-4"),
                                rowIconButtonCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit --> (_ => router.route(EditMilestonePage(dto.id)))
                              )
                            )
                          )
                        )
                      }
                  )
                },
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
