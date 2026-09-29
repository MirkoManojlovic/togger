package com.togger.ui.task
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgIds
import com.togger.dto.{FindProjectResp, FindTaskRespFull, SearchAccountDto}
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.task.DefaultTaskStatus.{Archived, Backlog, Done, WontDo}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgHtmlTable.{createTd, createTdOverflow}
import com.togger.ui.components.{MultiSelectItem, TableFilterComponent, TableSearchComponent, TgBadge, TgHtmlTable}
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}

import java.util.UUID

class ShowAllTasksComponent(
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(Seq.empty[FindTaskRespFull])
  val statusesVar = Var(TaskStatusesState())
  private val defaultDeselectedStatusNames = Set(Done, WontDo, Archived, Backlog).map(_.statusName)
  private val statusFilterItemsSignal: Signal[Seq[MultiSelectItem]] =
    statusesVar.signal.map(
      _.taskStatusesSorted
        .map { s =>
          val isSelected = !defaultDeselectedStatusNames.contains(s.name)
          MultiSelectItem(id = s.id.toString, name = s.name, isSelected = isSelected)
        }
    )
  private val statusFilter = new TableFilterComponent(statusFilterItemsSignal, label = "Filter by status")

  val searchTermVar = Var("")
  private val searchBox =TableSearchComponent(
    inputSignal = searchTermVar.signal,
    inputStateUpdater = searchTermVar.updater[String]((_, newSearchTerm) => newSearchTerm),
    placeholderValue = "Search by name, description",
    debounceMs = 0
  )

  private val filteredTasksSignal: Signal[Seq[FindTaskRespFull]] =
    stateFormVar.signal.combineWith(statusFilter.selectedIds, searchBox.inputSignal).map {
      case (tasks, selectedIds, term) =>
        val byStatus =
          if (selectedIds.isEmpty) tasks else tasks.filter(dto => selectedIds.contains(dto.taskStatus.id.toString))
        val needle = term.trim.toLowerCase
        if (needle.isEmpty) byStatus
        else
          byStatus.filter(dto => dto.name.toLowerCase.contains(needle) || dto.description.toLowerCase.contains(needle))
    }
  private val unassignedDto = SearchAccountDto(
    id = TgIds.UNASSIGNED_UUID,
    firstName = "Not",
    lastName = "assigned",
    email = "notassigned@email.com",
    accountTypeId = TgIds.UNASSIGNED_UUID
  )
  private val tasksByAccountSignal: Signal[Seq[(SearchAccountDto, Seq[FindTaskRespFull])]] =
    filteredTasksSignal.map(
      _.groupBy(_.assignedTo.getOrElse(unassignedDto)).toSeq.sortBy { case (accountDto, _) =>
        accountDto.fullName.toLowerCase
      }
    )
  private val projectVar: Var[FindProjectResp] = Var(
    FindProjectResp(
      id = UUID.randomUUID(),
      organizationId = UUID.randomUUID(),
      name = "",
      description = "",
      icon = "",
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

      val taskStatusCall = taskStatusFeService
        .findByProjectId(projectId)
        .map(response => response.map(success => statusesVar.set(TaskStatusesState(success.body.get))))

      val taskCall = taskService
        .findByProjectId(projectId)
        .map(response => response.map(success => stateFormVar.set(success.body.get)))

      projectCall.flatMap(_ => taskStatusCall).flatMap(_ => taskCall)
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
              div("Tasks"),
              div(
                cls("flex"),
                iconButton(
                  "Create Task",
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(CreateTaskPage(projectId))
                ),
                iconButton(
                  "Task statuses",
                  TgIcons.statusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(ShowAllTaskStatusesPage(projectId))
                ),
                iconButton(
                  "Create Milestone",
                  TgIcons.plusIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(CreateMilestonePage(projectId))
                ),
                iconButton(
                  "Milestones",
                  TgIcons.listIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(ShowMilestonesPage(projectId))
                )
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard
            .HtmlCardContent(
              div(
                div(cls("flex justify-between mb-4"), searchBox.element, statusFilter.element),
                div(children <-- tasksByAccountSignal.map { groups =>
                  groups.map { case (accountDto, dtos) =>
                    div(
                      cls("mb-4"),
                      h5(cls("text-xl font-bold text-heading mb-2"), accountDto.fullName),
                      TgHtmlTable.tableElement(
                        Seq("Id", "Name", "Description", "Status", "Action")
                      )(
                        dtos
                          .sortBy(dto => statusesVar.now().idToOrder(dto.taskStatus.id))
                          .map { dto =>
                            Seq(
                              createTd(dto.id.toString),
                              createTd(dto.name),
                              createTdOverflow(dto.description),
                              createTd(
                                TgBadge(text = dto.taskStatus.name, color = dto.taskStatus.badgeColor, bordered = true).element
                              ),
                              createTd(
                                div(
                                  iconButton(
                                    "Edit",
                                    TgIcons.editIcon(className = "w-4 h-4"),
                                    rowIconButtonCls,
                                    rowButtonMarginCls,
                                    onClick.mapToUnit --> (_ => router.route(EditTaskPage(dto.projectId, dto.id)))
                                  )
                                )
                              )
                            )
                          }
                      )
                    )
                  }
                })
              )
            )
        )
        .element
    )
  }

}
