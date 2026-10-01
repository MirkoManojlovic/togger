package com.togger.ui.task.quick

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, TgJsonFailure, TgJsonSuccess}
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.dto.organization.OrganizationProjectResp
import com.togger.task.TaskPriority
import com.togger.toast.ToastMessages
import com.togger.ui.auth.AccountService
import com.togger.ui.auth.TgInput.tgInputStyle
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor, TgBadgeSize, TgBadgeStyles}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.modal.ModalStyles
import com.togger.ui.organization.{OrganizationServiceFe, TgIcons}
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.quick.QuickDropdown._
import com.togger.ui.task.{TaskFeService, TaskLabelFeService, TaskStatusFeService, TaskStatusesState}
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.util.UUID
import scala.scalajs.js

/** A popup opened by clicking a badge, anchored `left` pixels from the badge row's left edge. */
case class BadgePopup(dropdown: QuickDropdown, left: Double)

case class QuickState(
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
  assigneeId: Option[UUID] = None,
  projectId: Option[UUID] = None,
  statusId: Option[UUID] = None,
  priority: Option[TaskPriority] = None,
  labelIds: Set[UUID] = Set.empty
) {

  lazy val parsed: ParsedQuickInput = TaskQuickInputParser.parse(rawInput)

  lazy val sortedStatuses: Seq[FindTaskStatusResp] = TaskStatusesState(statuses).taskStatusesSorted

  def effectiveStatusId: Option[UUID] = statusId.orElse(sortedStatuses.headOption.map(_.id))
  def effectiveAssigneeId: Option[UUID] = assigneeId.orElse(currentAccountId)
  def effectivePriority: TaskPriority = priority.getOrElse(TaskPriority.Medium)

  def withoutToken(token: QuickToken): QuickState =
    copy(
      rawInput = rawInput.patch(token.start, "", token.end - token.start),
      caret = token.start,
      dismissedTokenStart = None,
      highlighted = 0
    )

  def activeToken: Option[QuickToken] =
    TaskQuickInputParser.activeTokenAt(rawInput, caret).filterNot(token => dismissedTokenStart.contains(token.start))
}

case class QuickOption(
  id: String,
  label: String,
  description: String,
  isSelected: Boolean,
  badgeColor: Option[TgBadgeColor] = None
)

case class QuickPopup(token: QuickToken, options: Seq[QuickOption], highlighted: Int, left: Option[Double] = None)

