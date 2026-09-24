package com.togger.ui.project

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.toTraverseOps
import com.raquo.laminar.api.L._
import com.togger.common.TgIds
import com.togger.dto.organization.FindOrganizationResp
import com.togger.dto.{FindProjectResp, FindTaskRespFull, LogEntryDto, SearchAccountDto}
import com.togger.routes.Pages.{EditOrganizationPage, EditProjectPage, EditTaskPage, ShowAllTasksPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.task.DefaultTaskStatus
import com.togger.task.DefaultTaskStatus.{Archived, Backlog, Done, WontDo}
import com.togger.ui.auth.{AccountService, TgCard}
import com.togger.ui.components.{MultiSelectItem, TableFilterComponent}
import com.togger.ui.organization.{OrganizationServiceFe, TgIcons}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.TaskConfigTableSupport.{iconButton, rowButtonMarginCls, rowIconButtonCls}
import com.togger.ui.task.{TaskFeService, TgDateFormater}

import java.time.{LocalDate, ZoneOffset}
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
  organizationId: UUID
)(implicit
  ioRuntime: IORuntime
) {

  val stateFormVar = Var(ShowAllProjectsState())
  private val defaultDeselectedStatusNames = Set(Done, WontDo, Archived, Backlog).map(_.statusName)
  private val statusFilterItemsSignal: Signal[Seq[MultiSelectItem]] =
    stateFormVar.signal.map { state =>
      state.info
        .flatMap(_.tasks.map(_.taskStatus.name))
        .distinct
        .sortBy(name => DefaultTaskStatus.values.find(_.statusName == name).map(_.i).getOrElse(Int.MaxValue))
        .map(name => MultiSelectItem(id = name, name = name, isSelected = !defaultDeselectedStatusNames.contains(name)))
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

      val projectCall = projectService
        .findByOrganizationId(organizationId)
//        .map(response => response.map(success => stateFormVar.set(success.body.get))¬)
        .flatMap { projectResponse =>
          projectResponse.fold(
            e => IO(println(e)),
            success => {
              val projectDtos = success.body.get
              projectDtos
                .map { projectDto =>
                  taskFeService
                    .findByProjectId(projectDto.id)
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
              val selectedDayVar: Var[Option[LocalDate]] = Var(lastCalendarDay(task.logEntries))
              val taskCard = TgCard
                .headerNoFooter(
                  header = TgCard.CardHeader
                    .html(
                      div(
                        cls("flex", "justify-between"),
                        a(
                          cls("font-medium text-fg-brand hover:underline"),
                          cls("mr-2"),
                          task.name,
                          router.navigateTo(EditTaskPage(task.projectId, task.id))
                        ),
                        div(
                          task.taskStatus.name
                        )

                        //                              s"${task.name} - ${task.taskStatus.name} - ${task.assignedTo.map(_.fullName).getOrElse("Not assigned")} "
                      ),
                      TgCard.CardHeaderSize.H3
                    ),
                  body = TgCard.HtmlCardContent(
                    div(
                      div(
                        cls("mb-4"),
                        b("Description: "),
                        task.description
                      ),
                      logEntryCalendar(task.logEntries, selectedDayVar),
                      selectedDayStatus(task.logEntries, selectedDayVar)
                    )
                  )
                )
              taskCard.element.amend(cls("mb-4"))
            }
        )
      )

    }.toSeq

    div(elements)
  }

  private def isWeekend(day: LocalDate): Boolean = {
    val dow = day.getDayOfWeek.getValue
    dow == 6 || dow == 7
  }

  private def lastCalendarDay(logEntries: Seq[LogEntryDto]): Option[LocalDate] =
    logEntries
      .map(entry => entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate)
      .filterNot(isWeekend)
      .maxOption

  private def logEntryCalendar(logEntries: Seq[LogEntryDto], selectedDayVar: Var[Option[LocalDate]]) = {
    val weekdayEntries = logEntries.filterNot(entry => isWeekend(entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate))

    if (weekdayEntries.isEmpty) emptyNode
    else {
      val entriesByDate: Map[LocalDate, Seq[LogEntryDto]] =
        weekdayEntries.groupBy(entry => entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate)

      val minDate = entriesByDate.keys.min
      val maxDate = entriesByDate.keys.max

      val calendarStart = minDate.minusDays(minDate.getDayOfWeek.getValue - 1L)
      val calendarEnd = maxDate.plusDays(5L - maxDate.getDayOfWeek.getValue)

      val allDays =
        Iterator
          .iterate(calendarStart)(_.plusDays(1))
          .takeWhile(!_.isAfter(calendarEnd))
          .filterNot(isWeekend)
          .toSeq

      div(
        marginBottom.rem(1),
        cls("grid grid-cols-5 gap-1"),
        Seq("Mon", "Tue", "Wed", "Thu", "Fri")
          .map(label => div(cls("text-xs font-semibold text-center text-gray-500"), label)),
        allDays.map(day =>
          calendarDayCell(day, entriesByDate.getOrElse(day, Seq.empty).sortBy(_.createdAt), selectedDayVar)
        )
      )
    }
  }

  private def calendarDayCell(
    day: LocalDate,
    entries: Seq[LogEntryDto],
    selectedDayVar: Var[Option[LocalDate]]
  ) = {
    val tooltip =
      if (entries.isEmpty) ""
      else (tgDateFormater.asEuropeanDate(day) +: entries.map(_.status.toStringWithEmoji)).mkString("\n")

    div(
      cls("border-1 border-gray-200 rounded-md cursor-pointer"),
      cls(if (entries.isEmpty) "bg-white" else "bg-gray-50"),
      cls("p-1"),
      cls <-- selectedDayVar.signal.map(selected => if (selected.contains(day)) "ring-2 ring-blue-400" else ""),
      textAlign.center,
      title(tooltip),
      onClick.mapToUnit --> (_ => selectedDayVar.update(current => if (current.contains(day)) None else Some(day))),
      div(cls("text-xs text-gray-500"), tgDateFormater.dayAndMonth(day)),
      div(cls("flex flex-wrap justify-center gap-0.5"), entries.map(entry => span(entry.status.emoji)))
    )
  }

  private def selectedDayStatus(logEntries: Seq[LogEntryDto], selectedDayVar: Var[Option[LocalDate]]) =
    div(
      child <-- selectedDayVar.signal.map {
        case None => emptyNode
        case Some(day) =>
          val dayEntries = logEntries
            .filter(entry => entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate == day)
            .sortBy(_.createdAt)

          div(
            cls("mb-4"),
            p(b(s"Status on ${tgDateFormater.asEuropeanDate(day)}")),
            if (dayEntries.isEmpty) p("No status update")
            else
              dayEntries.map(entry =>
                div(
                  cls("mb-2"),
                  p(b(entry.status.toStringWithEmoji)),
                  entry.textExternal.split("\n").map(line => p(line)).toSeq
                )
              )
          )
      }
    )

}
