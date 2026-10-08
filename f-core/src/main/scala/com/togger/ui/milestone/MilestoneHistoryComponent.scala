package com.togger.ui.milestone

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.{FindMilestoneDateResp, FindMilestoneResp, FindProjectResp, FindTaskRespFull, LogEntryDto}
import com.togger.milestone.MilestoneStatus
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.task.DefaultTaskStatus
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{MultiSelectItem, TableFilterComponent, TableSearchComponent, TgBadgeColor, TgTooltip}
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskFeService, TgDateFormater}
import org.scalajs.dom
import org.scalajs.dom.MouseEvent

import java.time.temporal.TemporalAdjusters
import java.time.{DayOfWeek, Instant, LocalDate, ZoneOffset}
import java.util.UUID

class MilestoneHistoryComponent(
  taskService: TaskFeService,
  milestoneService: MilestoneFeService,
  milestoneDateService: MilestoneDateFeService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  tgDateFormater: TgDateFormater,
  projectId: UUID
)(implicit
  ioRuntime: IORuntime
) {

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
  private val tasksVar = Var(Seq.empty[FindTaskRespFull])
  private val logEntriesVar = Var(Seq.empty[LogEntryDto])
  private val milestonesVar = Var(Seq.empty[FindMilestoneResp])
  private val milestoneDatesVar = Var(Seq.empty[FindMilestoneDateResp])

  spinnerService
    .runWithSpinner {
      val projectCall =
        projectService.findById(projectId).map(_.fold(e => println(e), success => projectVar.set(success.body.get)))
      // The history charts every task and log entry of the project, so all pages are read (bounded by PaginatedLoader).
      val tasksCall =
        taskService.findAllByProjectId(projectId).map(_.fold(e => println(e), success => tasksVar.set(success.body.get)))
      val logEntriesCall =
        taskService
          .findAllLogEntriesByProjectId(projectId)
          .map(_.fold(e => println(e), success => logEntriesVar.set(success.body.get)))
      val milestonesCall =
        milestoneService
          .findByProjectId(projectId)
          .map(_.fold(e => println(e), success => milestonesVar.set(success.body.get)))
      val milestoneDatesCall =
        milestoneDateService
          .findByProjectId(projectId)
          .map(_.fold(e => println(e), success => milestoneDatesVar.set(success.body.get)))

      for {
        _ <- projectCall
        _ <- tasksCall
        _ <- logEntriesCall
        _ <- milestonesCall
        _ <- milestoneDatesCall
      } yield ()
    }
    .unsafeRunAndForget()

  private def toLocalDate(instant: Instant): LocalDate =
    instant.atOffset(ZoneOffset.UTC).toLocalDate

  private def isWeekend(date: LocalDate, workDays: Set[DayOfWeek]): Boolean =
    !workDays.contains(date.getDayOfWeek)

  private def weekStart(date: LocalDate): LocalDate =
    date.`with`(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

  private def statusOrder(statusName: String): Int =
    DefaultTaskStatus.values.find(_.statusName == statusName).map(_.i).getOrElse(Int.MaxValue)

  private def taskStatusOrder(task: FindTaskRespFull): Int =
    statusOrder(task.taskStatus.name)

  private def dayPasses(day: LocalDate, filter: ActiveDateFilter): Boolean =
    filter match {
      case ActiveDateFilter.ByMonth(selected) =>
        selected.isEmpty || selected.contains((day.getYear, day.getMonthValue))
      case ActiveDateFilter.ByRange(start, end) =>
        start.forall(!day.isBefore(_)) && end.forall(!day.isAfter(_))
    }

  private val monthOptionsSignal: Signal[Seq[MonthOption]] =
    logEntriesVar.signal.map { entries =>
      entries
        .map(e => toLocalDate(e.createdAt))
        .map(d => (d.getYear, d.getMonthValue))
        .distinct
        .sortBy(identity)
        .map { case (year, month) => MonthOption(year, month) }
    }

  private val dateFilterComponent = new DateFilterComponent(monthOptionsSignal, tgDateFormater)

  private val taskStatusItemsSignal: Signal[Seq[MultiSelectItem]] =
    tasksVar.signal.map(
      _.map(_.taskStatus)
        .distinctBy(_.id)
        .sortBy(s => statusOrder(s.name))
        .map(s => MultiSelectItem(id = s.id.toString, name = s.name, isSelected = true))
    )

  private val taskStatusFilter = new TableFilterComponent(taskStatusItemsSignal, label = "Filter by status")

  private val UnassignedMilestoneId = "unassigned"

  private val milestoneItemsSignal: Signal[Seq[MultiSelectItem]] =
    milestonesVar.signal.map { milestones =>
      milestones.sortBy(_.name).map(m => MultiSelectItem(id = m.id.toString, name = m.name, isSelected = true)) :+
        MultiSelectItem(id = UnassignedMilestoneId, name = "Unassigned", isSelected = true)
    }

  private val milestoneFilter = new TableFilterComponent(milestoneItemsSignal, label = "Filter by milestone")

  private val milestoneStatusItemsSignal: Signal[Seq[MultiSelectItem]] = Val(
    MilestoneStatus.values
      .sortBy(_.value)
      .map(s => MultiSelectItem(id = s.value.toString, name = s.statusName, isSelected = s == MilestoneStatus.InProgress))
  )

  private val milestoneStatusFilter =
    new TableFilterComponent(milestoneStatusItemsSignal, label = "Filter by milestone status")

  private val searchTermVar = Var("")
  private val searchBox = TableSearchComponent(
    inputSignal = searchTermVar.signal,
    inputStateUpdater = searchTermVar.updater[String]((_, newSearchTerm) => newSearchTerm),
    placeholderValue = "Search by name, description",
    debounceMs = 0
  )

  private val taskColWidthPxVar: Var[Double] = Var(192.0) // 12rem at default root font size
  private val dragStateVar: Var[Option[(Double, Double)]] = Var(None) // (startClientX, startWidth)
  private val hoveredRowIndexVar: Var[Option[Int]] = Var(None)

  private def startDrag(ev: MouseEvent): Unit = {
    ev.preventDefault()
    dragStateVar.set(Some((ev.clientX, taskColWidthPxVar.now())))
    dom.document.body.style.cursor = "col-resize"
    dom.document.body.style.setProperty("user-select", "none")
  }

  private def onDragMove(ev: MouseEvent): Unit =
    dragStateVar.now().foreach { case (startX, startWidth) =>
      taskColWidthPxVar.set(math.max(0.0, startWidth + (ev.clientX - startX)))
    }

  private def endDrag(): Unit = {
    dragStateVar.set(None)
    dom.document.body.style.cursor = ""
    dom.document.body.style.setProperty("user-select", "")
  }

  private val weekBackgrounds: Seq[String] = Seq("bg-neutral-tertiary-medium", "bg-neutral-primary")

  private case class MilestoneBox(name: String, rowStart: Int, rowEnd: Int, colStart: Int, colEnd: Int, color: String)

  private case class MonthHeader(name: String, monthNumber: Int, colStart: Int, colEnd: Int)

  private case class MilestoneDateMarker(
    rowStart: Int,
    rowEnd: Int,
    colIndex: Int,
    tooltipText: String
  )

  private case class HistoryLayout(
    orderedTasks: Seq[FindTaskRespFull],
    entriesByTaskAndDay: Map[(UUID, LocalDate), Seq[LogEntryDto]],
    days: Seq[LocalDate],
    weekIndexByDay: Map[LocalDate, Int],
    monthHeaders: Seq[MonthHeader],
    milestoneBoxes: Seq[MilestoneBox],
    hasAnyLogEntries: Boolean,
    latestStatusColorByTask: Map[UUID, String],
    milestoneDateMarkers: Seq[MilestoneDateMarker]
  )

  // Row order: one contiguous block per milestone (alphabetical by milestone name, tasks sorted by name within
  // the block), followed by a trailing block of tasks with no milestone. Contiguous blocks are what let a
  // milestone's red box be a single rectangle spanning exactly its own tasks' rows.
  private val historySignal: Signal[HistoryLayout] =
    tasksVar.signal
      .combineWith(
        logEntriesVar.signal,
        milestonesVar.signal,
        milestoneDatesVar.signal,
        dateFilterComponent.activeFilter,
        taskStatusFilter.selectedIds,
        milestoneFilter.selectedIds,
        milestoneStatusFilter.selectedIds,
        searchBox.inputSignal
      )
      .combineWith(projectVar.signal)
      .map {
        case (
              allTasks,
              allLogEntries,
              milestones,
              milestoneDates,
              activeFilter,
              selectedStatusIds,
              selectedMilestoneIds,
              selectedMilestoneStatusIds,
              searchTerm,
              project
            ) =>
          val workDays = project.workDays.map(DayOfWeek.of)
          val byStatus: Seq[FindTaskRespFull] =
            if (selectedStatusIds.isEmpty) allTasks
            else allTasks.filter(t => selectedStatusIds.contains(t.taskStatus.id.toString))

          def milestoneKey(t: FindTaskRespFull): String = t.milestone.map(_.toString).getOrElse(UnassignedMilestoneId)

          val byMilestone: Seq[FindTaskRespFull] =
            if (selectedMilestoneIds.isEmpty) byStatus
            else byStatus.filter(t => selectedMilestoneIds.contains(milestoneKey(t)))

          val milestoneStatusById: Map[UUID, MilestoneStatus] = milestones.map(m => m.id -> m.status).toMap

          val byMilestoneStatus: Seq[FindTaskRespFull] =
            if (selectedMilestoneStatusIds.isEmpty) byMilestone
            else
              byMilestone.filter(t =>
                t.milestone.flatMap(milestoneStatusById.get) match {
                  case Some(status) => selectedMilestoneStatusIds.contains(status.value.toString)
                  case None         => true
                }
              )

          val needle = searchTerm.trim.toLowerCase
          val tasks: Seq[FindTaskRespFull] =
            if (needle.isEmpty) byMilestoneStatus
            else
              byMilestoneStatus.filter(t =>
                t.name.toLowerCase.contains(needle) || t.description.toLowerCase.contains(needle)
              )

          val allDays: Seq[LocalDate] =
            if (allLogEntries.isEmpty) Seq.empty
            else {
              val dates = allLogEntries.map(e => toLocalDate(e.createdAt))
              val minDate = dates.min
              val maxDate = dates.max
              Iterator
                .iterate(minDate)(_.plusDays(1))
                .takeWhile(!_.isAfter(maxDate))
                .filterNot(isWeekend(_, workDays))
                .toSeq
            }
          val days: Seq[LocalDate] = allDays.filter(day => dayPasses(day, activeFilter))

          val logEntries: Seq[LogEntryDto] =
            allLogEntries.filter(e => dayPasses(toLocalDate(e.createdAt), activeFilter))
          val taskIdsWithVisibleEntries: Set[UUID] = logEntries.map(_.taskId).toSet

          val entriesByTaskAndDay: Map[(UUID, LocalDate), Seq[LogEntryDto]] =
            logEntries.groupBy(e => (e.taskId, toLocalDate(e.createdAt)))

          val latestStatusColorByTask: Map[UUID, String] =
            allLogEntries
              .groupBy(_.taskId)
              .view
              .mapValues(_.sortBy(_.createdAt).last.status.color)
              .toMap

          val weekStartIndex: Map[LocalDate, Int] = days.map(weekStart).distinct.zipWithIndex.toMap
          val weekIndexByDay: Map[LocalDate, Int] = days.map(d => d -> weekStartIndex(weekStart(d))).toMap

          // days is chronologically ordered, so days sharing a (year, month) are always contiguous —
          // a simple fold groups them into runs without needing a stable groupBy.
          val monthHeaders: Seq[MonthHeader] = days.zipWithIndex
            .foldLeft(Vector.empty[(LocalDate, Int, Int)]) { case (acc, (day, idx)) =>
              acc.lastOption match {
                case Some((repDay, startIdx, _)) if repDay.getYear == day.getYear && repDay.getMonth == day.getMonth =>
                  acc.init :+ (repDay, startIdx, idx)
                case _ =>
                  acc :+ (day, idx, idx)
              }
            }
            .map { case (repDay, startIdx, endIdx) =>
              MonthHeader(
                tgDateFormater.monthName(repDay),
                monthNumber = repDay.getMonthValue,
                colStart = startIdx + 1,
                colEnd = endIdx + 2
              )
            }

          val milestoneNamesById: Map[UUID, String] = milestones.map(m => m.id -> m.name).toMap
          val milestonesById: Map[UUID, FindMilestoneResp] = milestones.map(m => m.id -> m).toMap
          val milestoneDatesByMilestoneId: Map[UUID, Seq[FindMilestoneDateResp]] = milestoneDates.groupBy(_.milestoneId)
          val visibleTasks: Seq[FindTaskRespFull] = tasks.filter(t => taskIdsWithVisibleEntries.contains(t.id))
          val tasksByMilestone: Map[UUID, Seq[FindTaskRespFull]] =
            visibleTasks.filter(_.milestone.isDefined).groupBy(_.milestone.get)
          val unassignedTasks: Seq[FindTaskRespFull] =
            visibleTasks.filter(_.milestone.isEmpty).sortBy(t => (taskStatusOrder(t), t.name))

          val orderedMilestoneIds: Seq[UUID] =
            tasksByMilestone.keys.toSeq.sortBy(id => milestoneNamesById.getOrElse(id, id.toString))

          val orderedGroups: Seq[(Option[UUID], Seq[FindTaskRespFull])] =
            orderedMilestoneIds.map(id =>
              Some(id) -> tasksByMilestone(id).sortBy(t => (taskStatusOrder(t), t.name))
            ) :+ (None -> unassignedTasks)

          val orderedTasks: Seq[FindTaskRespFull] = orderedGroups.flatMap(_._2)

          var rowCursor = 0
          val (milestoneBoxes, milestoneDateMarkers)
            : (Seq[MilestoneBox], Seq[MilestoneDateMarker]) =
            orderedGroups.foldLeft((Seq.empty[MilestoneBox], Seq.empty[MilestoneDateMarker])) {
              case ((boxAcc, markerAcc), (milestoneIdOpt, groupTasks)) =>
                val rowStart = rowCursor + 3
                rowCursor += groupTasks.size
                val rowEnd = rowCursor + 3

                milestoneIdOpt match {
                  case Some(milestoneId) if groupTasks.nonEmpty =>
                    milestonesById.get(milestoneId) match {
                      case Some(milestone) =>
                        val milestoneName = milestoneNamesById.getOrElse(milestoneId, "Milestone")
                        val startDate = milestone.startDate
                        // The box's end column tracks the milestone's actual progress, so it's driven by the
                        // last logged activity on the milestone's own tasks (ignoring active filters, so the
                        // box doesn't shrink/grow as the user filters the view) rather than the milestone's
                        // estimated/actual end date fields.
                        val groupTaskIds: Set[UUID] = groupTasks.map(_.id).toSet
                        val lastLogEntryDate: Option[LocalDate] =
                          allLogEntries
                            .filter(e => groupTaskIds.contains(e.taskId))
                            .map(e => toLocalDate(e.createdAt))
                            .maxOption
                        val endDate = lastLogEntryDate.getOrElse(startDate)
                        // Milestone dates are always work days, but may fall outside the currently visible
                        // `days` (e.g. before the earliest logged day, or excluded by the active date filter).
                        // Clip the box to the nearest visible column on each side, and drop it entirely if the
                        // milestone's own range doesn't overlap the visible days at all.
                        val minIdxOpt = days.indexWhere(!_.isBefore(startDate)) match {
                          case -1  => None
                          case idx => Some(idx)
                        }
                        val maxIdxOpt = days.lastIndexWhere(!_.isAfter(endDate)) match {
                          case -1  => None
                          case idx => Some(idx)
                        }
                        val newBoxes = (minIdxOpt, maxIdxOpt) match {
                          case (Some(minIdx), Some(maxIdx)) if minIdx <= maxIdx =>
                            Seq(
                              MilestoneBox(
                                name = milestoneName,
                                rowStart = rowStart,
                                rowEnd = rowEnd,
                                colStart = minIdx + 1,
                                colEnd = maxIdx + 2,
                                color = milestone.status.color
                              )
                            )
                          case _ => Seq.empty
                        }
                        val dueDateMarker = days.indexOf(milestone.dueDate) match {
                          case -1 => Seq.empty
                          case idx =>
                            Seq(
                              MilestoneDateMarker(
                                rowStart = rowStart,
                                rowEnd = rowEnd,
                                colIndex = idx + 1,
                                tooltipText =
                                  s"$milestoneName — due date: ${tgDateFormater.asEuropeanDate(milestone.dueDate)}"
                              )
                            )
                        }
                        // Custom, user-defined dates attached to this milestone (distinct from its own
                        // startDate/dueDate/endDate fields) — each gets its own marker when its
                        // day is currently visible.
                        val customDateMarkers = milestoneDatesByMilestoneId.getOrElse(milestoneId, Seq.empty).flatMap {
                          milestoneDate =>
                            val date = toLocalDate(milestoneDate.date)
                            days.indexOf(date) match {
                              case -1 => None
                              case idx =>
                                Some(
                                  MilestoneDateMarker(
                                    rowStart = rowStart,
                                    rowEnd = rowEnd,
                                    colIndex = idx + 1,
                                    tooltipText =
                                      s"$milestoneName — ${milestoneDate.name}: ${tgDateFormater.asEuropeanDate(date)}"
                                  )
                                )
                            }
                        }
                        (boxAcc ++ newBoxes, markerAcc ++ dueDateMarker ++ customDateMarkers)
                      case None => (boxAcc, markerAcc)
                    }
                  case _ => (boxAcc, markerAcc)
                }
            }

          HistoryLayout(
            orderedTasks,
            entriesByTaskAndDay,
            days,
            weekIndexByDay,
            monthHeaders,
            milestoneBoxes,
            hasAnyLogEntries = allLogEntries.nonEmpty,
            latestStatusColorByTask,
            milestoneDateMarkers
          )
      }

  private def gridPlacement(colStart: Int, colEnd: Int, rowStart: Int, rowEnd: Int): String =
    s"grid-column: $colStart / $colEnd; grid-row: $rowStart / $rowEnd;"

  private def gridCellPlacement(col: Int, row: Int): String =
    gridPlacement(col, col + 1, row, row + 1)

  private def milestoneBoxElement(box: MilestoneBox) =
    div(
      cls("border rounded-sm pointer-events-none"),
      styleAttr :=
        s"${gridPlacement(box.colStart, box.colEnd, box.rowStart, box.rowEnd)} " +
          s"background-color: ${box.color}47; border-color: ${box.color}66;"
    )

  private def milestoneDateMarkerElement(marker: MilestoneDateMarker) = {
    val trigger = div(cls("w-full h-full border-2 border-default-strong rounded-sm hover:bg-neutral-quaternary-medium"))
    TgTooltip(trigger = trigger, text = marker.tooltipText).element.amend(
      styleAttr := gridPlacement(marker.colIndex, marker.colIndex + 1, marker.rowStart, marker.rowEnd)
    )
  }

  private def milestoneNameCell(box: MilestoneBox) =
    div(
      cls(
        "flex items-center justify-center text-[10px] font-semibold text-body whitespace-nowrap overflow-hidden [writing-mode:vertical-rl]"
      ),
      styleAttr := gridPlacement(2, 3, box.rowStart, box.rowEnd),
      box.name
    )

  private def monthHeaderCell(month: MonthHeader) = {
    val colorCls =
      if (month.monthNumber % 2 == 1) " border-default-strong border-x-8 border-y-2 text-heading"
      else " border-default-strong border-y-2 border-x-8 text-heading"
    div(
      cls("flex items-center justify-center text-xs font-semibold rounded-md"),
      cls(colorCls),
      styleAttr := gridPlacement(month.colStart, month.colEnd, 1, 2),
      month.name
    )
  }

  private def dayHeaderCell(day: LocalDate, colIndex: Int, weekIndex: Int) =
    div(
      cls("flex flex-col items-center justify-center text-xs text-center text-body leading-tight font-semibold"),
      cls(weekBackgrounds(weekIndex % weekBackgrounds.size)),
      styleAttr := gridCellPlacement(colIndex, 2),
      div(tgDateFormater.dayOfWeekAbbrev(day)),
      div(tgDateFormater.dayOnly(day))
    )

  private def dayCell(entries: Seq[LogEntryDto], colIndex: Int, rowIndex: Int) = {
    val latest = entries.sortBy(_.createdAt).lastOption
    div(
      cls("flex items-center justify-center"),
      styleAttr := gridCellPlacement(colIndex, rowIndex),
      onMouseEnter --> (_ => hoveredRowIndexVar.set(Some(rowIndex))),
      onMouseLeave --> (_ => hoveredRowIndexVar.set(None)),
      latest match {
        case Some(e) =>
          div(
            title(e.status.toString),
            cls("w-5.5 h-5.5 rounded-md"),
            styleAttr := s"background-color: ${e.status.color};"
          )
        case None =>
          div(cls("w-5.5 h-5.5 rounded-md bg-neutral-quaternary-medium"))
      }
    )
  }

  private case class TaskStatusBadge(text: String, color: TgBadgeColor)

  private val taskStatusAbbreviation: Map[DefaultTaskStatus, String] = Map(
    DefaultTaskStatus.InProgress -> "progress",
    DefaultTaskStatus.Testing -> "test",
    DefaultTaskStatus.Waiting -> "wait",
    DefaultTaskStatus.Blocked -> "block",
    DefaultTaskStatus.Todo -> "todo",
    DefaultTaskStatus.Backlog -> "blog",
    DefaultTaskStatus.Done -> "done",
    DefaultTaskStatus.WontDo -> "wontdo",
    DefaultTaskStatus.Archived -> "archive"
  )

  private def taskStatusBadge(task: FindTaskRespFull): Option[TaskStatusBadge] =
    DefaultTaskStatus.values
      .find(_.statusName == task.taskStatus.name)
      .flatMap(status => taskStatusAbbreviation.get(status).map(TaskStatusBadge(_, task.taskStatus.badgeColor)))

  private def taskStatusBadgeElement(badge: TaskStatusBadge) =
    div(
      title(badge.text),
      cls(
        "flex items-center justify-center rounded-sm shrink-0 mr-1.5 px-0.5 h-3.5 text-[8px] font-semibold leading-none"
      ),
      cls(badge.color.bgCls),
      cls(badge.color.textCls),
      badge.text
    )

  private def taskNameCell(task: FindTaskRespFull, rowIndex: Int, statusColor: Option[String]) =
    div(
      cls("flex items-center cursor-pointer text-fg-brand hover:underline truncate pr-2 bg-neutral-primary-soft"),
      styleAttr := gridCellPlacement(1, rowIndex),
      onMouseEnter --> (_ => hoveredRowIndexVar.set(Some(rowIndex))),
      onMouseLeave --> (_ => hoveredRowIndexVar.set(None)),
      statusColor.map { color =>
        div(
          cls("w-2 h-2 rounded-full shrink-0 mr-1.5"),
          styleAttr := s"background-color: $color;"
        )
      },
      taskStatusBadge(task).map(taskStatusBadgeElement),
      task.name,
      onClick.mapToUnit --> (_ => router.route(EditTaskPage(projectId, task.id)))
    )

  private def taskNameResizeHandle(rowStart: Int, rowEnd: Int) =
    div(
      cls("pointer-events-none flex items-center justify-end"),
      styleAttr := s"grid-column: 1 / 2; grid-row: $rowStart / $rowEnd;",
      div(
        cls("w-2 h-20 bg-neutral-quaternary-medium hover:bg-fg-brand cursor-col-resize pointer-events-auto rounded-full"),
        cls <-- dragStateVar.signal.map(state => if (state.isDefined) "bg-fg-brand" else ""),
        onMouseDown --> (ev => startDrag(ev))
      )
    )

  // A CSS Grid item's containing block for `position: sticky` purposes is its own grid area, not the
  // whole grid — so a sticky first-column cell inside one big scrolling grid can only stay pinned while
  // that narrow column's own track is still scrolled into view, and disappears once the user scrolls past
  // it. To keep the task-name column visible for the entire horizontal scroll range, it lives in its own
  // non-scrolling grid, laid out side by side with a second, independently horizontally-scrolling grid that
  // holds only the day columns.
  private def leftRowHoverOverlay(rowIndex: Int) =
    div(
      cls("pointer-events-none rounded-l-md border-2 border-y-indigo-500"),
      styleAttr := gridPlacement(1, 3, rowIndex, rowIndex + 1)
    )

  private def rightRowHoverOverlay(rowIndex: Int, colEnd: Int) =
    div(
      cls("pointer-events-none rounded-r-md border-2 border-y-indigo-500"),
      styleAttr := gridPlacement(1, colEnd, rowIndex, rowIndex + 1)
    )

  private def renderGrid(layout: HistoryLayout) = {
    val rowsTemplate = "grid-template-rows: 1.75rem 3.25rem; grid-auto-rows: 2.25rem;"
    div(
      cls("flex items-start"),
      div(
        cls("shrink-0"),
        styleAttr <-- taskColWidthPxVar.signal.map(width =>
          s"display: grid; grid-template-columns: ${width}px 2rem; $rowsTemplate"
        ),
        div(cls("bg-neutral-primary-soft"), styleAttr := gridPlacement(1, 2, 1, 3)),
        div(styleAttr := gridPlacement(2, 3, 1, 3)),
        layout.milestoneBoxes.map(milestoneNameCell),
        layout.orderedTasks.zipWithIndex.map { case (task, taskIdx) =>
          taskNameCell(task, taskIdx + 3, layout.latestStatusColorByTask.get(task.id))
        },
        taskNameResizeHandle(rowStart = 3, rowEnd = layout.orderedTasks.size + 3),
        child.maybe <-- hoveredRowIndexVar.signal.map(_.map(rowIndex => leftRowHoverOverlay(rowIndex)))
      ),
      div(
        cls("overflow-x-auto"),
        div(
          styleAttr := s"display: grid; grid-template-columns: repeat(${layout.days.size}, 2rem); $rowsTemplate",
          layout.milestoneBoxes.map(milestoneBoxElement),
          layout.monthHeaders.map(monthHeaderCell),
          layout.days.zipWithIndex.map { case (day, idx) =>
            dayHeaderCell(day, idx + 1, layout.weekIndexByDay.getOrElse(day, 0))
          },
          layout.orderedTasks.zipWithIndex.map { case (task, taskIdx) =>
            val rowIndex = taskIdx + 3
            layout.days.zipWithIndex.map { case (day, idx) =>
              dayCell(layout.entriesByTaskAndDay.getOrElse((task.id, day), Seq.empty), idx + 1, rowIndex)
            }
          },
          layout.milestoneDateMarkers.map(milestoneDateMarkerElement),
          child.maybe <-- hoveredRowIndexVar.signal.map(
            _.map(rowIndex => rightRowHoverOverlay(rowIndex, layout.days.size + 1))
          )
        )
      )
    )
  }

  val element = {
    div(
      windowEvents(_.onMouseMove) --> (ev => onDragMove(ev)),
      windowEvents(_.onMouseUp) --> (_ => endDrag()),
      child <-- projectVar.signal.map(projectResp =>
        TgCard
          .headerNoFooter(
            header = TgCard.CardHeader.html(div(projectResp.name)),
            body = TgCard.HtmlCardContent(div(projectResp.description))
          )
          .element
          .amend(cls("mb-4"))
      ),
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.html(div("Milestone history"), TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              historySignal --> (_ => hoveredRowIndexVar.set(None)),
              div(
                cls("flex justify-between items-start mb-4"),
                searchBox.element,
                div(
                  cls("flex"),
                  dateFilterComponent.element,
                  taskStatusFilter.element,
                  milestoneFilter.element,
                  milestoneStatusFilter.element
                )
              ),
              child <-- historySignal.map { layout =>
                if (!layout.hasAnyLogEntries) div("No log entries yet for this project.")
                else if (layout.days.isEmpty) div("No log entries match the selected filter.")
                else renderGrid(layout)
              }
            )
          )
        )
        .element
    )
  }

}
