package com.togger.ui.task.quick

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.{DateRangeValidation, TgClock, TgJsonFailure, TgJsonSuccess}
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.dto.organization.OrganizationProjectResp
import com.togger.milestone.MilestoneStatus
import com.togger.task.TaskPriority
import com.togger.toast.ToastMessages
import com.togger.ui.auth.AccountService
import com.togger.ui.auth.TgInput.tgInputStyle
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.components._
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.modal.ModalStyles
import com.togger.ui.organization.{OrganizationServiceFe, TgIcons}
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.quick.QuickDropdown._
import com.togger.ui.task._
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** A popup opened by clicking a badge, anchored `left` pixels from the badge row's left edge. */
case class BadgePopup(dropdown: QuickDropdown, left: Double)

/** What a date picker is built from. Its own case class so the picker is rebuilt only when one of these changes, not on
  * every keystroke in the name input.
  */
case class DatePickerRequest(dropdown: QuickDropdown, selected: LocalDate, minDate: Option[LocalDate])

case class QuickState(
  today: LocalDate,
  currentAccountId: Option[UUID] = None,
  rawInput: String = "",
  caret: Int = 0,
  dismissedTokenStart: Option[Int] = None,
  badgePopup: Option[BadgePopup] = None,
  highlighted: Int = 0,
  description: String = "",
  projects: Seq[OrganizationProjectResp] = Seq.empty,
  statuses: Seq[FindTaskStatusResp] = Seq.empty,
  labels: Seq[FindTaskLabelResp] = Seq.empty,
  members: Seq[SearchAccountDto] = Seq.empty,
  milestones: Seq[FindMilestoneResp] = Seq.empty,
  milestoneId: Option[UUID] = None,
  assigneeId: Option[UUID] = None,
  projectId: Option[UUID] = None,
  statusId: Option[UUID] = None,
  priority: Option[TaskPriority] = None,
  labelIds: Set[UUID] = Set.empty,
  startDate: Option[LocalDate] = None,
  dueDate: Option[LocalDate] = None
) {

  lazy val parsed: ParsedQuickInput = TaskQuickInputParser.parse(rawInput)

  lazy val sortedStatuses: Seq[FindTaskStatusResp] = TaskStatusesState(statuses).taskStatusesSorted

  def effectiveStatusId: Option[UUID] = statusId.orElse(sortedStatuses.headOption.map(_.id))
  def effectiveAssigneeId: Option[UUID] = assigneeId.orElse(currentAccountId)
  def effectivePriority: TaskPriority = priority.getOrElse(TaskPriority.Medium)

  /** The selected project's configured work days, or a Mon-Fri fallback when no project is selected yet (this
    * module has no access to the backend-only `Project.DefaultWorkDays` constant).
    */
  def effectiveWorkDays: Set[DayOfWeek] =
    projectId
      .flatMap(id => projects.find(_.id == id))
      .map(_.workDays.map(DayOfWeek.of))
      .getOrElse(QuickState.DefaultWorkDays)

  /** Today, or the next work day when today isn't one, since the backend rejects non-work-day dates. */
  def effectiveStartDate: LocalDate = startDate.getOrElse(firstWorkDayFrom(today))

  /** Work days from the start date to the due date, counting both ends. */
  def workDays: Int =
    Iterator
      .iterate(effectiveStartDate)(_.plusDays(1))
      .takeWhile(!_.isAfter(effectiveDueDate))
      .count(day => effectiveWorkDays.contains(day.getDayOfWeek))

  private def firstWorkDayFrom(day: LocalDate): LocalDate =
    Iterator.iterate(day)(_.plusDays(1)).find(d => effectiveWorkDays.contains(d.getDayOfWeek)).get

  /** One week after the start date unless picked; a pick that falls before a later-chosen start date is dropped for the
    * same default, rather than left to fail validation.
    */
  def effectiveDueDate: LocalDate =
    dueDate.filterNot(_.isBefore(effectiveStartDate)).getOrElse(effectiveStartDate.plusWeeks(1))

  def withoutToken(token: QuickToken): QuickState =
    copy(
      rawInput = rawInput.patch(token.start, "", token.end - token.start),
      caret = token.start,
      dismissedTokenStart = None,
      highlighted = 0
    )

  def activeToken: Option[QuickToken] =
    TaskQuickInputParser.activeTokenAt(rawInput, caret).filterNot(token => dismissedTokenStart.contains(token.start))

  /** The date picker a typed `@s`/`@d` or a clicked date badge is asking for, with whether a typed token asked. */
  def datePicker: Option[(DatePickerRequest, Boolean)] = {
    val typed = activeToken.map(_.dropdown).filter(QuickDropdown.dateDropdowns.contains).map(_ -> true)
    typed.orElse(badgePopup.map(_.dropdown).filter(QuickDropdown.dateDropdowns.contains).map(_ -> false)).map {
      case (StartDateDropdown, viaToken) =>
        (DatePickerRequest(StartDateDropdown, effectiveStartDate, minDate = None), viaToken)
      case (dropdown, viaToken) =>
        (DatePickerRequest(dropdown, effectiveDueDate, minDate = Some(effectiveStartDate)), viaToken)
    }
  }
}

