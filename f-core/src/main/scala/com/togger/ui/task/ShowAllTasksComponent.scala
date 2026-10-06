package com.togger.ui.task
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, TgIds, WorkDayCalendar}
import com.togger.dto.{FindMilestoneResp, FindProjectResp, FindTaskRespFull, SearchAccountDto}
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.task.DefaultTaskStatus.{Archived, Backlog, Done, WontDo}
import com.togger.task.TaskPriority
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgHtmlTable.createTd
import com.togger.ui.components.{
  MultiSelectItem,
  TableFilterComponent,
  TableSearchComponent,
  TgBadge,
  TgBadgeColor,
  TgHtmlTable
}
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.modal.{ModalFactory, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.organization.TgIcons
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.bulk.{
  BadgeSpec,
  BulkEdit,
  BulkEditContext,
  BulkEditKind,
  BulkEditPopups,
  BulkEditToolbar,
  BulkLookups,
  BulkSelection,
  BulkTaskBadges,
  BulkTaskCard,
  BulkTaskEditor
}
import com.togger.ui.task.TaskConfigTableSupport.{
  headerIconButtonCls,
  iconButton,
  rowButtonMarginCls,
  rowIconButtonCls,
  rowIconButtonDangerCls
}
import com.togger.ui.toast.Toast
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom
import org.scalajs.dom.HTMLTableCellElement
import cats.effect.IO

import java.time.DayOfWeek
import java.util.UUID
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class ShowAllTasksComponent(
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  milestoneService: MilestoneFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  tgClock: TgClock,
  projectId: UUID,
  val toast: Toast,
  modalFactory: ModalFactory
)(implicit
  ioRuntime: IORuntime
) extends ToastMessages {

  val stateFormVar = Var(Seq.empty[FindTaskRespFull])
  val statusesVar = Var(TaskStatusesState())
  val taskLabelsVar = Var(TaskLabelsState())
  val milestonesVar = Var(Seq.empty[FindMilestoneResp])
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

  private val priorityFilterItemsSignal: Signal[Seq[MultiSelectItem]] = Val(
    TaskPriority.values
      .sortBy(_.value)
      .map(p => MultiSelectItem(id = p.value.toString, name = p.priorityName, isSelected = true))
  )
  private val priorityFilterSelectedIdsVar = Var(Set.empty[String])

  private val labelFilterItemsSignal: Signal[Seq[MultiSelectItem]] =
    taskLabelsVar.signal.map(
      _.taskLabels.sortBy(_.name).map(l => MultiSelectItem(id = l.id.toString, name = l.name, isSelected = true))
    )
  private val labelFilterSelectedIdsVar = Var(Set.empty[String])

  private val unassignedDto = SearchAccountDto(
    id = TgIds.UNASSIGNED_UUID,
    firstName = "Not",
    lastName = "assigned",
    email = "notassigned@email.com",
    accountTypeId = TgIds.UNASSIGNED_UUID
  )

  private val assigneeFilterItemsSignal: Signal[Seq[MultiSelectItem]] =
    stateFormVar.signal.map(
      _.map(_.assignedTo.getOrElse(unassignedDto))
        .distinctBy(_.id)
        .sortBy(_.fullName.toLowerCase)
        .map(a => MultiSelectItem(id = a.id.toString, name = a.fullName, isSelected = true))
    )
  private val assigneeFilterSelectedIdsVar = Var(Set.empty[String])

  private val noMilestoneId = "none"
  private val milestoneFilterItemsSignal: Signal[Seq[MultiSelectItem]] =
    milestonesVar.signal.map(milestones =>
      milestones.sortBy(_.name.toLowerCase).map(m => MultiSelectItem(id = m.id.toString, name = m.name, isSelected = true)) :+
        MultiSelectItem(id = noMilestoneId, name = "No milestone", isSelected = true)
    )
  private val milestoneFilterSelectedIdsVar = Var(Set.empty[String])

  val searchTermVar = Var("")
  private val searchBox =TableSearchComponent(
    inputSignal = searchTermVar.signal,
    inputStateUpdater = searchTermVar.updater[String]((_, newSearchTerm) => newSearchTerm),
    placeholderValue = "Search by name, description",
    debounceMs = 0
  )

  private val filteredRowsSignal: Signal[Seq[FindTaskRespFull]] =
    stateFormVar.signal
      .combineWith(
        statusFilterSelectedIdsVar.signal,
        priorityFilterSelectedIdsVar.signal,
        labelFilterSelectedIdsVar.signal,
        assigneeFilterSelectedIdsVar.signal,
        milestoneFilterSelectedIdsVar.signal,
        searchBox.inputSignal
      )
      .map {
        case (dtos, selectedIds, selectedPriorityIds, selectedLabelIds, selectedAssigneeIds, selectedMilestoneIds, term) =>
        val byStatus =
          if (selectedIds.isEmpty) dtos else dtos.filter(dto => selectedIds.contains(dto.taskStatus.id.toString))
        val byPriority =
          if (selectedPriorityIds.isEmpty) byStatus
          else byStatus.filter(dto => selectedPriorityIds.contains(dto.priority.value.toString))
        // Unlabeled tasks always pass: with every label selected by default, the "any-of-selected" test below
        // would otherwise hide them, since an empty label set can never match a selected label id.
        val byLabel =
          if (selectedLabelIds.isEmpty) byPriority
          else
            byPriority.filter(dto =>
              dto.taskLabels.isEmpty || dto.taskLabels.exists(l => selectedLabelIds.contains(l.id.toString))
            )
        val byAssignee =
          if (selectedAssigneeIds.isEmpty) byLabel
          else byLabel.filter(dto => selectedAssigneeIds.contains(dto.assignedTo.getOrElse(unassignedDto).id.toString))
        val byMilestone =
          if (selectedMilestoneIds.isEmpty) byAssignee
          else
            byAssignee.filter(dto =>
              selectedMilestoneIds.contains(dto.milestone.map(_.toString).getOrElse(noMilestoneId))
            )
        val needle = term.trim.toLowerCase
        if (needle.isEmpty) byMilestone
        else
          byMilestone.filter(dto =>
            dto.name.toLowerCase.contains(needle) || dto.description.toLowerCase.contains(needle)
          )
      }
  private val visibleRowsSignal: Signal[Seq[FindTaskRespFull]] =
    filteredRowsSignal.combineWith(statusesVar.signal).map { case (rows, statuses) =>
      rows.sortBy(dto => statuses.idToOrder(dto.taskStatus.id))
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

  private object taskBadges {

    def labelRow(dto: FindTaskRespFull): Seq[BadgeSpec] =
      if (dto.taskLabels.isEmpty) Seq(BadgeSpec(BulkTaskBadges.EmptyLabels, TgBadgeColor.Gray, Some(BulkEditKind.Labels)))
      else
        dto.taskLabels.sortBy(_.name).map(label => BadgeSpec(label.name, label.badgeColor, Some(BulkEditKind.Labels)))

    def primaryRow(dto: FindTaskRespFull): Seq[BadgeSpec] =
      Seq(
        dto.milestone
          .flatMap(id => milestonesVar.now().find(_.id == id))
          .map(milestone => BadgeSpec(milestone.name, TgBadgeColor.Alternative, Some(BulkEditKind.Milestone)))
          .getOrElse(BadgeSpec(BulkTaskBadges.EmptyMilestone, TgBadgeColor.Gray, Some(BulkEditKind.Milestone))),
        BadgeSpec(dto.priority.shortCode, dto.priority.badgeColor, Some(BulkEditKind.Priority)),
        BadgeSpec(dto.taskStatus.name, dto.taskStatus.badgeColor, Some(BulkEditKind.Status)),
        dto.assignedTo
          .map(member => BadgeSpec(member.fullName, TgBadgeColor.Gray, Some(BulkEditKind.Assignee)))
          .getOrElse(BadgeSpec(BulkTaskBadges.EmptyAssignee, TgBadgeColor.Gray, Some(BulkEditKind.Assignee)))
      )

    def dateRow(dto: FindTaskRespFull): Seq[BadgeSpec] =
      Seq(
        BadgeSpec(
          tgDateFormater.asAmericanDateWithDayAbrv(dto.startDate),
          TgBadgeColor.Gray,
          Some(BulkEditKind.StartDate)
        ),
        BadgeSpec(
          tgDateFormater.asAmericanDateWithDayAbrv(dto.dueDate),
          TgBadgeColor.Gray,
          Some(BulkEditKind.DueDate)
        ),
        daysLeft(dto)
      ) ++ dto.endDate.map(date => BadgeSpec(s"End: ${tgDateFormater.asEuropeanDate(date)}", TgBadgeColor.Gray))
  }

  private def daysLeft(dto: FindTaskRespFull): BadgeSpec = {
    val today = tgClock.currentDate()
    if (dto.dueDate.isBefore(today)) BadgeSpec("Overdue", TgBadgeColor.Red)
    else {
      val left = WorkDayCalendar.countWorkDays(today, dto.dueDate, projectVar.now().workDays.map(DayOfWeek.of))
      BadgeSpec(s"$left days left", TgBadgeColor.Gray)
    }
  }

  private def badgeRow(taskId: UUID, specs: Seq[BadgeSpec]) =
    div(
      cls("flex flex-wrap items-center gap-1.5"),
      BulkTaskCard.row(taskId, specs, popups)
    )

  private def lookupsNow(): BulkLookups =
    BulkLookups(
      statuses = statusesVar.now().taskStatuses,
      labels = taskLabelsVar.now().taskLabels,
      members = projectVar.now().members,
      milestones = milestonesVar.now(),
      workDays = projectVar.now().workDays.map(DayOfWeek.of)
    )

  private val selectedVar = Var(Set.empty[UUID])
  private val selectionAnchor = Var(Option.empty[UUID])
  private val visibleIdsVar = Var(Seq.empty[UUID])

  /** Tasks whose update is in flight; they can't be edited again until it is done, so a second edit never sends the
    * labels or dates the first one has not saved yet.
    */
  private val savingVar = Var(Set.empty[UUID])

  private val popups: BulkEditPopups = new BulkEditPopups(
    context = scope => {
      val edited = scope.fold(selectedVar.now())(Set(_))
      BulkEditContext(
        lookupsNow(),
        stateFormVar.now().filter(dto => edited.contains(dto.id)).map(ShowAllTasksBulkMapping.toBulkTask),
        tgClock.currentDate()
      )
    },
    onEdit = saveEdit
  )

  private val editToolbar = new BulkEditToolbar(popups)

  /** Saves the edit for the task, or for the selected tasks, one request each. A task the edit would not change (a date
    * that is not a work day, the value it already has) is left alone with no request.
    */
  private def saveEdit(scope: Option[UUID], edit: BulkEdit): Unit = {
    val lookups = lookupsNow()
    val edited = scope.fold(selectedVar.now())(Set(_)) -- savingVar.now()
    val changed = stateFormVar.now().filter(dto => edited.contains(dto.id)).flatMap { dto =>
      val before = ShowAllTasksBulkMapping.toBulkTask(dto)
      val after = BulkTaskEditor(Seq(before), Set(dto.id), edit, lookups.workDays).head
      Option.when(after != before)(ShowAllTasksBulkMapping.applyTo(dto, after, lookups))
    }
    if (changed.isEmpty) return
    savingVar.update(_ ++ changed.map(_.id))
    spinnerService
      .runWithSpinner(saveAll(changed.toList, changed.size, 0))
      .guarantee(IO(savingVar.update(_ -- changed.map(_.id))))
      .unsafeRunAndForget()
  }

  /** Stops at the first failure: what was saved stays in the table, the rest stays as it was. */
  private def saveAll(pending: List[FindTaskRespFull], total: Int, saved: Int): IO[Unit] =
    pending match {
      case Nil =>
        IO(if (total > 1) toast.success(s"$saved tasks updated"))
      case updated :: rest =>
        taskService.updateTask(ShowAllTasksBulkMapping.toUpdateRequest(updated, tgClock.currentInstant)).flatMap {
          case Left(error) =>
            IO {
              toast.error(error)
              if (saved > 0) toast.success(s"$saved of $total tasks updated")
            }
          case Right(_) =>
            IO(stateFormVar.update(_.map(row => if (row.id == updated.id) updated else row))) >>
              saveAll(rest, total, saved + 1)
        }
    }

  private def onSelectClick(id: UUID, shift: Boolean): Unit = {
    val (updated, anchor) =
      BulkSelection.click(selectedVar.now(), visibleIdsVar.now(), selectionAnchor.now(), id, shift)
    selectedVar.set(updated)
    selectionAnchor.set(anchor)
  }

  private val checkboxCls = "w-4 h-4 border border-default-strong rounded-xs bg-neutral-secondary-strong cursor-pointer"

  private def selectCheckbox(id: UUID): HtmlElement =
    input(
      `type`("checkbox"),
      cls(checkboxCls),
      cls("mt-1 shrink-0"),
      aria.label("Select task"),
      checked <-- selectedVar.signal.map(_.contains(id)),
      onClick --> (event => onSelectClick(id, event.shiftKey))
    )

  /** A click on the row selects it, like its checkbox; checkboxes, badges, buttons and links keep their own click. */
  private def selectOnClick(id: UUID): Seq[Modifier[HtmlElement]] =
    Seq(
      onClick.filter(event => !isInteractive(event.target)) --> (event => onSelectClick(id, event.shiftKey))
    )

  private def isInteractive(target: dom.EventTarget): Boolean =
    target match {
      case element: dom.Element => element.closest("button, input, a") != null
      case _                    => false
    }

  private val selectAllCheckbox: HtmlElement =
    input(
      `type`("checkbox"),
      cls(checkboxCls),
      aria.label("Select all tasks"),
      checked <-- selectedVar.signal.combineWith(visibleIdsVar.signal).map { case (chosen, ids) =>
        ids.nonEmpty && ids.forall(chosen.contains)
      },
      onClick.mapToUnit --> (_ => selectedVar.update(BulkSelection.toggleAll(_, visibleIdsVar.now())))
    )

  private val selectionBar: HtmlElement =
    div(
      cls("mb-4 flex flex-wrap items-center gap-3"),
      div(
        cls("flex items-center gap-2 text-sm text-body"),
        span(child.text <-- selectedVar.signal.map(chosen => s"${chosen.size} selected"))
      ),
      editToolbar.element
    ).amend(cls("hidden") <-- selectedVar.signal.map(_.isEmpty))

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

      val milestoneCall = milestoneService
        .findByProjectId(projectId)
        .map(response => response.map(success => milestonesVar.set(success.body.get)))

      val taskCall = taskService
        .findByProjectId(projectId)
        .map(response => response.map(success => stateFormVar.set(success.body.get)))

      projectCall
        .flatMap(_ => taskStatusCall)
        .flatMap(_ => taskLabelCall)
        .flatMap(_ => milestoneCall)
        .flatMap(_ => taskCall)
    }
    .unsafeRunAndForget()

  private def deleteTask(dto: FindTaskRespFull): Unit =
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete task '${dto.name}' and all its log entries and links?"),
      title = "Delete task",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete it",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService
        .runWithSpinner(taskService.deleteTask(dto.id))
        .toastBackendMessageCodeBlock {
          stateFormVar.update(_.filterNot(_.id == dto.id))
        }
        .unsafeRunAndForget()
      close()
    }

  val element = {
    div(
      visibleRowsSignal.map(_.map(_.id)) --> visibleIdsVar.writer,
      visibleIdsVar.signal --> (ids => selectedVar.update(BulkSelection.prune(_, ids))),
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
            body = TgCard.HtmlCardContent(
              div(
                div(projectResp.description),
                Option.when(projectResp.projectLabels.nonEmpty)(
                  div(
                    cls("flex flex-wrap gap-1 mt-2"),
                    projectResp.projectLabels.map(ts => TgBadge(text = ts.name, color = TgBadgeColor.Indigo, bordered = true).element)
                  )
                )
              )
            )
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
                  "Milestone",
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
                selectionBar,
                TgHtmlTable.tableElement(
                  Seq[Modifier[ReactiveHtmlElement[HTMLTableCellElement]]](
                    div(cls("flex items-center gap-2"), selectAllCheckbox, "Name"),
                    Seq[Modifier[ReactiveHtmlElement[HTMLTableCellElement]]](
                      cls("!px-2 w-0 whitespace-nowrap"),
                      div(
                        cls("flex items-center gap-2"),
                        new TableFilterComponent(
                          statusFilterItemsSignal,
                          label = "Status",
                          statusFilterSelectedIdsVar
                        ).element,
                        new TableFilterComponent(
                          priorityFilterItemsSignal,
                          label = "Priority",
                          priorityFilterSelectedIdsVar
                        ).element,
                        new TableFilterComponent(
                          labelFilterItemsSignal,
                          label = "Labels",
                          labelFilterSelectedIdsVar
                        ).element,
                        new TableFilterComponent(
                          assigneeFilterItemsSignal,
                          label = "Assigned to",
                          assigneeFilterSelectedIdsVar
                        ).element,
                        new TableFilterComponent(
                          milestoneFilterItemsSignal,
                          label = "Milestone",
                          milestoneFilterSelectedIdsVar
                        ).element
                      )
                    ),
                    Seq[Modifier[ReactiveHtmlElement[HTMLTableCellElement]]](
                      cls("!px-2 w-0 whitespace-nowrap text-center"),
                      "Action"
                    )
                  ),
                  striped = false
                )(
                  visibleRowsSignal.map(
                    _.map { dto =>
                        Seq(
                          createTd(
                            div(
                              cls("flex items-start gap-2 rounded-base"),
                              cls("bg-neutral-tertiary-medium") <-- selectedVar.signal.map(_.contains(dto.id)),
                              cls("opacity-60 pointer-events-none") <-- savingVar.signal.map(_.contains(dto.id)),
                              selectCheckbox(dto.id),
                              div(
                                cls("flex flex-col gap-1"),
                                div(
                                  cls("flex flex-wrap items-center gap-x-2 gap-y-1"),
                                  a(
                                    cls("text-base font-semibold text-body hover:text-fg-brand hover:underline"),
                                    dto.name,
                                    router.navigateTo(EditTaskPage(dto.projectId, dto.id))
                                  ),
                                  badgeRow(dto.id, taskBadges.labelRow(dto))
                                ),
                                badgeRow(dto.id, taskBadges.primaryRow(dto)),
                                badgeRow(dto.id, taskBadges.dateRow(dto))
                              )
                            )
                          ).amend(selectOnClick(dto.id)),
                          createTd(emptyMod).amend(selectOnClick(dto.id)),
                          td(
                            cls("!px-2 w-0 whitespace-nowrap"),
                            selectOnClick(dto.id),
                            div(
                              iconButton(
                                "Edit",
                                TgIcons.editIcon(className = "w-4 h-4"),
                                rowIconButtonCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit --> (_ => router.route(EditTaskPage(dto.projectId, dto.id)))
                              ),
                              iconButton(
                                "Delete",
                                TgIcons.trashIcon(className = "w-4 h-4"),
                                rowIconButtonDangerCls,
                                rowButtonMarginCls,
                                onClick.mapToUnit --> (_ => deleteTask(dto))
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
        .element
    )
  }

}
