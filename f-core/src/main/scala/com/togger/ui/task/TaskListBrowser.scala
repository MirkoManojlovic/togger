package com.togger.ui.task

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.togger.common.TgIds
import com.togger.dto.{FindMilestoneResp, FindProjectResp, FindTaskRespFull, SearchAccountDto, TaskFilter}
import com.togger.routes.TaskListParams
import com.togger.task.DefaultTaskStatus
import com.togger.task.DefaultTaskStatus.{Backlog, Done, WontDo}
import com.togger.task.TaskPriority
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.auth.TgCheckbox
import com.togger.ui.components.{
  MultiSelectItem,
  TableFilterComponent,
  TableSearchComponent,
  TableServerSearchComponent,
  TgPaginationComponent
}

import java.util.UUID

/** The paged, searchable and filtered task list shared by the task screens: the current page of tasks, the backend search,
  * the local filter, the column filters and the pagination widget. The screen puts them in its own table.
  *
  * The page, the filters and the search term live in `paramsVar`. A change is not applied by the browser itself: it is
  * handed to `navigate`, which stores the new params in `paramsVar` (a screen that keeps them in the URL routes there,
  * and the page is rebuilt) and reads the page again.
  */
class TaskListBrowser(
  taskService: TaskFeService,
  lookups: TaskListLookups,
  projectId: UUID,
  paramsVar: Var[TaskListParams],
  navigate: TaskListParams => Unit,
  searchPlaceholder: String = "Search all tasks by name, description",
  defaultDeselectedStatuses: Set[DefaultTaskStatus] = Set(Done, WontDo, Backlog)
)(implicit ioRuntime: IORuntime) {

  import lookups.{milestonesVar, projectVar, statusesVar, taskLabelsVar}

  val rowsVar = Var(Seq.empty[FindTaskRespFull])

  private val paginationVar = Var(Option.empty[PaginationState])
  private val showArchivedVar = Var(false)
  private val defaultDeselectedStatusNames = defaultDeselectedStatuses.map(_.toStringWithEmoji)

  private val unassignedDto = SearchAccountDto(
    id = TgIds.UNASSIGNED_UUID,
    firstName = "Not",
    lastName = "assigned",
    email = "notassigned@email.com",
    accountTypeId = TgIds.UNASSIGNED_UUID
  )

  private def params: TaskListParams = paramsVar.now()

  private type Options = Seq[(String, String)]

  /** One filter widget. What is ticked comes from the params (or the widget's default); ticking something navigates to
    * the first page of the new filter, and the page is then read from the backend with that filter.
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
      source.signal.combineWith(paramsVar.signal).map { case (state, current) =>
        val offered = optionsOf(state)
        val ticked = TaskListFilterMapping.selectedFromParams(chosenIn(current), defaultSelectedOf(offered))
        offered.map { case (id, name) => MultiSelectItem(id = id, name = name, isSelected = ticked.contains(id)) }
      }

    def selection: FilterSelection =
      FilterSelection(
        allIds = options.map(_._1).toSet,
        selectedIds = TaskListFilterMapping.selectedFromParams(chosenIn(params), defaultSelected)
      )

    // The ticks the params themselves imply (the widget seeds them when its options arrive) are not a change.
    val navigateOnChange: Modifier[HtmlElement] =
      selectedIdsVar.signal.changes --> Observer[Set[String]] { picked =>
        val current = selection
        if (current.allIds.nonEmpty && picked != current.selectedIds.intersect(current.allIds))
          navigate(withChosen(params, TaskListFilterMapping.toParamSelection(picked, defaultSelected)).firstPage)
      }
  }

  private def allOptions(options: Options): Set[String] = options.map(_._1).toSet

  private val statusFilter = new FilterSpec[TaskStatusesState](
    source = statusesVar,
    optionsOf = _.taskStatusesSorted.map(status => status.id.toString -> status.badgeText),
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
    optionsOf = _.taskLabels.sortBy(_.name).map(label => label.id.toString -> label.badgeText),
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

  private val searchBox = TableServerSearchComponent(
    initialTerm = params.search,
    placeholderValue = searchPlaceholder,
    onSearch = term => if (term.trim != params.search) navigate(TaskListFilterMapping.withSearch(params, term))
  )

  /** Narrows the tasks already shown on this page; no request is made. */
  private val localFilterVar = Var("")
  private val localFilterBox = TableSearchComponent(
    inputSignal = localFilterVar.signal,
    inputStateUpdater = localFilterVar.writer,
    placeholderValue = "Filter shown tasks"
  )

  private def matchesLocalFilter(dto: FindTaskRespFull, term: String): Boolean = {
    val needle = term.trim.toLowerCase
    needle.isEmpty || dto.name.toLowerCase.contains(needle) || dto.description.toLowerCase.contains(needle)
  }

  /** The tasks of the current page that match the local filter. */
  val visibleRowsSignal: Signal[Seq[FindTaskRespFull]] =
    rowsVar.signal.combineWith(localFilterVar.signal).map { case (rows, term) =>
      rows.filter(matchesLocalFilter(_, term))
    }

  private def currentFilter(): TaskFilter =
    TaskListFilterMapping.toTaskFilter(
      statuses = statusFilter.selection,
      priorities = priorityFilter.selection,
      labels = labelFilter.selection,
      assignees = assigneeFilter.selection,
      milestones = milestoneFilter.selection,
      search = params.search,
      includeArchived = showArchivedVar.now()
    )

  /** Reads the page the params ask for. A page past the end (its tasks were deleted) steps back to the last page. */
  def loadPage(): IO[Unit] =
    // Deferred: the filter depends on the options of the widgets, which may not have arrived when the IO is built.
    IO.defer(taskService.findByProjectId(projectId, currentFilter(), params.pageRequest)).map {
      case Left(failure) => println(failure)
      case Right(success) =>
        val response = success.body.get
        if (response.items.isEmpty && response.totalNumberOfPages > 0 && params.page > response.totalNumberOfPages)
          navigate(params.atPage(response.totalNumberOfPages.toInt, params.size))
        else {
          rowsVar.set(response.items)
          paginationVar.set(Some(PaginationState.fromResponse(response)))
        }
    }

  /** Both search boxes, side by side. */
  def searchBar: HtmlElement =
    div(
      cls("mb-4 flex flex-wrap items-center justify-between gap-3"),
      searchBox.element,
      localFilterBox.element
    )

  /** The column filter buttons. */
  def filterButtons: HtmlElement =
    div(
      cls("flex items-center justify-end gap-2"),
      new TableFilterComponent(statusFilter.itemsSignal, label = "Status", statusFilter.selectedIdsVar).element,
      new TableFilterComponent(priorityFilter.itemsSignal, label = "Priority", priorityFilter.selectedIdsVar).element,
      new TableFilterComponent(labelFilter.itemsSignal, label = "Labels", labelFilter.selectedIdsVar).element,
      new TableFilterComponent(assigneeFilter.itemsSignal, label = "Assignee", assigneeFilter.selectedIdsVar).element,
      new TableFilterComponent(milestoneFilter.itemsSignal, label = "Milestone", milestoneFilter.selectedIdsVar).element,
      TgCheckbox
        .checkBox(showArchivedVar.signal, showArchivedVar.writer, "Show archived")
        .htmlElement
        .amend(cls("!w-auto whitespace-nowrap"))
    )

  /** Put these on an element of the screen: they turn a filter change into a navigation. */
  val filterBindings: Seq[Modifier[HtmlElement]] = Seq(
    statusFilter.navigateOnChange,
    priorityFilter.navigateOnChange,
    labelFilter.navigateOnChange,
    assigneeFilter.navigateOnChange,
    milestoneFilter.navigateOnChange,
    showArchivedVar.signal.changes --> Observer[Boolean](_ => loadPage().unsafeRunAndForget())
  )

  def pagination =
    TgPaginationComponent(
      paginationVar.signal,
      (newPage, newSize) => navigate(params.atPage(newPage, newSize))
    )
}
