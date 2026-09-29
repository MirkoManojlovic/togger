package com.togger.ui.task
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgIds
import com.togger.dto.{FindProjectResp, FindTaskRespFull, SearchAccountDto}
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.task.DefaultTaskStatus.{Archived, Backlog, Done, WontDo}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgHtmlTable.{createTd, createTdOverflow}
import com.togger.ui.components.{MultiSelectItem, TableFilterComponent, TableSearchComponent, TgBadge, TgHtmlTable}
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{headerIconButtonCls, iconButton, rowButtonMarginCls, rowIconButtonCls}
import com.togger.ui.toast.Toast
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom
import org.scalajs.dom.HTMLTableCellElement

import java.util.UUID
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class ShowAllTasksComponent(
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  projectId: UUID,
  toast: Toast
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(Seq.empty[FindTaskRespFull])
  val statusesVar = Var(TaskStatusesState())
  val taskLabelsVar = Var(TaskLabelsState())
  private val defaultDeselectedStatusNames = Set(Done, WontDo, Archived, Backlog).map(_.statusName)
  private val statusFilterItemsSignal: Signal[Seq[MultiSelectItem]] =
    statusesVar.signal.map(
      _.taskStatusesSorted
        .map { s =>
          val isSelected = !defaultDeselectedStatusNames.contains(s.name)
          MultiSelectItem(id = s.id.toString, name = s.name, isSelected = isSelected)
        }
    )
  private val statusFilterSelectedIdsVar = Var(Set.empty[String])

  private val labelFilterItemsSignal: Signal[Seq[MultiSelectItem]] =
    taskLabelsVar.signal.map(
      _.taskLabels.sortBy(_.name).map(l => MultiSelectItem(id = l.id.toString, name = l.name, isSelected = true))
    )
  private val labelFilterSelectedIdsVar = Var(Set.empty[String])

  val searchTermVar = Var("")
  private val searchBox =TableSearchComponent(
    inputSignal = searchTermVar.signal,
    inputStateUpdater = searchTermVar.updater[String]((_, newSearchTerm) => newSearchTerm),
    placeholderValue = "Search by name, description",
    debounceMs = 0
  )

  private val unassignedDto = SearchAccountDto(
    id = TgIds.UNASSIGNED_UUID,
    firstName = "Not",
    lastName = "assigned",
    email = "notassigned@email.com",
    accountTypeId = TgIds.UNASSIGNED_UUID
  )

  /** Grouped from the raw fetched tasks only, unaffected by search/status filtering, so this signal - and the table
    * header (with its embedded status filter) built from it - stays mounted while the user searches or toggles the
    * status filter; only [[visibleRowsSignal]] reacts to those.
    */
  private val accountGroupsSignal: Signal[Seq[(SearchAccountDto, Seq[FindTaskRespFull])]] =
    stateFormVar.signal.map(
      _.groupBy(_.assignedTo.getOrElse(unassignedDto)).toSeq.sortBy { case (accountDto, _) =>
        accountDto.fullName.toLowerCase
      }
    )

  private def visibleRowsSignal(dtos: Seq[FindTaskRespFull]): Signal[Seq[FindTaskRespFull]] =
    statusFilterSelectedIdsVar.signal
      .combineWith(labelFilterSelectedIdsVar.signal, searchBox.inputSignal)
      .map { case (selectedIds, selectedLabelIds, term) =>
        val byStatus =
          if (selectedIds.isEmpty) dtos else dtos.filter(dto => selectedIds.contains(dto.taskStatus.id.toString))
        // Unlabeled tasks always pass: with every label selected by default, the "any-of-selected" test below
        // would otherwise hide them, since an empty label set can never match a selected label id.
        val byLabel =
          if (selectedLabelIds.isEmpty) byStatus
          else
            byStatus.filter(dto =>
              dto.taskLabels.isEmpty || dto.taskLabels.exists(l => selectedLabelIds.contains(l.id.toString))
            )
        val needle = term.trim.toLowerCase
        if (needle.isEmpty) byLabel
        else
          byLabel.filter(dto => dto.name.toLowerCase.contains(needle) || dto.description.toLowerCase.contains(needle))
      }
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

      val taskLabelCall = taskLabelFeService
        .findByProjectId(projectId)
        .map(response => response.map(success => taskLabelsVar.set(TaskLabelsState(success.body.get))))

      val taskCall = taskService
        .findByProjectId(projectId)
        .map(response => response.map(success => stateFormVar.set(success.body.get)))

      projectCall.flatMap(_ => taskStatusCall).flatMap(_ => taskLabelCall).flatMap(_ => taskCall)
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
                  "Task labels",
                  TgIcons.listIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(ShowAllTaskLabelsPage(projectId))
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
                div(cls("mb-4"), searchBox.element),
                div(children <-- accountGroupsSignal.map { groups =>
                  groups.map { case (accountDto, dtos) =>
                    div(
                      cls("mb-4"),
                      h5(cls("text-xl font-bold text-heading mb-2"), accountDto.fullName),
                      TgHtmlTable.tableElement(
                        Seq[Modifier[ReactiveHtmlElement[HTMLTableCellElement]]](
                          "Name",
                          "Description",
                          new TableFilterComponent(
                            statusFilterItemsSignal,
                            label = "Status",
                            statusFilterSelectedIdsVar
                          ).element,
                          new TableFilterComponent(
                            labelFilterItemsSignal,
                            label = "Labels",
                            labelFilterSelectedIdsVar
                          ).element,
                          "Action"
                        )
                      )(
                        visibleRowsSignal(dtos).map(
                          _.sortBy(dto => statusesVar.now().idToOrder(dto.taskStatus.id))
                            .map { dto =>
                              Seq(
                                createTd(
                                  div(
                                    cls("flex items-center gap-1"),
                                    button(
                                      `type`("button"),
                                      title("Copy task ID"),
                                      cls(
                                        "inline-flex items-center justify-center text-body hover:text-fg-brand focus:outline-none"
                                      ),
                                      TgIcons.copyIcon(className = "w-5 h-5 mr-2"),
                                      onClick.mapToUnit --> (_ =>
                                        dom.window.navigator.clipboard
                                          .writeText(dto.id.toString)
                                          .toFuture
                                          .foreach(_ => toast.success("Task ID copied"))
                                      )
                                    ),
                                    span(dto.name)
                                  )
                                ),
                                createTdOverflow(dto.description),
                                createTd(
                                  TgBadge(
                                    text = dto.taskStatus.name,
                                    color = dto.taskStatus.badgeColor,
                                    bordered = true
                                  ).element
                                ),
                                createTd(
                                  div(
                                    cls("flex flex-wrap gap-1"),
                                    dto.taskLabels.map(label =>
                                      TgBadge(text = label.name, color = label.badgeColor, bordered = true).element
                                    )
                                  )
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
