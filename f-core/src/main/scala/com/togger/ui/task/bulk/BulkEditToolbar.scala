package com.togger.ui.task.bulk

import com.raquo.laminar.api.L._
import com.togger.dto.FindTaskStatusResp
import com.togger.task.{DefaultTaskStatus, TaskPriority}
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{CalendarPickerPopup, QuickOption, QuickOptionsPopup, TgBadge}
import com.togger.ui.task.TgDateFormater
import org.scalajs.dom

import java.time.LocalDate
import java.util.UUID

/** What a popup is built from when it opens: the project data and the tasks the edit would apply to. */
case class BulkEditContext(lookups: BulkLookups, selectedTasks: Seq[BulkTask], today: LocalDate)

sealed trait BulkEditKind

object BulkEditKind {
  case object Priority extends BulkEditKind
  case object Status extends BulkEditKind
  case object Milestone extends BulkEditKind
  case object Assignee extends BulkEditKind
  case object Labels extends BulkEditKind
  case object StartDate extends BulkEditKind
  case object DueDate extends BulkEditKind
}

/** One button per attribute; each opens a popup under it, and picking something reports a [[BulkEdit]]. */
class BulkEditToolbar(context: () => BulkEditContext, onEdit: BulkEdit => Unit) {

  private val openVar = Var(Option.empty[BulkEditKind])
  private val tgDateFormater = new TgDateFormater

  /** Read by the modal so Escape closes the popup first and the modal only when no popup is open. */
  def isPopupOpen: Boolean = openVar.now().isDefined

  private def close(): Unit = openVar.set(None)

  private val closeOnOutsideClickOrEscape: Seq[Modifier[HtmlElement]] = Seq(
    onMountBind { ctx =>
      val rootNode = ctx.thisNode.ref
      documentEvents(_.onClick)
        .filter(event => !rootNode.contains(event.target.asInstanceOf[dom.Node]))
        .mapToUnit --> (_ => close())
    },
    onMountBind(_ => documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> (_ => close()))
  )

  private def choose(edit: BulkEdit): Unit = {
    onEdit(edit)
    close()
  }

  private def optionsPopup(options: Seq[QuickOption], onChoose: QuickOption => Unit): HtmlElement =
    QuickOptionsPopup(options = options, onChoose = onChoose, widthCls = "w-64").element
      .amend(closeOnOutsideClickOrEscape)

  private def optionalId(option: QuickOption): Option[UUID] =
    Option.when(option.id.nonEmpty)(UUID.fromString(option.id))

  private def priorityPopup(): HtmlElement =
    optionsPopup(
      TaskPriority.values.map(priority =>
        QuickOption(
          priority.value.toString,
          s"${priority.shortCode.toUpperCase} - ${priority.priorityName}",
          priority.description,
          isSelected = false,
          Some(priority.badgeColor)
        )
      ),
      option => choose(BulkEdit.SetPriority(TaskPriority.withValue(option.id.toInt)))
    )

  private def statusOrder(status: FindTaskStatusResp): Int =
    DefaultTaskStatus.values.find(_.statusName == status.name).map(_.i).getOrElse(Int.MaxValue)

  private def statusPopup(): HtmlElement =
    optionsPopup(
      context().lookups.statuses
        .sortBy(statusOrder)
        .map(status =>
          QuickOption(status.id.toString, status.name, status.description, false, Some(status.badgeColor))
        ),
      option => choose(BulkEdit.SetStatus(UUID.fromString(option.id)))
    )

  private def milestonePopup(): HtmlElement =
    optionsPopup(
      QuickOption("", "No milestone", "", isSelected = false) +:
        context().lookups.milestones
          .sortBy(_.name)
          .map(milestone => QuickOption(milestone.id.toString, milestone.name, milestone.status.statusName, false)),
      option => choose(BulkEdit.SetMilestone(optionalId(option)))
    )

  private def assigneePopup(): HtmlElement =
    optionsPopup(
      QuickOption("", "Unassigned", "", isSelected = false) +:
        context().lookups.members
          .sortBy(_.fullName)
          .map(member => QuickOption(member.id.toString, member.fullName, member.email, false)),
      option => choose(BulkEdit.SetAssignee(optionalId(option)))
    )

