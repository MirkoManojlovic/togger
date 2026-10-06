package com.togger.ui.task.bulk

import com.raquo.laminar.api.L._
import com.togger.common.WorkDayCalendar
import com.togger.dto.FindTaskStatusResp
import com.togger.task.{DefaultTaskStatus, TaskPriority}
import com.togger.ui.auth.TgInput.tgInputStyle
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
  case object WorkDays extends BulkEditKind
}

/** @param scope
  *   the task the popup edits, or none for the selected tasks
  */
case class BulkPopupTarget(scope: Option[UUID], kind: BulkEditKind)

/** The popups that edit one attribute: of the selected tasks when opened from the toolbar, of a single task when opened
  * from one of its badges. Only one is open at a time, wherever it was opened from.
  *
  * A popup opens at a fixed position next to its trigger rather than inside it, since a trigger sits in a scrolling
  * list that would otherwise cut the popup off.
  */
class BulkEditPopups(context: Option[UUID] => BulkEditContext, onEdit: (Option[UUID], BulkEdit) => Unit) {

  private val openVar = Var(Option.empty[BulkPopupTarget])
  private val tgDateFormater = new TgDateFormater

  /** Read by the modal so Escape closes the popup first and the modal only when no popup is open. */
  def isPopupOpen: Boolean = openVar.now().isDefined

  private def close(): Unit = openVar.set(None)

  /** The trigger, which toggles the popup, with the popup itself while it is open. */
  def anchor(scope: Option[UUID], kind: BulkEditKind, trigger: HtmlElement): HtmlElement = {
    val target = BulkPopupTarget(scope, kind)
    val isOpen = openVar.signal.map(_.contains(target)).distinct
    div(
      cls("relative"),
      trigger.amend(
        cls("ring-2 ring-blue-500") <-- isOpen,
        // stopPropagation: the popup's outside-click listener is registered while this click is still bubbling
        onClick.stopPropagation.mapToUnit --> (_ =>
          openVar.update(open => if (open.contains(target)) None else Some(target))
        )
      ),
      child.maybe <-- isOpen.map(open => Option.when(open)(floating(trigger, target)))
    )
  }

  private val closeOnOutsideClickOrEscape: Seq[Modifier[HtmlElement]] = Seq(
    onMountBind { ctx =>
      val rootNode = ctx.thisNode.ref
      documentEvents(_.onClick)
        .filter(event => !rootNode.contains(event.target.asInstanceOf[dom.Node]))
        .mapToUnit --> (_ => close())
    },
    onMountBind(_ => documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> (_ => close()))
  )

  private def isDate(kind: BulkEditKind): Boolean = kind == BulkEditKind.StartDate || kind == BulkEditKind.DueDate

  private def floating(trigger: HtmlElement, target: BulkPopupTarget): HtmlElement = {
    val rect = trigger.ref.getBoundingClientRect()
    val popupWidth = if (isDate(target.kind)) 640.0 else 300.0
    val popupHeight = 380.0
    val leftPx = rect.left.min(dom.window.innerWidth - popupWidth - 8).max(8)
    val topPx = rect.bottom.min(dom.window.innerHeight - popupHeight).max(8)
    div(
      cls("fixed z-[110]"),
      left(s"${leftPx}px"),
      top(s"${topPx}px"),
      closeOnOutsideClickOrEscape,
      // the popup sits inside a task card, whose own click would otherwise select the task
      onClick.stopPropagation --> Observer.empty,
      popup(target)
    )
  }

  private def popup(target: BulkPopupTarget): HtmlElement = {
    val current = context(target.scope)
    def choose(edit: BulkEdit): Unit = {
      onEdit(target.scope, edit)
      close()
    }
    target.kind match {
      case BulkEditKind.Priority  => priorityPopup(current, choose)
      case BulkEditKind.Status    => statusPopup(current, choose)
      case BulkEditKind.Milestone => milestonePopup(current, choose)
      case BulkEditKind.Assignee  => assigneePopup(current, choose)
      case BulkEditKind.Labels    => labelsPopup(current, target.scope.isDefined, choose)
      case BulkEditKind.StartDate => datePopup(current, isStart = true, choose)
      case BulkEditKind.DueDate   => datePopup(current, isStart = false, choose)
      case BulkEditKind.WorkDays  => workDaysPopup(current, choose)
    }
  }

  /** The value every task the popup edits has, if they all have the same, to mark it as the current choice. */
  private def shared[A](current: BulkEditContext)(attribute: BulkTask => A): Option[A] =
    current.selectedTasks.map(attribute).distinct match {
      case Seq(only) => Some(only)
      case _         => None
    }

  private def optionsPopup(options: Seq[QuickOption], onChoose: QuickOption => Unit): HtmlElement =
    QuickOptionsPopup(options = options, onChoose = onChoose, widthCls = "w-64").element

  private def optionalId(option: QuickOption): Option[UUID] =
    Option.when(option.id.nonEmpty)(UUID.fromString(option.id))

  private def priorityPopup(current: BulkEditContext, choose: BulkEdit => Unit): HtmlElement =
    optionsPopup(
      TaskPriority.values.map(priority =>
        QuickOption(
          priority.value.toString,
          s"${priority.shortCode.toUpperCase} - ${priority.priorityName}",
          priority.description,
          shared(current)(_.priority).contains(priority),
          Some(priority.badgeColor)
        )
      ),
      option => choose(BulkEdit.SetPriority(TaskPriority.withValue(option.id.toInt)))
    )