object QuickState {
  val DefaultWorkDays: Set[DayOfWeek] =
    Set(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
}

case class QuickPopup(token: QuickToken, options: Seq[QuickOption], highlighted: Int, left: Option[Double] = None)

class CreateTaskQuick(
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  projectService: ProjectService,
  milestoneFeService: MilestoneFeService,
  organizationServiceFe: OrganizationServiceFe,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  currentProjectId: Option[UUID],
  val toast: Toast,
  onClose: () => Unit
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  private val state = Var(
    QuickState(
      today = tgClock.currentDate(),
      currentAccountId = accountService.get.map(_.id),
      projectId = currentProjectId
    )
  )

  /** Milestones still being worked on: a finished or abandoned one is no place for a new task. */
  private val OpenMilestoneStatuses: Set[MilestoneStatus] = Set(MilestoneStatus.InProgress, MilestoneStatus.Paused)

  private val tgDateFormater = new TgDateFormater
  private val dateRangeValidation = new DateRangeValidation

  private def projectLabel(project: OrganizationProjectResp): String = s"${project.icon} ${project.name}".trim

  private def matches(query: String, texts: String*): Boolean =
    texts.exists(_.toLowerCase.contains(query.toLowerCase))

  private def optionsFor(current: QuickState, token: QuickToken): Seq[QuickOption] =
    token.dropdown match {
      case StartDateDropdown | DueDateDropdown => Seq.empty
      case MilestoneDropdown =>
        current.milestones
          .filter(milestone => matches(token.query, milestone.name))
          .sortBy(_.name)
          .map(milestone =>
            QuickOption(
              milestone.id.toString,
              milestone.name,
              milestone.status.statusName,
              current.milestoneId.contains(milestone.id)
            )
          )
      case ProjectDropdown =>
        current.projects
          .filter(project => matches(token.query, project.name))
          .sortBy(_.name)
          .map(project =>
            QuickOption(
              project.id.toString,
              projectLabel(project),
              project.description,
              current.projectId.contains(project.id)
            )
          )
      case LabelsDropdown =>
        current.labels
          .filter(label => matches(token.query, label.name))
          .sortBy(_.name)
          .map(label =>
            QuickOption(label.id.toString, label.name, label.description, current.labelIds.contains(label.id))
          )
      case PriorityDropdown =>
        TaskPriority.values
          .filter(priority => matches(token.query, priority.priorityName, priority.shortCode))
          .map(priority =>
            QuickOption(
              priority.value.toString,
              s"${priority.shortCode.toUpperCase} - ${priority.priorityName}",
              priority.description,
              current.effectivePriority == priority,
              Some(priority.badgeColor)
            )
          )
      case AssignedToDropdown =>
        current.members
          .filter(member => matches(token.query, member.fullName, member.email))
          .sortBy(_.fullName)
          .map(member =>
            QuickOption(
              member.id.toString,
              member.fullName,
              member.email,
              current.effectiveAssigneeId.contains(member.id)
            )
          )
      case TaskStatusDropdown =>
        current.sortedStatuses
          .filter(status => matches(token.query, status.name))
          .map(status =>
            QuickOption(
              status.id.toString,
              status.name,
              status.description,
              current.effectiveStatusId.contains(status.id)
            )
          )
    }

  private def popupAt(current: QuickState, token: QuickToken, left: Option[Double]): QuickPopup = {
    val options = optionsFor(current, token)
    QuickPopup(token, options, current.highlighted.min((options.size - 1).max(0)), left)
  }

  /** A token being typed wins over a popup opened from a badge. A badge popup has no token text to filter or remove,
    * hence the token with an empty query and a negative start.
    */
  private def popupFor(current: QuickState): Option[QuickPopup] =
    current.activeToken
      .filterNot(token => QuickDropdown.dateDropdowns.contains(token.dropdown))
      .map(token => popupAt(current, token, left = None))
      .orElse(
        current.badgePopup.filterNot(popup => QuickDropdown.dateDropdowns.contains(popup.dropdown)).map { badgePopup =>
          popupAt(current, QuickToken(badgePopup.dropdown, "", -1, -1), Some(badgePopup.left))
        }
      )

  private def isBadgeToken(token: QuickToken): Boolean = token.start < 0

  private def toggleBadgePopup(dropdown: QuickDropdown, left: Double): Unit = {
    state.update(current =>
      current.copy(
        badgePopup = Option.when(!current.badgePopup.exists(_.dropdown == dropdown))(BadgePopup(dropdown, left)),
        highlighted = 0
      )
    )
    // a date picker takes focus for its own input as it mounts; pulling it back here would close it again
    if (!QuickDropdown.dateDropdowns.contains(dropdown) || state.now().badgePopup.isEmpty) nameInput.ref.focus()
  }

  private def pickDate(dropdown: QuickDropdown, day: LocalDate): Unit =
    state.update { current =>
      val withoutToken =
        current.activeToken
          .filter(token => QuickDropdown.dateDropdowns.contains(token.dropdown))
          .fold(current)(current.withoutToken)
      val cleared = withoutToken.copy(badgePopup = None)
      if (dropdown == StartDateDropdown) cleared.copy(startDate = Some(day)) else cleared.copy(dueDate = Some(day))
    }

  /** Cancel, Escape, outside click, and (after `pickDate`) OK all end up here. A typed token that is still in the input
    * is dismissed, otherwise the picker would reopen at once.
    */
  private def closeDatePicker(): Unit = {
    state.update { current =>
      current.activeToken
        .filter(token => QuickDropdown.dateDropdowns.contains(token.dropdown))
        .fold(current.copy(badgePopup = None))(token => current.copy(dismissedTokenStart = Some(token.start)))
    }
    nameInput.ref.focus()
  }

  private def choose(token: QuickToken, option: QuickOption): Unit =
    state.update { current =>
      val cleared =
        if (!isBadgeToken(token)) current.withoutToken(token)
        else if (token.dropdown == LabelsDropdown) current
        else current.copy(badgePopup = None, highlighted = 0)
      token.dropdown match {
        case ProjectDropdown    => cleared.copy(projectId = Some(UUID.fromString(option.id)))
        case PriorityDropdown   => cleared.copy(priority = Some(TaskPriority.withValue(option.id.toInt)))
        case TaskStatusDropdown => cleared.copy(statusId = Some(UUID.fromString(option.id)))
        case AssignedToDropdown => cleared.copy(assigneeId = Some(UUID.fromString(option.id)))
        case StartDateDropdown | DueDateDropdown => cleared
        case MilestoneDropdown                   => cleared.copy(milestoneId = Some(UUID.fromString(option.id)))
        case LabelsDropdown =>
          val id = UUID.fromString(option.id)
          cleared.copy(labelIds = if (cleared.labelIds.contains(id)) cleared.labelIds - id else cleared.labelIds + id)
      }
    }

  private def loadProjects(): Unit =
    organizationServiceFe
      .findOrgAndProjectsByAccountId(accountService.get.get.id)
      .map(_.foreach(success => state.update(_.copy(projects = success.body.get.flatMap(_.projects)))))
      .unsafeRunAndForget()

  private def loadProjectData(projectId: Option[UUID]): Unit = {
    state.update(
      _.copy(
        statuses = Seq.empty,
        labels = Seq.empty,
        members = Seq.empty,
        milestones = Seq.empty,
        statusId = None,
        labelIds = Set.empty,
        milestoneId = None
      )
    )
    projectId.foreach { id =>
      val statusesCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskStatusResp]]]] =
        taskStatusFeService.findByProjectId(id)
      val labelsCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskLabelResp]]]] =
        taskLabelFeService.findByProjectId(id)
      val membersCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[SearchAccountDto]]]] =
        projectService.findAllMembers(id)
      val milestonesCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindMilestoneResp]]]] =
        milestoneFeService.findByProjectId(id)
      (statusesCall, labelsCall, membersCall, milestonesCall)
        .parMapN { (statusesResp, labelsResp, membersResp, milestonesResp) =>
          if (state.now().projectId.contains(id)) {
            val statuses = statusesResp.toOption.flatMap(_.body).getOrElse(Seq.empty)
            val labels = labelsResp.toOption.flatMap(_.body).getOrElse(Seq.empty)
            val members = membersResp.toOption.flatMap(_.body).getOrElse(Seq.empty)
            val milestones = milestonesResp.toOption
              .flatMap(_.body)
              .getOrElse(Seq.empty)
              .filter(milestone => OpenMilestoneStatuses.contains(milestone.status))
            state.update(_.copy(statuses = statuses, labels = labels, members = members, milestones = milestones))
          }
        }
        .unsafeRunAndForget()
    }
  }

  private def submit(): Unit = {
    val current = state.now()
    val name = current.parsed.taskName
    (current.projectId, current.effectiveStatusId) match {
      case _ if name.isEmpty => toast.error("Enter a task name")
      case (None, _)         => toast.error("Pick a project with ~")
      case (_, None)         => toast.error("The project has no task statuses")
      case (Some(projectId), Some(statusId)) =>
        val now = tgClock.currentInstant
        val dto = CreateTaskAndLinksReq(
          task = CreateTaskReq(
            projectId = projectId,
            name = name,
            description = current.description,
            assignedTo = current.effectiveAssigneeId,
            createdBy = accountService.get.get.id,
            taskStatusId = statusId,
            createdAt = now,
            updatedAt = now,
            priority = current.effectivePriority,
            milestone = current.milestoneId,
            startDate = current.effectiveStartDate,
            dueDate = current.effectiveDueDate,
            taskLabelIds = current.labelIds.toSeq
          ),
          links = Seq.empty
        )
        spinnerService
          .runWithSpinner(taskService.createTask(dto))
          .flatMap(
            _.fold(
              error => IO(toast.error(error)),
              _ => IO(toast.success("Task created successfully")) >> IO(onClose())
            )
          )
          .unsafeRunAndForget()
    }
  }

  private def caretOf(event: dom.Event): Int =
    event.target.asInstanceOf[dom.html.Input].selectionStart

  private def updateCaret(event: dom.Event): Unit = {
    val caret = caretOf(event)
    state.update(current => if (current.caret == caret) current else current.copy(caret = caret, highlighted = 0))
  }

  private def focusDescription(): Unit =
    Option(descriptionEditor.htmlElement.ref.querySelector("textarea")).foreach {
      case textarea: dom.html.TextArea => textarea.focus()
      case _                           => ()
    }

  private def onInputKeyDown(event: dom.KeyboardEvent): Unit =
    (popupFor(state.now()), event.key) match {
      case (Some(_), "Escape") =>
        event.stopPropagation()
        state.update(current =>
          if (current.activeToken.isDefined) current.copy(dismissedTokenStart = current.activeToken.map(_.start))
          else current.copy(badgePopup = None)
        )
      case (Some(QuickPopup(_, options, _, _)), "ArrowDown" | "ArrowUp") if options.nonEmpty =>
        event.preventDefault()
        val step = if (event.key == "ArrowDown") 1 else -1
        state.update(current =>
          current.copy(highlighted = (popupFor(current).get.highlighted + step + options.size) % options.size)
        )
      case (Some(QuickPopup(token, options, highlighted, _)), "Enter" | "Tab") if options.nonEmpty =>
        event.preventDefault()
        choose(token, options(highlighted))
      case (_, "Tab") if !event.shiftKey =>
        event.preventDefault()
        focusDescription()
      case _ => ()
    }

  private def isDescriptionTextArea(target: dom.EventTarget): Boolean =
    target.isInstanceOf[dom.html.TextArea] && descriptionEditor.htmlElement.ref.contains(target.asInstanceOf[dom.Node])

  private def onWrapperKeyDown(event: dom.KeyboardEvent): Unit =
    if (event.key == "Tab" && event.shiftKey && isDescriptionTextArea(event.target)) {
      event.preventDefault()
      nameInput.ref.focus()
    } else if (event.key == "Enter" && (event.ctrlKey || event.metaKey)) {
      event.preventDefault()
      submit()
    }

  private val uncoloredBadgeCls =
    "inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-base bg-neutral-tertiary text-heading"

  private def badge(
    dropdown: QuickDropdown,
    text: String,
    onClear: Option[() => Unit],
    color: Option[TgBadgeColor] = None,
    isOpen: Boolean = false
  ): HtmlElement =
    span(
      cls(
        color.fold(uncoloredBadgeCls)(
          TgBadgeStyles.container(_, TgBadgeSize.Default, bordered = true, pill = false, action = None, "gap-1")
        )
      ),
      cls("cursor-pointer"),
      cls(if (isOpen) "ring-2 ring-blue-400" else ""),
      // keeps the name input focused, so its blur does not close the popup this click is about to toggle
      onMouseDown.preventDefault --> Observer.empty,
      // stopPropagation: a date picker opened by this click starts listening for outside clicks as it mounts, and
      // would otherwise treat the very click that opened it as one
      onClick.stopPropagation --> (event =>
        toggleBadgePopup(dropdown, event.currentTarget.asInstanceOf[dom.html.Element].offsetLeft)
      ),
      text,
      onClear.map(clear =>
        button(
          `type`("button"),
          cls("text-body hover:text-heading cursor-pointer"),
          aria.label(s"Remove $text"),
          TgIcons.closeIcon("w-3 h-3"),
          onClick.stopPropagation.mapToUnit --> (_ => clear())
        )
      )
    )

  private def primaryBadges: Signal[Seq[HtmlElement]] =
    state.signal.map { current =>
      val project =
        current.projects.find(p => current.projectId.contains(p.id)).fold("No project")(projectLabel)
      val priorityClear = current.priority.map(_ => () => state.update(_.copy(priority = None)))
      val statusDto = current.sortedStatuses.find(s => current.effectiveStatusId.contains(s.id))
      Seq(
        Some(badge(ProjectDropdown, project, current.projectId.map(_ => () => state.update(_.copy(projectId = None))))),
        Some(
          badge(
            MilestoneDropdown,
            current.milestones.find(m => current.milestoneId.contains(m.id)).fold("No milestone")(_.name),
            current.milestoneId.map(_ => () => state.update(_.copy(milestoneId = None)))
          )
        ),
        Some(
          badge(
            PriorityDropdown,
            current.effectivePriority.priorityName,
            priorityClear,
            Some(current.effectivePriority.badgeColor)
          )
        ),
        statusDto.map(dto =>
          badge(
            TaskStatusDropdown,
            dto.name,
            current.statusId.map(_ => () => state.update(_.copy(statusId = None))),
            Some(dto.badgeColor)
          )
        )
      ).flatten
    }

  private def assigneeBadges: Signal[Seq[HtmlElement]] =
    state.signal.map { current =>
      val assigneeClear = current.assigneeId.map(_ => () => state.update(_.copy(assigneeId = None)))
      current.members
        .find(member => current.effectiveAssigneeId.contains(member.id))
        .map(member => badge(AssignedToDropdown, member.fullName, assigneeClear))
        .toSeq
    }

  private def labelRowBadges: Signal[Seq[HtmlElement]] = state.signal.map(labelBadges)

  private def dateBadges: Signal[Seq[HtmlElement]] =
    state.signal.map { current =>
      Seq(
        dateBadge(
          StartDateDropdown,
          "Start",
          current.effectiveStartDate,
          current.startDate.map(_ => () => state.update(_.copy(startDate = None))),
          current
        ),
        dateBadge(
          DueDateDropdown,
          "Due",
          current.effectiveDueDate,
          current.dueDate.map(_ => () => state.update(_.copy(dueDate = None))),
          current
        ),
        TgBadge(
          text = if (current.workDays == 1) "1 day left" else s"${current.workDays} days left",
          color = TgBadgeColor.Gray,
          bordered = true
        ).element
      )
    }

  private def dateBadge(
    dropdown: QuickDropdown,
    prefix: String,
    date: LocalDate,
    onClear: Option[() => Unit],
    current: QuickState
  ): HtmlElement =
    badge(
      dropdown,
      s"$prefix ${tgDateFormater.asAmericanDateWithDayAbrv(date)}",
      onClear,
      Some(TgBadgeColor.Gray),
      isOpen = current.datePicker.exists(_._1.dropdown == dropdown)
    )

  private def labelBadges(current: QuickState): Seq[HtmlElement] = {
    val selected = current.labels.filter(label => current.labelIds.contains(label.id))
    if (selected.isEmpty) Seq(badge(LabelsDropdown, "+ Labels", None))
    else
      selected.map { label =>
        badge(
          LabelsDropdown,
          label.name,
          Some(() => state.update(current => current.copy(labelIds = current.labelIds - label.id))),
          Some(label.badgeColor)
        )
      }
  }

  private def popupElement(popup: QuickPopup): HtmlElement =
    QuickOptionsPopup(
      options = popup.options,
      isHighlighted = index => state.signal.map(popupFor).map(_.exists(_.highlighted == index)).distinct,
      onChoose = option => choose(popup.token, option),
      widthCls = popup.left.fold("w-full")(_ => "w-72 max-w-full"),
      leftPx = popup.left
    ).element

  /** Rebuilt only when the token or options change, not on every highlight move, so the list keeps its scroll. */
  private def popupSlot(belongsHere: QuickPopup => Boolean): Signal[Option[HtmlElement]] =
    state.signal
      .map(popupFor)
      .map(_.filter(belongsHere).map(_.copy(highlighted = 0)))
      .distinct
      .map(_.map(popupElement))

  private def datePicker(request: DatePickerRequest): HtmlElement =
    div(
      cls("quick-date-picker"),
      CalendarPickerPopup(
        initialMonth = request.selected,
        today = state.now().today,
        tgDateFormater = tgDateFormater,
        selected = Some(request.selected),
        minDate = request.minDate,
        isDisabled = day => dateRangeValidation.isWeekend(day, state.now().effectiveWorkDays),
        onPick = day => pickDate(request.dropdown, day),
        onClose = () => closeDatePicker()
      ).element
    )

  /** The picker for a typed `@s`/`@d` goes under the name input, the one for a clicked badge under the badge row. */
  private def datePickerSlot(viaToken: Boolean): Signal[Option[HtmlElement]] =
    state.signal
      .map(_.datePicker.collect { case (request, typed) if typed == viaToken => request })
      .distinct
      .map(_.map(datePicker))

  private def isInsideDatePicker(target: dom.EventTarget): Boolean =
    target match {
      case element: dom.Element => element.closest(".quick-date-picker") != null
      case _                    => false
    }

  /** One row of badges. It is its own positioned box, so the popup of a clicked badge opens right under that row, and
    * `offsetLeft` of a badge is measured from the same box the popup is positioned in.
    */
  private def badgeRow(
    badges: Signal[Seq[HtmlElement]],
    dropdowns: Set[QuickDropdown],
    extra: Modifier[HtmlElement]*
  ): HtmlElement =
    div(
      cls("relative"),
      div(cls("flex flex-wrap gap-1.5"), children <-- badges),
      child.maybe <-- popupSlot(popup => popup.left.isDefined && dropdowns.contains(popup.token.dropdown)),
      extra
    )

  private val nameInput =
    input(
      `type`("text"),
      cls(tgInputStyle),
      placeholder("Task name"),
      autoFocus(true),
      controlled(
        value <-- state.signal.map(_.rawInput),
        onInput.mapToValue --> (raw =>
          state.update(_.copy(rawInput = raw, dismissedTokenStart = None, badgePopup = None, highlighted = 0))
        )
      ),
      onInput --> (event => updateCaret(event)),
      onKeyUp --> (event => updateCaret(event)),
      onClick --> (event => updateCaret(event)),
      onKeyDown --> (event => onInputKeyDown(event)),
      // focus moving into a date picker is the picker opening, not the name input being left
      onBlur.filterNot(event => isInsideDatePicker(event.relatedTarget)).mapToUnit --> (_ =>
        state.update(current => current.copy(dismissedTokenStart = current.activeToken.map(_.start), badgePopup = None))
      ),
      state.signal.map(_.caret).distinct.changes --> (caret =>
        Option(dom.document.activeElement).collect { case el: dom.html.Input => el }.foreach { el =>
          if (el.selectionStart != caret) el.setSelectionRange(caret, caret)
        }
      )
    )

  private val descriptionEditor = state.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Description",
    placeholderValue = "Describe the task",
    minHeightRem = 20,
    minWidthRem = 30
  )

  val element: HtmlElement =
    div(
      cls("fixed inset-0 z-[100] flex items-start justify-center pt-24 px-4 bg-white/90 dark:bg-neutral-primary/90"),
      onKeyDown --> (event => onWrapperKeyDown(event)),
      onClick.filter(event => event.target == event.currentTarget).mapToUnit --> (_ => onClose()),
      onMountCallback { _ =>
        loadProjects()
        nameInput.ref.focus()
      },
      // on the document, not the wrapper, so Escape closes even when focus has dropped to the page body; a popup
      // handling its own Escape stops the event before it gets here
      documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> (_ => onClose()),
      state.signal.map(_.projectId).distinct --> (projectId => loadProjectData(projectId)),
      div(
        cls(ModalStyles.contentBoxCls),
        cls("w-full max-w-2xl"),
        div(
          cls(ModalStyles.headerCls),
          h3(cls(ModalStyles.titleCls), "Quick task"),
          button(
            `type`("button"),
            cls(ModalStyles.closeButtonCls),
            aria.label("Close"),
            TgIcons.closeIcon("w-4 h-4"),
            onClick.mapToUnit --> (_ => onClose())
          )
        ),
        div(
          cls("relative mb-3"),
          nameInput,
          child.maybe <-- popupSlot(_.left.isEmpty),
          child.maybe <-- datePickerSlot(viaToken = true),
          p(
            cls("mt-1.5 text-xs text-body-subtle"),
            "~project  #label  !priority  $status  %assignee  -milestone  @s start  @d due"
          )
        ),
        div(
          cls("flex flex-col gap-1.5 mb-4"),
          badgeRow(primaryBadges, Set(ProjectDropdown, MilestoneDropdown, PriorityDropdown, TaskStatusDropdown)),
          badgeRow(dateBadges, Set.empty, child.maybe <-- datePickerSlot(viaToken = false)),
          badgeRow(labelRowBadges, Set(LabelsDropdown)),
          badgeRow(assigneeBadges, Set(AssignedToDropdown))
        ),
        descriptionEditor.htmlElement.amend(cls("mb-4")),
        div(
          cls("flex justify-end gap-2"),
          button(
            `type`("button"),
            cls(
              "text-body bg-neutral-secondary-medium border border-default-medium hover:bg-neutral-tertiary-medium font-medium rounded-base text-sm px-4 py-2.5 cursor-pointer"
            ),
            "Cancel",
            onClick.mapToUnit --> (_ => onClose())
          ),
          button(
            `type`("button"),
            cls(TgHtmlButton.tailwindStandardButton),
            "Create task",
            onClick.mapToUnit --> (_ => submit())
          )
        )
      )
    )
}
