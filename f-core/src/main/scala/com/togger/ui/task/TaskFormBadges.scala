package com.togger.ui.task

import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, WorkDayCalendar}
import com.togger.dto.{FindMilestoneResp, FindTaskLabelResp, FindTaskStatusResp, SearchAccountDto}
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.task.bulk._

import java.time.DayOfWeek

/** The attributes of the task a form edits, as badges that open the same popups the task list uses; the form reads the
  * edited task from [[taskVar]] when it is submitted.
  */
class TaskFormBadges(initial: BulkTask, tgClock: TgClock) {

  val taskVar: Var[BulkTask] = Var(initial)
  private val lookupsVar = Var(BulkLookups(Seq.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty))
  private val tgDateFormater = new TgDateFormater

  def setStatuses(statuses: Seq[FindTaskStatusResp]): Unit = lookupsVar.update(_.copy(statuses = statuses))
  def setLabels(labels: Seq[FindTaskLabelResp]): Unit = lookupsVar.update(_.copy(labels = labels))
  def setMembers(members: Seq[SearchAccountDto]): Unit = lookupsVar.update(_.copy(members = members))
  def setMilestones(milestones: Seq[FindMilestoneResp]): Unit = lookupsVar.update(_.copy(milestones = milestones))
  def setWorkDays(workDays: Set[DayOfWeek]): Unit = lookupsVar.update(_.copy(workDays = workDays))

  def lookupsNow(): BulkLookups = lookupsVar.now()

  private val popups = new BulkEditPopups(
    context = _ => BulkEditContext(lookupsVar.now(), Seq(taskVar.now()), tgClock.currentDate()),
    onEdit = (_, edit) =>
      taskVar.update(task => BulkTaskEditor(Seq(task), Set(task.key), edit, lookupsVar.now().workDays).head)
  )

  private def primaryRow(task: BulkTask, lookups: BulkLookups): Seq[BadgeSpec] = {
    val milestone = task.milestoneId
      .flatMap(id => lookups.milestones.find(_.id == id))
      .map(found => BadgeSpec(found.name, TgBadgeColor.Alternative, Some(BulkEditKind.Milestone)))
      .getOrElse(BadgeSpec(BulkTaskBadges.EmptyMilestone, TgBadgeColor.Gray, Some(BulkEditKind.Milestone)))
    val status = task.statusId
      .flatMap(id => lookups.statuses.find(_.id == id))
      .map(found => BadgeSpec(found.name, found.badgeColor, Some(BulkEditKind.Status)))
      .getOrElse(BadgeSpec(EmojiPrefix.Fallback, TgBadgeColor.Gray, Some(BulkEditKind.Status)))
    val assignee = task.assigneeId
      .flatMap(id => lookups.members.find(_.id == id))
      .map(found => BadgeSpec(found.fullName, TgBadgeColor.Gray, Some(BulkEditKind.Assignee)))
      .getOrElse(BadgeSpec(BulkTaskBadges.EmptyAssignee, TgBadgeColor.Gray, Some(BulkEditKind.Assignee)))
    val labels = lookups.labels.filter(found => task.labelIds.contains(found.id)).sortBy(_.name) match {
      case Seq() => Seq(BadgeSpec(BulkTaskBadges.EmptyLabels, TgBadgeColor.Gray, Some(BulkEditKind.Labels)))
      case found => found.map(label => BadgeSpec(label.name, label.badgeColor, Some(BulkEditKind.Labels)))
    }
    Seq(
      milestone,
      BadgeSpec(task.priority.shortCode, task.priority.badgeColor, Some(BulkEditKind.Priority)),
      status,
      assignee
    ) ++ labels
  }

  private def dateRow(task: BulkTask, lookups: BulkLookups): Seq[BadgeSpec] =
    Seq(
      BadgeSpec(
        tgDateFormater.asAmericanDateWithDayAbrv(task.startDate),
        TgBadgeColor.Gray,
        Some(BulkEditKind.StartDate)
      ),
      BadgeSpec(tgDateFormater.asAmericanDateWithDayAbrv(task.dueDate), TgBadgeColor.Gray, Some(BulkEditKind.DueDate)),
      daysLeft(task, lookups)
    )

  private def daysLeft(task: BulkTask, lookups: BulkLookups): BadgeSpec = {
    val today = tgClock.currentDate()
    if (task.dueDate.isBefore(today)) BadgeSpec("Overdue", TgBadgeColor.Red)
    else {
      val left = WorkDayCalendar.countWorkDays(today, task.dueDate, lookups.workDays)
      BadgeSpec(s"$left days left", TgBadgeColor.Gray)
    }
  }

  private def row(specs: Seq[BadgeSpec]): HtmlElement =
    div(cls("flex flex-wrap items-center gap-1.5"), BulkTaskCard.row(initial.key, specs, popups))

  val element: HtmlElement =
    div(
      cls("flex flex-col gap-1.5"),
      children <-- taskVar.signal.combineWith(lookupsVar.signal).map { case (task, lookups) =>
        Seq(row(primaryRow(task, lookups)), row(dateRow(task, lookups)))
      }
    )
}