class CreateTaskQuick(
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  projectService: ProjectService,
  organizationServiceFe: OrganizationServiceFe,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  currentProjectId: Option[UUID],
  val toast: Toast,
  onClose: () => Unit
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  private val state = Var(QuickState(currentAccountId = accountService.get.map(_.id), projectId = currentProjectId))

  private def matches(query: String, texts: String*): Boolean =
    texts.exists(_.toLowerCase.contains(query.toLowerCase))

  private def optionsFor(current: QuickState, token: QuickToken): Seq[QuickOption] =
    token.dropdown match {
      case ProjectDropdown =>
        current.projects
          .filter(project => matches(token.query, project.name))
          .sortBy(_.name)
          .map(project =>
            QuickOption(project.id.toString, project.name, project.description, current.projectId.contains(project.id))
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
            QuickOption(member.id.toString, member.fullName, member.email, current.effectiveAssigneeId.contains(member.id))
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
      .map(token => popupAt(current, token, left = None))
      .orElse(current.badgePopup.map { badgePopup =>
        popupAt(current, QuickToken(badgePopup.dropdown, "", -1, -1), Some(badgePopup.left))
      })

  private def isBadgeToken(token: QuickToken): Boolean = token.start < 0

  private def toggleBadgePopup(dropdown: QuickDropdown, left: Double): Unit = {
    state.update(current =>
      current.copy(
        badgePopup = Option.when(!current.badgePopup.exists(_.dropdown == dropdown))(BadgePopup(dropdown, left)),
        highlighted = 0
      )
    )
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
      _.copy(statuses = Seq.empty, labels = Seq.empty, members = Seq.empty, statusId = None, labelIds = Set.empty)
    )
    projectId.foreach { id =>
      val statusesCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskStatusResp]]]] =
        taskStatusFeService.findByProjectId(id)
      val labelsCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskLabelResp]]]] =
        taskLabelFeService.findByProjectId(id)
      val membersCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[SearchAccountDto]]]] =
        projectService.findAllMembers(id)
      (statusesCall, labelsCall, membersCall)
        .parMapN { (statusesResp, labelsResp, membersResp) =>
          if (state.now().projectId.contains(id)) {
            val statuses = statusesResp.toOption.flatMap(_.body).getOrElse(Seq.empty)
            val labels = labelsResp.toOption.flatMap(_.body).getOrElse(Seq.empty)
            val members = membersResp.toOption.flatMap(_.body).getOrElse(Seq.empty)
            state.update(_.copy(statuses = statuses, labels = labels, members = members))
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
    if (event.key == "Escape") onClose()
    else if (event.key == "Tab" && event.shiftKey && isDescriptionTextArea(event.target)) {
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
    color: Option[TgBadgeColor] = None
  ): HtmlElement =
    span(
      cls(
        color.fold(uncoloredBadgeCls)(
          TgBadgeStyles.container(_, TgBadgeSize.Default, bordered = true, pill = false, action = None, "gap-1")
        )
      ),
      cls("cursor-pointer"),
      // keeps the name input focused, so its blur does not close the popup this click is about to toggle
      onMouseDown.preventDefault --> Observer.empty,
      onClick --> (event =>
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

  private def badges: Signal[Seq[HtmlElement]] =
    state.signal.map { current =>
      val project = current.projects.find(p => current.projectId.contains(p.id)).map(_.name).getOrElse("No project")
      val priorityClear = current.priority.map(_ => () => state.update(_.copy(priority = None)))
      val assigneeClear = current.assigneeId.map(_ => () => state.update(_.copy(assigneeId = None)))
      val assignee = current.members.find(member => current.effectiveAssigneeId.contains(member.id))
      val statusDto = current.sortedStatuses.find(s => current.effectiveStatusId.contains(s.id))
      Seq(
        Some(badge(ProjectDropdown, project, current.projectId.map(_ => () => state.update(_.copy(projectId = None))))),
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
        ),
        assignee.map(member => badge(AssignedToDropdown, member.fullName, assigneeClear))
      ).flatten ++ labelBadges(current)
    }

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

  private def popupRow(popup: QuickPopup, option: QuickOption, index: Int): HtmlElement = {
    val isHighlighted: Signal[Boolean] =
      state.signal.map(popupFor).map(_.exists(_.highlighted == index)).distinct
    li(
      button(
        `type`("button"),
        cls(
          "flex flex-col items-start w-full p-2 text-sm font-medium text-heading hover:bg-neutral-tertiary-medium rounded-md cursor-pointer"
        ),
        cls("bg-neutral-tertiary-medium") <-- isHighlighted,
        inContext(row =>
          isHighlighted.changes.filter(highlighted => highlighted) --> (_ =>
            row.ref.asInstanceOf[js.Dynamic].scrollIntoView(js.Dynamic.literal(block = "nearest"))
          )
        ),
        onMouseDown.preventDefault --> Observer.empty,
        onClick.mapToUnit --> (_ => choose(popup.token, option)),
        div(
          cls("inline-flex items-center w-full gap-1.5"),
          div(cls("w-4 h-4 shrink-0"), Option.when(option.isSelected)(TgIcons.checkIcon("w-4 h-4"))),
          option.badgeColor.fold[HtmlElement](span(option.label))(color =>
            TgBadge(text = option.label, color = color, bordered = true).element
          )
        ),
        Option.when(option.description.nonEmpty)(
          p(cls("w-full text-left text-xs font-normal text-body mt-0.5 line-clamp-1"), option.description)
        )
      )
    )
  }

  private def popupElement(popup: QuickPopup): HtmlElement =
    div(
      cls("absolute z-10 mt-1 bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg"),
      popup.left.fold[Modifier[HtmlElement]](cls("w-full"))(left => Seq(cls("w-72 max-w-full"), styleProp("left") := s"${left}px")),
      ul(
        cls("p-2 text-sm text-body font-medium max-h-72 overflow-y-auto"),
        if (popup.options.isEmpty) Seq(li(cls("p-2 text-sm text-body"), "No matches"))
        else popup.options.zipWithIndex.map { case (option, index) => popupRow(popup, option, index) }
      )
    )

  /** Rebuilt only when the token or options change, not on every highlight move, so the list keeps its scroll. */
  private def popupSlot(belongsHere: QuickPopup => Boolean): Signal[Option[HtmlElement]] =
    state.signal
      .map(popupFor)
      .map(_.filter(belongsHere).map(_.copy(highlighted = 0)))
      .distinct
      .map(_.map(popupElement))

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
      onBlur.mapToUnit --> (_ =>
        state.update(current =>
          current.copy(dismissedTokenStart = current.activeToken.map(_.start), badgePopup = None)
        )
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
    minHeightRem = 10,
    minWidthRem = 30
  )

  val element: HtmlElement =
    div(
      cls("fixed inset-0 z-[100] flex items-start justify-center pt-24 px-4 bg-white/90"),
      onKeyDown --> (event => onWrapperKeyDown(event)),
      onClick.filter(event => event.target == event.currentTarget).mapToUnit --> (_ => onClose()),
      onMountCallback(_ => loadProjects()),
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
          p(cls("mt-1.5 text-xs text-gray-500"), "~project  #label  !priority  $status  %assignee")
        ),
        div(
          cls("relative mb-4"),
          div(cls("flex flex-wrap gap-1.5"), children <-- badges),
          child.maybe <-- popupSlot(_.left.isDefined)
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
