package com.togger.ui.task
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, TgIds, WorkDayCalendar}
import com.togger.dto.{FindMilestoneResp, FindProjectResp, FindTaskRespFull, SearchAccountDto, TaskFilter}
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TaskListParams, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.task.DefaultTaskStatus.{Archived, Backlog, Done, WontDo}
import com.togger.task.TaskPriority
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgHtmlTable.createTd
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.components.{
  MultiSelectItem,
  TableFilterComponent,
  TableSearchComponent,
  TgBadge,
  TgBadgeColor,
  TgHtmlTable,
  TgPaginationComponent
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
  params: TaskListParams,
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

  private val paginationVar = Var(Option.empty[PaginationState])

  private val unassignedDto = SearchAccountDto(
    id = TgIds.UNASSIGNED_UUID,
    firstName = "Not",
    lastName = "assigned",
    email = "notassigned@email.com",
    accountTypeId = TgIds.UNASSIGNED_UUID
  )

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

  private def showList(newParams: TaskListParams): Unit =
    router.route(ShowAllTasksPage(projectId, newParams))

  private type Options = Seq[(String, String)]

  /** One filter widget. What is ticked comes from the URL (or the widget's default); ticking something navigates to the
    * first page of the new filter, and the page is then read from the backend with that filter.
    */
  private class FilterSpec[S](
    source: Var[S],
    optionsOf: S => Options,
    defaultSelectedOf: Options => Set[String],
    chosenIn: TaskListParams => Option[Set[String]],
    withChosen: (TaskListParams, Option[Set[String]]) => TaskListParams
  ) {
    val selectedIdsVar: Var[Set[String]] = Var(Set.empty)

    private def options: Options = optionsOf(source.now())
    private def defaultSelected: Set[String] = defaultSelectedOf(options)

    val itemsSignal: Signal[Seq[MultiSelectItem]] =
      source.signal.map { state =>
        val offered = optionsOf(state)
        val ticked = TaskListFilterMapping.selectedFromParams(chosenIn(params), defaultSelectedOf(offered))
        offered.map { case (id, name) => MultiSelectItem(id = id, name = name, isSelected = ticked.contains(id)) }
      }

    def selection: FilterSelection =
      FilterSelection(
        allIds = options.map(_._1).toSet,
        selectedIds = TaskListFilterMapping.selectedFromParams(chosenIn(params), defaultSelected)
      )

    // The ticks the URL itself implies (the widget seeds them when its options arrive) are not a change.
    val navigateOnChange: Modifier[HtmlElement] =
      selectedIdsVar.signal.changes --> Observer[Set[String]] { picked =>
        val current = selection
        if (current.allIds.nonEmpty && picked != current.selectedIds.intersect(current.allIds))
          showList(withChosen(params, TaskListFilterMapping.toParamSelection(picked, defaultSelected)).firstPage)
      }
  }

  private def allOptions(options: Options): Set[String] = options.map(_._1).toSet

  private val statusFilter = new FilterSpec[TaskStatusesState](
    source = statusesVar,
    optionsOf = _.taskStatusesSorted.map(status => status.id.toString -> status.name),
    defaultSelectedOf = _.collect { case (id, name) if !defaultDeselectedStatusNames.contains(name) => id }.toSet,
    chosenIn = _.statuses,
    withChosen = (listParams, chosen) => listParams.copy(statuses = chosen)
  )

  private val priorityFilter = new FilterSpec[Unit](
    source = Var(()),
    optionsOf = _ => TaskPriority.values.sortBy(_.value).map(priority => priority.value.toString -> priority.priorityName),
    defaultSelectedOf = allOptions,
    chosenIn = _.priorities,
    withChosen = (listParams, chosen) => listParams.copy(priorities = chosen)
  )

  private val labelFilter = new FilterSpec[TaskLabelsState](
    source = taskLabelsVar,
    optionsOf = _.taskLabels.sortBy(_.name).map(label => label.id.toString -> label.name),
    defaultSelectedOf = allOptions,
    chosenIn = _.labels,
    withChosen = (listParams, chosen) => listParams.copy(labels = chosen)
  )

  private val assigneeFilter = new FilterSpec[FindProjectResp](
    source = projectVar,
    optionsOf = project =>
      (unassignedDto +: project.members).sortBy(_.fullName.toLowerCase).map(member => member.id.toString -> member.fullName),
    defaultSelectedOf = allOptions,
    chosenIn = _.assignees,
    withChosen = (listParams, chosen) => listParams.copy(assignees = chosen)
  )

  private val milestoneFilter = new FilterSpec[Seq[FindMilestoneResp]](
    source = milestonesVar,
    optionsOf = milestones =>
      milestones.sortBy(_.name.toLowerCase).map(milestone => milestone.id.toString -> milestone.name) :+
        (TaskListFilterMapping.NoMilestoneId -> "No milestone"),
    defaultSelectedOf = allOptions,
    chosenIn = _.milestones,
    withChosen = (listParams, chosen) => listParams.copy(milestones = chosen)
  )

  val searchTermVar = Var(params.search)
  private val searchBox = TableSearchComponent(
    inputSignal = searchTermVar.signal,
    inputStateUpdater = searchTermVar.updater[String]((_, newSearchTerm) => newSearchTerm),
    placeholderValue = "Search by name, description"
  )

  private val navigateOnSearch: Modifier[HtmlElement] =
    searchTermVar.signal.changes --> Observer[String] { term =>
      if (term.trim != params.search) showList(TaskListFilterMapping.withSearch(params, term))
    }

  private val visibleRowsSignal: Signal[Seq[FindTaskRespFull]] = stateFormVar.signal

  private def currentFilter(): TaskFilter =
    TaskListFilterMapping.toTaskFilter(
      statuses = statusFilter.selection,
      priorities = priorityFilter.selection,
      labels = labelFilter.selection,
      assignees = assigneeFilter.selection,
      milestones = milestoneFilter.selection,
      search = params.search
    )

  /** Reads the page the URL asks for. A page past the end (its tasks were deleted) steps back to the last page. */
  private def loadPage(): IO[Unit] =
    // Deferred: the filter depends on the options of the widgets, which may not have arrived when the IO is built.
    IO.defer(taskService.findByProjectId(projectId, currentFilter(), params.pageRequest)).map {
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

  private def reloadPage(): Unit =
    spinnerService.runWithSpinner(loadPage()).unsafeRunAndForget()

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

  /** Stops at the first failure; either way the current page is read again, so the table shows what the backend has. */
  private def saveAll(pending: List[FindTaskRespFull], total: Int, saved: Int): IO[Unit] =
    pending match {
      case Nil =>
        IO(if (total > 1) toast.success(s"$saved tasks updated")) >> loadPage()
      case updated :: rest =>
        taskService.updateTask(ShowAllTasksBulkMapping.toUpdateRequest(updated, tgClock.currentInstant)).flatMap {
          case Left(error) =>
            IO {
              toast.error(error)
              if (saved > 0) toast.success(s"$saved of $total tasks updated")
            } >> loadPage()
          case Right(_) =>
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

  /** A click on the row selects it, like its checkbox, and every cell of a selected row is highlighted; checkboxes, badges, buttons and links keep their own click. */
  private def selectOnClick(id: UUID): Seq[Modifier[HtmlElement]] =
    Seq(
      cls("cursor-pointer"),
      cls("bg-neutral-tertiary-medium") <-- selectedVar.signal.map(_.contains(id)),
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
        span(child.text <-- selectedVar.signal.map(chosen => s"${chosen.size} selected on this page"))
      ),
      editToolbar.element,
      button(
        `type`("button"),
        cls("px-2.5 py-1 text-xs font-medium rounded-base border border-red-600 text-red-600"),
        cls("hover:bg-red-600 hover:text-white cursor-pointer"),
        "Delete",
        onClick.mapToUnit --> (_ => deleteSelected())
      )
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

      // The status filter offers every status, read page by page (bounded by PaginatedLoader).
      val taskStatusCall = taskStatusFeService
        .findAllByProjectId(projectId)
        .map(response => response.map(success => statusesVar.set(TaskStatusesState(success.body.get))))

      // The label filter offers every label, read page by page (bounded by PaginatedLoader).
      val taskLabelCall = taskLabelFeService
        .findAllByProjectId(projectId)
        .map(response => response.map(success => taskLabelsVar.set(TaskLabelsState(success.body.get))))

      // The milestone filter offers every milestone, read page by page (bounded by PaginatedLoader).
      val milestoneCall = milestoneService
        .findAllByProjectId(projectId)
        .map(response => response.map(success => milestonesVar.set(success.body.get)))

      val taskCall = loadPage()

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
          reloadPage()
        }
        .unsafeRunAndForget()
      close()
    }

  /** Deletes the selected tasks one by one; at the first failure it stops, what was deleted is gone from the table. */
  private def deleteSelected(): Unit = {
    val doomed = stateFormVar.now().filter(dto => selectedVar.now().contains(dto.id)).toList
    if (doomed.isEmpty) return
    modalFactory.plainModal(
      message = PlainTextModalMessage(s"Delete ${doomed.size} tasks and all their log entries and links?"),
      title = "Delete tasks",
      variant = PlainModalVariant.Danger,
      confirmButtonMsg = "Yes, delete them",
      cancelButtonMsg = "Cancel"
    ) { close =>
      spinnerService.runWithSpinner(deleteAll(doomed, doomed.size, 0)).unsafeRunAndForget()
      close()
    }
  }

  private def deleteAll(pending: List[FindTaskRespFull], total: Int, deleted: Int): IO[Unit] =
    pending match {
      case Nil => IO(toast.success(s"$deleted tasks deleted")) >> loadPage()
      case dto :: rest =>
        taskService.deleteTask(dto.id).flatMap {
          case Left(error) =>
            IO {
              toast.error(error)
              if (deleted > 0) toast.success(s"$deleted of $total tasks deleted")
            } >> loadPage()
          case Right(_) =>
            IO(stateFormVar.update(_.filterNot(_.id == dto.id))) >> deleteAll(rest, total, deleted + 1)
        }
    }

  /** One tall row spanning every column, so the table keeps its size when no task matches. */
  private def noTasksRow: Seq[ReactiveHtmlElement[HTMLTableCellElement]] =
    Seq(td(colSpan(3), cls("px-6 py-32 text-center text-lg text-body"), "No tasks found"))

  val element = {
    div(
      statusFilter.navigateOnChange,
      priorityFilter.navigateOnChange,
      labelFilter.navigateOnChange,
      assigneeFilter.navigateOnChange,
      milestoneFilter.navigateOnChange,
      navigateOnSearch,
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
                ),
                iconButton(
                  "Timeboxes",
                  TgIcons.listIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(ShowTimeboxesPage(projectId))
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
                          statusFilter.itemsSignal,
                          label = "Status",
                          statusFilter.selectedIdsVar
                        ).element,
                        new TableFilterComponent(
                          priorityFilter.itemsSignal,
                          label = "Priority",
                          priorityFilter.selectedIdsVar
                        ).element,
                        new TableFilterComponent(
                          labelFilter.itemsSignal,
                          label = "Labels",
                          labelFilter.selectedIdsVar
                        ).element,
                        new TableFilterComponent(
                          assigneeFilter.itemsSignal,
                          label = "Assigned to",
                          assigneeFilter.selectedIdsVar
                        ).element,
                        new TableFilterComponent(
                          milestoneFilter.itemsSignal,
                          label = "Milestone",
                          milestoneFilter.selectedIdsVar
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
                    rows => if (rows.isEmpty) Seq(noTasksRow) else rows.map { dto =>
                        Seq(
                          createTd(
                            div(
                              cls("flex items-start gap-2 rounded-base"),
                              cls("opacity-60 pointer-events-none") <-- savingVar.signal.map(_.contains(dto.id)),
                              selectCheckbox(dto.id),
                              div(
                                cls("flex flex-col gap-1"),
                                div(
                                  cls("flex flex-wrap items-center gap-x-2 gap-y-1"),
                                  a(
                                    cls("text-base font-semibold text-heading hover:text-fg-brand hover:underline"),
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
