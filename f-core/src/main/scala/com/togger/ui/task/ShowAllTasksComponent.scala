package com.togger.ui.task
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.dto.FindTaskRespFull
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TaskListParams, TgRouter}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgHtmlTable.createTd
import com.togger.ui.components.{TgBadge, TgBadgeColor, TgHtmlTable}
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

  private val lookups = new TaskListLookups(
    projectId,
    projectService,
    taskStatusFeService,
    taskLabelFeService,
    milestoneService
  )
  import lookups.{milestonesVar, projectVar, statusesVar, taskLabelsVar}

  private val paramsVar = Var(params)

  private def showList(newParams: TaskListParams): Unit =
    router.route(ShowAllTasksPage(projectId, newParams))

  private val browser = new TaskListBrowser(taskService, lookups, projectId, paramsVar, showList)

  val stateFormVar = browser.rowsVar
  private val visibleRowsSignal = browser.visibleRowsSignal

  private def loadPage(): IO[Unit] = browser.loadPage()

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
      cls("flex flex-wrap items-center gap-3"),
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

  private val noSelectionHint: HtmlElement =
    div(cls("text-sm text-body"), "No tasks selected")
      .amend(cls("hidden") <-- selectedVar.signal.map(_.nonEmpty))

  /** Both states share one box of a fixed height, so the table does not move when the selection starts or ends. */
  private val selectionArea: HtmlElement =
    div(cls("mb-4 flex min-h-8 items-center"), selectionBar, noSelectionHint)

  spinnerService
    .runWithSpinner(lookups.load().flatMap(_ => loadPage()))
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
    Seq(td(colSpan(2), cls("px-6 py-32 text-center text-lg text-body"), "No tasks found"))

  val element = {
    div(
      browser.filterBindings,
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
                  "Task templates",
                  TgIcons.listIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(ShowAllTaskTemplatesPage(projectId))
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
                ),
                iconButton(
                  "Contributions",
                  TgIcons.userGroupIcon(className = "w-4 h-4"),
                  headerIconButtonCls,
                  "mr-2",
                  router.navigateTo(ShowContributionsPage(projectId))
                )
              )
            ),
            TgCard.CardHeaderSize.H3
          ),
          body = TgCard
            .HtmlCardContent(
              div(
                browser.searchBar,
                selectionArea,
                TgHtmlTable.tableElement(
                  Seq[Modifier[ReactiveHtmlElement[HTMLTableCellElement]]](
                    div(
                      cls("flex items-center justify-between gap-4"),
                      div(cls("flex items-center gap-2"), selectAllCheckbox, "Name"),
                      browser.filterButtons
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
                                  badgeRow(dto.id, taskBadges.labelRow(dto)),
                                  TaskProgressComponent
                                    .fromDescription(Val(dto.description), ringSizeCls = "w-3 h-3", textSizeCls = "text-xs")
                                    .element
                                ),
                                badgeRow(dto.id, taskBadges.primaryRow(dto)),
                                badgeRow(dto.id, taskBadges.dateRow(dto))
                              )
                            )
                          ).amend(selectOnClick(dto.id)),
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
                browser.pagination
              )
            )
        )
        .element
    )
  }

}
