package com.togger.ui.milestone

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.dto.{FindMilestoneResp, FindProjectResp, FindTaskRespFull, LogEntryDto}
import com.togger.routes.Pages._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.ui.auth.TgCard
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.{TaskFeService, TgDateFormater}

import java.time.temporal.TemporalAdjusters
import java.time.{DayOfWeek, Instant, LocalDate, ZoneOffset}
import java.util.UUID

class MilestoneHistoryComponent(
  taskService: TaskFeService,
  milestoneService: MilestoneFeService,
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
      members = Seq.empty
    )
  )
  private val tasksVar = Var(Seq.empty[FindTaskRespFull])
  private val logEntriesVar = Var(Seq.empty[LogEntryDto])
  private val milestonesVar = Var(Seq.empty[FindMilestoneResp])

  spinnerService
    .runWithSpinner {
      val projectCall =
        projectService.findById(projectId).map(_.fold(e => println(e), success => projectVar.set(success.body.get)))
      val tasksCall =
        taskService.findByProjectId(projectId).map(_.fold(e => println(e), success => tasksVar.set(success.body.get)))
      val logEntriesCall =
        taskService
          .findLogEntriesByProjectId(projectId)
          .map(_.fold(e => println(e), success => logEntriesVar.set(success.body.get)))
      val milestonesCall =
        milestoneService
          .findByProjectId(projectId)
          .map(_.fold(e => println(e), success => milestonesVar.set(success.body.get)))

      for {
        _ <- projectCall
        _ <- tasksCall
        _ <- logEntriesCall
        _ <- milestonesCall
      } yield ()
    }
    .unsafeRunAndForget()

  private def toLocalDate(instant: Instant): LocalDate =
    instant.atOffset(ZoneOffset.UTC).toLocalDate

  private def isWeekend(date: LocalDate): Boolean =
    date.getDayOfWeek == DayOfWeek.SATURDAY || date.getDayOfWeek == DayOfWeek.SUNDAY

  private def weekStart(date: LocalDate): LocalDate =
    date.`with`(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

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

  private val weekBackgrounds: Seq[String] = Seq("bg-stone-200", "bg-white-100")

  private case class MilestoneBox(name: String, rowStart: Int, rowEnd: Int, colStart: Int, colEnd: Int)

  private case class MonthHeader(name: String, monthNumber: Int, colStart: Int, colEnd: Int)

  private case class HistoryLayout(
    orderedTasks: Seq[FindTaskRespFull],
    entriesByTaskAndDay: Map[(UUID, LocalDate), Seq[LogEntryDto]],
    days: Seq[LocalDate],
    weekIndexByDay: Map[LocalDate, Int],
    monthHeaders: Seq[MonthHeader],
    milestoneBoxes: Seq[MilestoneBox],
    hasAnyLogEntries: Boolean
  )

  // Row order: one contiguous block per milestone (alphabetical by milestone name, tasks sorted by name within
  // the block), followed by a trailing block of tasks with no milestone. Contiguous blocks are what let a
  // milestone's red box be a single rectangle spanning exactly its own tasks' rows.
  private val historySignal: Signal[HistoryLayout] =
    tasksVar.signal.combineWith(logEntriesVar.signal, milestonesVar.signal, dateFilterComponent.activeFilter).map {
      case (tasks, allLogEntries, milestones, activeFilter) =>
        val allDays: Seq[LocalDate] =
          if (allLogEntries.isEmpty) Seq.empty
          else {
            val dates = allLogEntries.map(e => toLocalDate(e.createdAt))
            val minDate = dates.min
            val maxDate = dates.max
            Iterator.iterate(minDate)(_.plusDays(1)).takeWhile(!_.isAfter(maxDate)).filterNot(isWeekend).toSeq
          }
        val days: Seq[LocalDate] = allDays.filter(day => dayPasses(day, activeFilter))

        val logEntries: Seq[LogEntryDto] = allLogEntries.filter(e => dayPasses(toLocalDate(e.createdAt), activeFilter))
        val taskIdsWithVisibleEntries: Set[UUID] = logEntries.map(_.taskId).toSet

        val entriesByTaskAndDay: Map[(UUID, LocalDate), Seq[LogEntryDto]] =
          logEntries.groupBy(e => (e.taskId, toLocalDate(e.createdAt)))

        val dayIndex: Map[LocalDate, Int] = days.zipWithIndex.toMap

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
              colStart = startIdx + 3,
              colEnd = endIdx + 4
            )
          }

        val milestoneNamesById: Map[UUID, String] = milestones.map(m => m.id -> m.name).toMap
        val visibleTasks: Seq[FindTaskRespFull] = tasks.filter(t => taskIdsWithVisibleEntries.contains(t.id))
        val tasksByMilestone: Map[UUID, Seq[FindTaskRespFull]] =
          visibleTasks.filter(_.milestone.isDefined).groupBy(_.milestone.get)
        val unassignedTasks: Seq[FindTaskRespFull] = visibleTasks.filter(_.milestone.isEmpty).sortBy(_.name)

        val orderedMilestoneIds: Seq[UUID] =
          tasksByMilestone.keys.toSeq.sortBy(id => milestoneNamesById.getOrElse(id, id.toString))

        val orderedGroups: Seq[(Option[UUID], Seq[FindTaskRespFull])] =
          orderedMilestoneIds.map(id => Some(id) -> tasksByMilestone(id).sortBy(_.name)) :+ (None -> unassignedTasks)

        val orderedTasks: Seq[FindTaskRespFull] = orderedGroups.flatMap(_._2)

        var rowCursor = 0
        val milestoneBoxes: Seq[MilestoneBox] = orderedGroups.flatMap { case (milestoneIdOpt, groupTasks) =>
          val rowStart = rowCursor + 3
          rowCursor += groupTasks.size
          val rowEnd = rowCursor + 3

          milestoneIdOpt match {
            case Some(milestoneId) if groupTasks.nonEmpty =>
              val groupTaskIds = groupTasks.map(_.id).toSet
              val groupDates = logEntries
                .filter(e => groupTaskIds.contains(e.taskId))
                .map(e => toLocalDate(e.createdAt))
                .filter(dayIndex.contains)
              if (groupDates.isEmpty) Seq.empty
              else {
                val minIdx = dayIndex(groupDates.min)
                val maxIdx = dayIndex(groupDates.max)
                Seq(
                  MilestoneBox(
                    name = milestoneNamesById.getOrElse(milestoneId, "Milestone"),
                    rowStart = rowStart,
                    rowEnd = rowEnd,
                    colStart = minIdx + 3,
                    colEnd = maxIdx + 4
                  )
                )
              }
            case _ => Seq.empty
          }
        }

        HistoryLayout(
          orderedTasks,
          entriesByTaskAndDay,
          days,
          weekIndexByDay,
          monthHeaders,
          milestoneBoxes,
          hasAnyLogEntries = allLogEntries.nonEmpty
        )
    }

  private def gridPlacement(colStart: Int, colEnd: Int, rowStart: Int, rowEnd: Int): String =
    s"grid-column: $colStart / $colEnd; grid-row: $rowStart / $rowEnd;"

  private def gridCellPlacement(col: Int, row: Int): String =
    gridPlacement(col, col + 1, row, row + 1)

  private def milestoneBoxElement(box: MilestoneBox) =
    div(
      cls("bg-gray-600/5 border border-gray-600/40 rounded-sm pointer-events-none"),
      styleAttr := gridPlacement(box.colStart, box.colEnd, box.rowStart, box.rowEnd)
    )

  private def milestoneNameCell(box: MilestoneBox) =
    div(
      cls(
        "flex items-center justify-center text-[10px] font-semibold text-gray-800 whitespace-nowrap overflow-hidden [writing-mode:vertical-rl]"
      ),
      styleAttr := gridPlacement(2, 3, box.rowStart, box.rowEnd),
      box.name
    )

  private def monthHeaderCell(month: MonthHeader) = {
    val colorCls =
      if (month.monthNumber % 2 == 1) " border-black-600 border-x-8 border-y-2 text-black" else " border-black-600 border-y-2 border-x-8 text-black"
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
      latest match {
        case Some(e) =>
          div(
            title(e.status.toString),
            cls("w-5.5 h-5.5 rounded-md"),
            styleAttr := s"background-color: ${e.status.color};"
          )
        case None =>
          div(cls("w-5.5 h-5.5 rounded-md bg-gray-200"))
      }
    )
  }

  private def taskNameCell(task: FindTaskRespFull, rowIndex: Int) =
    div(
      cls("cursor-pointer text-fg-brand hover:underline truncate pr-2"),
      styleAttr := gridCellPlacement(1, rowIndex),
      task.name,
      onClick.mapToUnit --> (_ => router.route(EditTaskPage(projectId, task.id)))
    )

  private def renderGrid(layout: HistoryLayout) =
    div(
      cls("overflow-x-auto"),
      div(
        styleAttr := s"display: grid; grid-template-columns: 12rem 2rem repeat(${layout.days.size}, 2rem); grid-template-rows: 1.75rem 3.25rem; grid-auto-rows: 2.25rem;",
        layout.milestoneBoxes.map(milestoneBoxElement),
        layout.milestoneBoxes.map(milestoneNameCell),
        div(styleAttr := gridPlacement(1, 2, 1, 3)),
        div(styleAttr := gridPlacement(2, 3, 1, 3)),
        layout.monthHeaders.map(monthHeaderCell),
        layout.days.zipWithIndex.map { case (day, idx) =>
          dayHeaderCell(day, idx + 3, layout.weekIndexByDay.getOrElse(day, 0))
        },
        layout.orderedTasks.zipWithIndex.map { case (task, taskIdx) =>
          val rowIndex = taskIdx + 3
          taskNameCell(task, rowIndex) +:
            layout.days.zipWithIndex.map { case (day, idx) =>
              dayCell(layout.entriesByTaskAndDay.getOrElse((task.id, day), Seq.empty), idx + 3, rowIndex)
            }
        }
      )
    )

  val element = {
    div(
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
              dateFilterComponent.element,
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
