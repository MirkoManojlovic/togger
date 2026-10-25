package com.togger.ui.project

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.toTraverseOps
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, TgIds}
import com.togger.dto.organization.FindOrganizationResp
import com.togger.dto.{FindProjectResp, FindTaskRespFull, SearchAccountDto}
import com.togger.markdown.TgMarkdown
import com.togger.routes.Pages.{EditOrganizationPage, EditProjectPage, EditTaskPage, ShowAllTasksPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.task.DefaultTaskStatus
import com.togger.task.DefaultTaskStatus.{Backlog, Done, WontDo}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components._
import com.togger.ui.organization.{OrganizationServiceFe, TgIcons}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonCls}
import com.togger.ui.task.{TaskFeService, TaskPreviewComponent, TaskProgressComponent, TgDateFormater}

import java.time.DayOfWeek
import java.util.UUID

case class ShowAllProjectsState(
  info: Seq[ProjectInfo] = Seq.empty
)

case class ProjectInfo(
  project: FindProjectResp,
  tasks: Seq[FindTaskRespFull]
)

class ShowAllProjectsStatusComponent(
  projectService: ProjectService,
  organizationServiceFe: OrganizationServiceFe,
  taskFeService: TaskFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountService: AccountService,
  tgDateFormater: TgDateFormater,
  tgMarkdown: TgMarkdown,
  tgClock: TgClock,
  organizationId: UUID
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(ShowAllProjectsState())
  private val defaultDeselectedStatusNames = Set(Done, WontDo, Backlog).map(_.statusName)
  private val statusFilterItemsSignal: Signal[Seq[MultiSelectItem]] =
    stateFormVar.signal.map { state =>
      state.info
        .flatMap(_.tasks.map(_.taskStatus))
        .distinctBy(_.name)
        .sortBy(status => DefaultTaskStatus.values.find(_.statusName == status.name).map(_.i).getOrElse(Int.MaxValue))
        .map(status =>
          MultiSelectItem(
            id = status.name,
            name = status.badgeText,
            isSelected = !defaultDeselectedStatusNames.contains(status.name)
          )
        )
    }
  private val statusFilter = new TableFilterComponent(statusFilterItemsSignal, label = "Filter by status")
  private val filteredStateSignal: Signal[ShowAllProjectsState] =
    stateFormVar.signal.combineWith(statusFilter.selectedIds).map { case (state, selectedNames) =>
      if (selectedNames.isEmpty) state
      else
        ShowAllProjectsState(
          state.info
            .map(info => info.copy(tasks = info.tasks.filter(task => selectedNames.contains(task.taskStatus.name))))
            .filter(_.tasks.nonEmpty)
        )
    }
  private val organizationVar: Var[FindOrganizationResp] = Var(
    FindOrganizationResp(
      id = UUID.randomUUID(),
      name = "",
      description = "",
      icon = "",
      members = Seq.empty
    )
  )

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

      // The status board shows every project of the organization, so all pages are read (bounded by PaginatedLoader).
      val projectCall = projectService
        .findAllByOrganizationId(organizationId)
//        .map(response => response.map(success => stateFormVar.set(success.body.get))¬)
        .flatMap { projectResponse =>
          projectResponse.fold(
            e => IO(println(e)),
            success => {
              val projectDtos = success.body.get
              projectDtos
                .map { projectDto =>
                  // Progress is computed over every task of the project, so all pages are read.
                  taskFeService
                    .findAllByProjectId(projectDto.id)
                    .map(
                      _.fold(
                        e => {
                          println(e)
                          ProjectInfo(projectDto, Seq.empty)
                        },
                        success => {
                          val taskDtos = success.body.get
                          ProjectInfo(projectDto, taskDtos)
                        }
                      )
                    )
                }
                .toList
                .sequence
                .map(projectInfos => stateFormVar.set(ShowAllProjectsState(projectInfos)))
            }
          )
        }
      organizationCall.flatMap(_ => projectCall)
    }
    .unsafeRunAndForget()

  val element = {
    div(
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
        )
      ),
      div(cls("flex justify-end mb-4"), statusFilter.element),
      children <-- filteredStateSignal.map(_.info.map { dto =>
        val projectCard = TgCard
          .headerNoFooter(
            header = TgCard.CardHeader.html(
              div(
                cls("border-1 border-neutral-200 rounded-md bg-neutral-200"),
                cls("flex items-center justify-between"),
                div(
                  cls("mr-2"),
                  cls("pl-6 pr-6 pt-2 pb-2"),
//                      cls("grow"),
                  dto.project.name
                ),
                div(
                  iconButton(
                    "Edit Project",
                    TgIcons.editIcon(className = "w-4 h-4"),
                    rowIconButtonCls,
                    rowButtonMarginCls,
                    onClick.mapToUnit --> (_ => router.route(EditProjectPage(dto.project.id)))
                  ),
                  iconButton(
                    "Show Tasks",
                    TgIcons.hammerIcon(className = "w-4 h-4"),
                    rowIconButtonCls,
                    rowButtonMarginCls,
                    onClick.mapToUnit --> (_ => router.route(ShowAllTasksPage(dto.project.id)))
                  )
                )
              ),
              TgCard.CardHeaderSize.H3
            ),
            body = TgCard.HtmlCardContent(createTaskCard(dto))
          )

        projectCard.element.amend(cls("mb-4"))
      })
    )
  }

  private def createTaskCard(dto: ProjectInfo) = {
    val unassignedDto = SearchAccountDto(
      id = TgIds.UNASSIGNED_UUID,
      firstName = "Not",
      lastName = "assigned",
      email = "notassinged@email.com",
      accountTypeId = TgIds.UNASSIGNED_UUID
    )
    val groupedTasksByAssignee = dto.tasks.groupBy(_.assignedTo.getOrElse(unassignedDto))
    val workDays = dto.project.workDays.map(DayOfWeek.of)

    val elements = groupedTasksByAssignee.map { case (accountDto, tasks) =>
      div(
        h5(
          cls("ext-xl font-bold text-heading"),
          cls("mb-2"),
          accountDto.fullName
        ),
        div(
          tasks
            .sortBy(_.taskStatus.id)
            .map { task =>
              val taskCard = TgCard
                .headerNoFooter(
                  header = TgCard.CardHeader
                    .html(
                      div(
                        cls("flex", "items-center"),
                        TgBadge(
                          text = task.taskStatus.badgeText,
                          color = task.taskStatus.badgeColor,
                          bordered = true
                        ).element,
                        a(
                          cls("font-medium text-fg-brand hover:underline"),
                          cls("ml-2"),
                          task.name,
                          router.navigateTo(EditTaskPage(task.projectId, task.id))
                        ),
                        TaskProgressComponent
                          .fromDescription(Val(task.description), ringSizeCls = "w-5 h-5")
                          .element
                          .amend(cls("ml-2"))

                        //                              s"${task.name} - ${task.taskStatus.name} - ${task.assignedTo.map(_.fullName).getOrElse("Not assigned")} "
                      ),
                      TgCard.CardHeaderSize.H3
                    ),
                  body = TgCard.HtmlCardContent(
                    new TaskPreviewComponent(task, workDays, router, tgDateFormater, tgMarkdown, tgClock).element
                  )
                )
              taskCard.element.amend(cls("mb-4"))
            }
        )
      )

    }.toSeq

    div(elements)
  }

}
