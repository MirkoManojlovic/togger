package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.FindMilestoneResp
import com.togger.dto.FindMilestoneStepResp
import com.togger.dto.FindTaskLabelResp
import com.togger.dto.FindTaskStatusResp
import com.togger.dto.SearchAccountDto
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.milestone.MilestoneStepLabels
import com.togger.ui.task.bulk._
import com.togger.ui.teamdaily.TeamDailyTaskBadges

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/** The attributes of the task a form edits, as badges that open the same popups the task list uses; the form reads the
  * edited task from [[taskVar]] when it is submitted. Choosing a closing status (Done or Won't do) is handed to
  * [[onClosingChosen]] instead of being applied, as a closed task needs an end date.
  */
class TaskFormBadges(
  initial: BulkTask,
  tgClock: TgClock,
  trailing: Option[HtmlElement] = None,
  onClosingChosen: UUID => Unit = _ => ()
) {

  val taskVar: Var[BulkTask] = Var(initial)
  private val lookupsVar = Var(BulkLookups(Seq.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty))
  private val tgDateFormater = new TgDateFormater

  def setStatuses(statuses: Seq[FindTaskStatusResp]): Unit = lookupsVar.update(_.copy(statuses = statuses))
  def setLabels(labels: Seq[FindTaskLabelResp]): Unit = lookupsVar.update(_.copy(labels = labels))
  def setMembers(members: Seq[SearchAccountDto]): Unit = lookupsVar.update(_.copy(members = members))
  def setMilestones(milestones: Seq[FindMilestoneResp]): Unit = lookupsVar.update(_.copy(milestones = milestones))
  def setSteps(steps: Seq[FindMilestoneStepResp]): Unit = lookupsVar.update(_.copy(steps = steps))
  def setWorkDays(workDays: Set[DayOfWeek]): Unit = lookupsVar.update(_.copy(workDays = workDays))

  def lookupsNow(): BulkLookups = lookupsVar.now()

  val workDaysSignal: Signal[Set[DayOfWeek]] = lookupsVar.signal.map(_.workDays).distinct

  private val popups = new BulkEditPopups(
    context = _ => BulkEditContext(lookupsVar.now(), Seq(taskVar.now()), tgClock.currentDate()),
    onEdit = (_, edit) =>
      closingStatusId(edit).fold(
        taskVar.update(task => BulkTaskEditor(Seq(task), Set(task.key), edit, lookupsVar.now().workDays).head)
      )(onClosingChosen)
  )

  private def closingStatusId(edit: BulkEdit): Option[UUID] =
    edit match {
      case BulkEdit.SetStatus(statusId) =>
        Some(statusId).filter(id => TaskClose.closingStatuses(lookupsVar.now().statuses).exists(_.id == id))
      case _ => None
    }

  private def primaryRow(task: BulkTask, lookups: BulkLookups): Seq[BadgeSpec] = {
    val milestone = task.milestoneStepId
      .flatMap(id => MilestoneStepLabels.milestoneOf(id, lookups.steps, lookups.milestones))
      .map(found => BadgeSpec(found.name, TgBadgeColor.Alternative, Some(BulkEditKind.Milestone)))
      .getOrElse(BadgeSpec(BulkTaskBadges.EmptyMilestone, TgBadgeColor.Gray, Some(BulkEditKind.Milestone)))
    val milestoneStep = task.milestoneStepId
      .flatMap(id => lookups.steps.find(_.id == id))
      .map(found => BadgeSpec(found.name, TgBadgeColor.Alternative, Some(BulkEditKind.MilestoneStep)))
      .getOrElse(BadgeSpec(BulkTaskBadges.EmptyMilestoneStep, TgBadgeColor.Gray, Some(BulkEditKind.MilestoneStep)))
    val status = task.statusId
      .flatMap(id => lookups.statuses.find(_.id == id))
      .map(found => BadgeSpec(found.badgeText, found.badgeColor, Some(BulkEditKind.Status)))
      .getOrElse(BadgeSpec(EmojiPrefix.Fallback, TgBadgeColor.Gray, Some(BulkEditKind.Status)))
    val assignee = task.assigneeId
      .flatMap(id => lookups.members.find(_.id == id))
      .map(found => BadgeSpec(found.fullName, TgBadgeColor.Gray, Some(BulkEditKind.Assignee)))
      .getOrElse(BadgeSpec(BulkTaskBadges.EmptyAssignee, TgBadgeColor.Gray, Some(BulkEditKind.Assignee)))
    val labels = lookups.labels.filter(found => task.labelIds.contains(found.id)).sortBy(_.name) match {
      case Seq() => Seq(BadgeSpec(BulkTaskBadges.EmptyLabels, TgBadgeColor.Gray, Some(BulkEditKind.Labels)))
      case found => found.map(label => BadgeSpec(label.badgeText, label.badgeColor, Some(BulkEditKind.Labels)))
    }
    Seq(
      milestone,
      milestoneStep,
      BadgeSpec(task.priority.shortCode, task.priority.badgeColor, Some(BulkEditKind.Priority)),
      status,
      assignee
    ) ++ labels
  }

  private def dateRow(task: BulkTask, lookups: BulkLookups): Seq[BadgeSpec] =
    Seq(
      BadgeSpec(
        tgDateFormater.asStartDate(task.startDate),
        TgBadgeColor.Gray,
        Some(BulkEditKind.StartDate)
      ),
      BadgeSpec(tgDateFormater.asDueDate(task.dueDate), TgBadgeColor.Gray, Some(BulkEditKind.DueDate))
    ) ++ task.endDate.map(endBadge) ++
      Seq(TeamDailyTaskBadges.duration(task.startDate, task.dueDate, lookups.workDays)) ++
      Option.when(task.endDate.isEmpty)(daysLeft(task, lookups))

  /** The end date is set by closing the task, so its badge has no popup of its own. */
  private def endBadge(date: LocalDate): BadgeSpec =
    BadgeSpec(tgDateFormater.asEndDate(date), TgBadgeColor.Success)

  private def daysLeft(task: BulkTask, lookups: BulkLookups): BadgeSpec =
    TeamDailyTaskBadges.daysLeft(task.dueDate, tgClock.currentDate(), lookups.workDays)

  private def row(specs: Seq[BadgeSpec], extraCls: String = ""): HtmlElement =
    div(cls("flex flex-wrap items-center gap-1.5", extraCls), BulkTaskCard.row(initial.key, specs, popups))

  val element: HtmlElement =
    div(
      cls("flex flex-col gap-1.5"),
      div(
        cls("flex flex-wrap items-center gap-1.5"),
        child <-- taskVar.signal.combineWith(lookupsVar.signal).map { case (task, lookups) =>
          row(primaryRow(task, lookups), "contents")
        },
        trailing.toSeq
      ),
      child <-- taskVar.signal.combineWith(lookupsVar.signal).map { case (task, lookups) =>
        row(dateRow(task, lookups))
      }
    )
}