  private def statusOrder(status: FindTaskStatusResp): Int =
    DefaultTaskStatus.values.find(_.statusName == status.name).map(_.i).getOrElse(Int.MaxValue)

  private def statusPopup(current: BulkEditContext, choose: BulkEdit => Unit): HtmlElement =
    optionsPopup(
      current.lookups.statuses
        .sortBy(statusOrder)
        .map(status =>
          QuickOption(
            status.id.toString,
            status.name,
            status.description,
            shared(current)(_.statusId).contains(Some(status.id)),
            Some(status.badgeColor)
          )
        ),
      option => choose(BulkEdit.SetStatus(UUID.fromString(option.id)))
    )

  private def milestonePopup(current: BulkEditContext, choose: BulkEdit => Unit): HtmlElement =
    optionsPopup(
      QuickOption("", "No milestone", "", shared(current)(_.milestoneId).contains(None)) +:
        current.lookups.milestones
          .sortBy(_.name)
          .map(milestone =>
            QuickOption(
              milestone.id.toString,
              milestone.name,
              milestone.status.statusName,
              shared(current)(_.milestoneId).contains(Some(milestone.id))
            )
          ),
      option => choose(BulkEdit.SetMilestone(optionalId(option)))
    )

  private def assigneePopup(current: BulkEditContext, choose: BulkEdit => Unit): HtmlElement =
    optionsPopup(
      QuickOption("", "Unassigned", "", shared(current)(_.assigneeId).contains(None)) +:
        current.lookups.members
          .sortBy(_.fullName)
          .map(member =>
            QuickOption(
              member.id.toString,
              member.fullName,
              member.email,
              shared(current)(_.assigneeId).contains(Some(member.id))
            )
          ),
      option => choose(BulkEdit.SetAssignee(optionalId(option)))
    )

  /** For the selected tasks the labels are added, removed or replaced; a single task starts from its own labels and
    * just takes the ticked ones.
    */
  private def labelsPopup(current: BulkEditContext, forOneTask: Boolean, choose: BulkEdit => Unit): HtmlElement = {
    val chosen = Var(
      if (forOneTask) current.selectedTasks.headOption.fold(Set.empty[UUID])(_.labelIds) else Set.empty[UUID]
    )
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
      cls("absolute z-10 w-64 bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg p-2"),
      ul(
        cls("max-h-60 overflow-y-auto"),
        if (current.lookups.labels.isEmpty) li(cls("p-2 text-sm text-body"), "This project has no labels")
        else
          current.lookups.labels.sortBy(_.name).map { projectLabel =>
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
        if (forOneTask) Seq(applyButton("Apply", BulkEdit.SetLabels, needsLabels = false))
        else
          Seq(
            applyButton("Add", BulkEdit.AddLabels, needsLabels = true),
            applyButton("Remove", BulkEdit.RemoveLabels, needsLabels = true),
            applyButton("Replace", BulkEdit.SetLabels, needsLabels = false)
          )
      )
    )
  }

  private def datePopup(current: BulkEditContext, isStart: Boolean, choose: BulkEdit => Unit): HtmlElement = {
    val starts = current.selectedTasks.map(_.startDate)
    val selectedDate = if (isStart) shared(current)(_.startDate) else shared(current)(_.dueDate)
    div(
      cls("quick-date-picker"),
      CalendarPickerPopup(
        initialMonth = selectedDate.orElse(starts.minOption).getOrElse(current.today),
        today = current.today,
        tgDateFormater = tgDateFormater,
        selected = selectedDate,
        // a due date before any selected task's start would be refused for that task, so it is not offered
        minDate = if (isStart) None else starts.maxOption,
        isDisabled = day => !current.lookups.workDays.contains(day.getDayOfWeek),
        onPick = day => choose(if (isStart) BulkEdit.SetStartDate(day) else BulkEdit.SetDueDate(day)),
        onClose = () => close()
      ).element
    )
  }

  /** How many work days a task takes, which is the same as moving its due date: from the start, so many work days on.
    */
  private def workDaysPopup(current: BulkEditContext, choose: BulkEdit => Unit): HtmlElement = {
    val count = Var(
      current.selectedTasks.headOption
        .fold(1)(task => WorkDayCalendar.countWorkDays(task.startDate, task.dueDate, current.lookups.workDays))
        .max(1)
    )
    def applyCount(): Unit = choose(BulkEdit.SetWorkDays(count.now()))

    div(
      cls("absolute z-10 w-56 bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg p-3"),
      label(cls("block mb-1.5 text-sm font-medium text-heading"), "Days of work"),
      input(
        `type`("number"),
        cls(tgInputStyle),
        cls("text-sm mb-2"),
        aria.label("Days of work"),
        onMountCallback { ctx =>
          ctx.thisNode.ref.focus()
          ctx.thisNode.ref.select()
        },
        controlled(
          value <-- count.signal.map(_.toString),
          onInput.mapToValue --> (raw => raw.toIntOption.foreach(count.set))
        ),
        onKeyDown.filter(_.key == "Enter") --> (event => {
          event.preventDefault()
          applyCount()
        })
      ),
      button(
        `type`("button"),
        cls("px-2.5 py-1 text-xs font-medium rounded-base border border-default-medium text-body"),
        cls("hover:bg-neutral-tertiary-medium cursor-pointer"),
        "Apply",
        onClick.mapToUnit --> (_ => applyCount())
      )
    )
  }
}