  private def labelsPopup(): HtmlElement = {
    val chosen = Var(Set.empty[UUID])
    def applyButton(text: String, edit: Set[UUID] => BulkEdit, needsLabels: Boolean): HtmlElement =
      button(
        `type`("button"),
        cls("px-2.5 py-1 text-xs font-medium rounded-base border border-default-medium text-body"),
        cls("hover:bg-neutral-tertiary-medium cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"),
        disabled <-- chosen.signal.map(labels => needsLabels && labels.isEmpty),
        text,
        onClick.mapToUnit --> (_ => choose(edit(chosen.now())))
      )

    div(
      cls("absolute z-10 mt-1 w-64 bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg p-2"),
      closeOnOutsideClickOrEscape,
      ul(
        cls("max-h-60 overflow-y-auto"),
        if (context().lookups.labels.isEmpty) li(cls("p-2 text-sm text-body"), "This project has no labels")
        else
          context().lookups.labels.sortBy(_.name).map { projectLabel =>
            li(
              label(
                cls("flex items-center gap-2 p-2 rounded-md hover:bg-neutral-tertiary-medium cursor-pointer"),
                input(
                  `type`("checkbox"),
                  cls("w-4 h-4 border border-default-strong rounded-xs bg-neutral-secondary-strong"),
                  checked <-- chosen.signal.map(_.contains(projectLabel.id)),
                  onClick.mapToChecked --> (isChecked =>
                    chosen.update(labels => if (isChecked) labels + projectLabel.id else labels - projectLabel.id)
                  )
                ),
                TgBadge(text = projectLabel.name, color = projectLabel.badgeColor, bordered = true).element
              )
            )
          }
      ),
      div(
        cls("flex gap-1.5 pt-2 border-t border-default-medium"),
        applyButton("Add", BulkEdit.AddLabels, needsLabels = true),
        applyButton("Remove", BulkEdit.RemoveLabels, needsLabels = true),
        applyButton("Replace", BulkEdit.SetLabels, needsLabels = false)
      )
    )
  }

  private def datePopup(kind: BulkEditKind): HtmlElement = {
    val current = context()
    val isStart = kind == BulkEditKind.StartDate
    val firstStart = current.selectedTasks.map(_.startDate).minOption
    val latestStart = current.selectedTasks.map(_.startDate).maxOption
    div(
      cls("quick-date-picker"),
      CalendarPickerPopup(
        initialMonth = firstStart.getOrElse(current.today),
        today = current.today,
        tgDateFormater = tgDateFormater,
        // a due date before any selected task's start would be refused for that task, so it is not offered
        minDate = if (isStart) None else latestStart,
        isDisabled = day => !current.lookups.workDays.contains(day.getDayOfWeek),
        onPick = day => onEdit(if (isStart) BulkEdit.SetStartDate(day) else BulkEdit.SetDueDate(day)),
        onClose = () => close()
      ).element
    )
  }

  private def popupFor(kind: BulkEditKind): HtmlElement =
    kind match {
      case BulkEditKind.Priority  => priorityPopup()
      case BulkEditKind.Status    => statusPopup()
      case BulkEditKind.Milestone => milestonePopup()
      case BulkEditKind.Assignee  => assigneePopup()
      case BulkEditKind.Labels    => labelsPopup()
      case BulkEditKind.StartDate => datePopup(BulkEditKind.StartDate)
      case BulkEditKind.DueDate   => datePopup(BulkEditKind.DueDate)
    }

  private def trigger(kind: BulkEditKind, text: String): HtmlElement =
    div(
      cls("relative"),
      button(
        `type`("button"),
        cls("px-2.5 py-1 text-xs font-medium rounded-base border border-default-medium text-body"),
        cls("hover:bg-neutral-tertiary-medium cursor-pointer"),
        cls("bg-neutral-tertiary-medium") <-- openVar.signal.map(_.contains(kind)),
        text,
        // stopPropagation: the popup's outside-click listener is registered while this click is still bubbling
        onClick.stopPropagation.mapToUnit --> (_ =>
          openVar.update(open => if (open.contains(kind)) None else Some(kind))
        )
      ),
      child.maybe <-- openVar.signal.map(_.contains(kind)).distinct.map(isOpen => Option.when(isOpen)(popupFor(kind)))
    )

  val element: HtmlElement =
    div(
      cls("flex flex-wrap items-center gap-1.5"),
      trigger(BulkEditKind.Priority, "Priority"),
      trigger(BulkEditKind.Status, "Status"),
      trigger(BulkEditKind.Milestone, "Milestone"),
      trigger(BulkEditKind.Assignee, "Assignee"),
      trigger(BulkEditKind.Labels, "Labels"),
      trigger(BulkEditKind.StartDate, "Start date"),
      trigger(BulkEditKind.DueDate, "Due date")
    )
}
