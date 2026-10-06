package com.togger.ui.task.bulk

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.ui.auth.TgInput.tgInputStyle
import com.togger.ui.components.{DropdownMeta, TgSelectDropdown, TgSelectDropdownOption}
import com.togger.ui.modal.ModalStyles
import com.togger.ui.organization.TgIcons
import com.togger.ui.auth.AccountService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.task.{TaskFeService, TaskProjectData, TaskProjectDataLoader}
import com.togger.ui.toast.Toast
import org.scalajs.dom

import java.util.UUID
import scala.concurrent.duration.DurationInt

/** Two panels: plain text on the left, the tasks it parses to on the right. */
class BulkAddTasks(
  taskService: TaskFeService,
  projectDataLoader: TaskProjectDataLoader,
  spinnerService: SpinnerService,
  accountService: AccountService,
  tgClock: TgClock,
  currentProjectId: Option[UUID],
  localStorage: LocalStorage,
  toast: Toast,
  onClose: () => Unit
)(implicit ioRuntime: IORuntime) {

  private val ParseDebounce = 150.millis
  private val DraftSaveDebounce = 300.millis

  /** The unfinished bulk add of this account, saved as it changes so it survives a refresh or closing the modal. */
  private val draftStore = accountService.get.map(account => new BulkDraftStore(localStorage, account.id))

  private val restoredDraft = draftStore.flatMap(_.load())

  private val state = Var(
    restoredDraft.fold(BulkState(today = tgClock.currentDate(), projectId = currentProjectId)) { draft =>
      BulkState(
        today = tgClock.currentDate(),
        projectId = draft.projectId.orElse(currentProjectId),
        rawInput = draft.rawInput,
        tasks = draft.tasks,
        awaitingRestoredData = true
      )
    }
  )

  /** Off once the draft is discarded or the tasks are created, so a save still waiting can't bring the draft back. */
  private val persisting = Var(true)

  private def saveDraft(): Unit =
    if (persisting.now()) draftStore.foreach(_.save(state.now().draft))

  private def closeKeepingDraft(): Unit = {
    saveDraft()
    onClose()
  }

  private def closeDiscardingDraft(): Unit = {
    persisting.set(false)
    draftStore.foreach(_.clear())
    onClose()
  }

  /** Parsed after a short pause in typing, so a long list isn't re-parsed and re-rendered on every keystroke. */
  private val parsedSignal: Signal[ParsedBulkInput] =
    state.signal.map(_.rawInput).composeChanges(_.debounce(ParseDebounce.toMillis.toInt)).map(TaskBulkInputParser.parse)

  private sealed trait FooterMode
  private case object Normal extends FooterMode
  private case object ConfirmClose extends FooterMode
  private case object ConfirmClear extends FooterMode

  /** Asked before the modal closes with text in it, or before everything is cleared, so a stray click can't lose a long
    * list.
    */
  private val footerMode = Var[FooterMode](Normal)

  private val selected = Var(Set.empty[UUID])

  /** The last task whose checkbox was clicked, where a shift click extends the selection from. */
  private val selectionAnchor = Var(Option.empty[UUID])

  private val dragKey = Var(Option.empty[UUID])

  /** The gap a drop would land in, counted before each task: 0 is above the first, the list size below the last. */
  private val dropInsertion = Var(Option.empty[Int])

  private def reconciled(current: BulkState, parsed: ParsedBulkInput): Seq[BulkTask] =
    BulkTaskList.reconcile(current.tasks, parsed.lines.map(_.name), current.defaults, () => UUID.randomUUID())

  /** Reorders the tasks and writes the new order back into the text, which stays the source of the names. */
  private def reorder(change: Seq[BulkTask] => Seq[BulkTask]): Unit =
    state.update { current =>
      val inSync = reconciled(current, TaskBulkInputParser.parse(current.rawInput))
      val moved = change(inSync)
      current.copy(tasks = moved, rawInput = TaskBulkInputParser.applyOrder(current.rawInput, moved.map(_.name)))
    }

  private val popups = new BulkEditPopups(
    context = scope => {
      val current = state.now()
      val edited = scope.fold(selected.now())(Set(_))
      BulkEditContext(current.lookups, current.tasks.filter(task => edited.contains(task.key)), current.today)
    },
    onEdit = (scope, edit) =>
      state.update { current =>
        val edited = scope.fold(selected.now())(Set(_))
        current.copy(tasks = BulkTaskEditor(current.tasks, edited, edit, current.workDays))
      }
  )

  private val editToolbar = new BulkEditToolbar(popups)

  private def onSelectClick(key: UUID, shift: Boolean): Unit = {
    val (updated, anchor) =
      BulkSelection.click(selected.now(), state.now().tasks.map(_.key), selectionAnchor.now(), key, shift)
    selected.set(updated)
    selectionAnchor.set(anchor)
  }

  private val checkboxCls = "w-4 h-4 border border-default-strong rounded-xs bg-neutral-secondary-strong cursor-pointer"

  private def selectCheckbox(key: UUID): HtmlElement =
    input(
      `type`("checkbox"),
      cls(checkboxCls),
      cls("mt-0.5 shrink-0"),
      aria.label("Select task"),
      checked <-- selected.signal.map(_.contains(key)),
      onClick --> (event => onSelectClick(key, event.shiftKey))
    )

  /** A click on the card selects it, like its checkbox; the checkbox and the reorder buttons keep their own click. */
  private def selectOnClick(key: UUID): Seq[Modifier[HtmlElement]] =
    Seq(
      cls("cursor-pointer"),
      onClick.filter(event => !isInteractive(event.target)) --> (event => onSelectClick(key, event.shiftKey))
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
      checked <-- selected.signal.combineWith(state.signal.map(_.tasks.map(_.key))).map { case (chosen, keys) =>
        keys.nonEmpty && keys.forall(chosen.contains)
      },
      onClick.mapToUnit --> (_ => selected.update(BulkSelection.toggleAll(_, state.now().tasks.map(_.key))))
    )

  private val selectionBar: HtmlElement =
    div(
      cls("mb-2 flex flex-col gap-2"),
      div(
        cls("flex items-center gap-2 text-sm text-body"),
        selectAllCheckbox,
        span(
          child.text <-- selected.signal.map(chosen => if (chosen.isEmpty) "Select all" else s"${chosen.size} selected")
        )
      ),
      editToolbar.element.amend(cls("hidden") <-- selected.signal.map(_.isEmpty))
    )

  private def requestClose(): Unit =
    if (state.now().draft.isEmpty) closeDiscardingDraft() else footerMode.set(ConfirmClose)

  /** Removes the text and all the tasks made from it, and the saved draft with them. */
  private def clearAll(): Unit = {
    state.update(_.copy(rawInput = "", tasks = Seq.empty))
    selected.set(Set.empty)
    selectionAnchor.set(None)
    draftStore.foreach(_.clear())
    footerMode.set(Normal)
  }

  private val submitBlocker: Signal[Option[String]] =
    state.signal.combineWith(parsedSignal.map(_.hasIssues)).map { case (current, hasIssues) =>
      BulkSubmit.blocker(current.projectId, current.tasks, hasIssues)
    }

  /** Creates the tasks in one request. If it fails nothing was created, so the modal stays open, text intact. */
  private def submit(): Unit = {
    val parsed = TaskBulkInputParser.parse(state.now().rawInput)
    val current = state.now()
    val inSync = reconciled(current, parsed)
    val createdBy = accountService.get.get.id
    BulkSubmit.request(current.projectId, inSync, parsed.hasIssues, createdBy, tgClock.currentInstant) match {
      case Left(reason) => toast.error(reason)
      case Right(request) =>
        spinnerService
          .runWithSpinner(taskService.createTasks(request))
          .flatMap(
            _.fold(
              error => IO(toast.error(error)),
              created =>
                IO(toast.success(s"${created.body.fold(0)(_.size)} tasks created")) >> IO(closeDiscardingDraft())
            )
          )
          .unsafeRunAndForget()
    }
  }

  // the shared standard button is full width, which squeezes its neighbours until their text wraps, so the footer
  // has small content-sized buttons of its own
  private val secondaryButtonCls =
    "text-body bg-neutral-secondary-medium border border-default-medium hover:bg-neutral-tertiary-medium " +
      "font-medium leading-5 rounded-base text-xs px-3 py-1.5 whitespace-nowrap cursor-pointer"

  private val primaryButtonCls =
    "text-white bg-brand border border-transparent hover:bg-brand-strong focus:ring-4 focus:ring-brand-medium " +
      "shadow-xs font-medium leading-5 rounded-base text-xs px-3 py-1.5 whitespace-nowrap cursor-pointer " +
      "focus:outline-none"

  private val footerButtons: HtmlElement =
    div(
      cls("flex shrink-0 justify-end gap-2"),
      button(
        `type`("button"),
        cls(secondaryButtonCls),
        cls("hidden") <-- state.signal.map(_.draft.isEmpty).distinct,
        "Clear all",
        onClick.mapToUnit --> (_ => footerMode.set(ConfirmClear))
      ),
      button(`type`("button"), cls(secondaryButtonCls), "Cancel", onClick.mapToUnit --> (_ => requestClose())),
      button(
        `type`("button"),
        cls(primaryButtonCls),
        cls("opacity-50 cursor-not-allowed") <-- submitBlocker.map(_.isDefined),
        disabled <-- submitBlocker.map(_.isDefined),
        child.text <-- state.signal.map(_.tasks.size).distinct.map {
          case 1     => "Create 1 task"
          case count => s"Create $count tasks"
        },
        onClick.mapToUnit --> (_ => submit())
      )
    )

  private val closeConfirmation: HtmlElement =
    div(
      cls("flex flex-wrap items-center justify-end gap-2"),
      span(cls("text-sm text-body"), "Your tasks are saved as a draft."),
      button(
        `type`("button"),
        cls(secondaryButtonCls),
        "Keep editing",
        onClick.mapToUnit --> (_ => footerMode.set(Normal))
      ),
      button(
        `type`("button"),
        cls(secondaryButtonCls),
        "Discard",
        onClick.mapToUnit --> (_ => closeDiscardingDraft())
      ),
      button(
        `type`("button"),
        cls(primaryButtonCls),
        "Close, keep draft",
        onClick.mapToUnit --> (_ => closeKeepingDraft())
      )
    )

  private val clearConfirmation: HtmlElement =
    div(
      cls("flex flex-wrap items-center justify-end gap-2"),
      span(
        cls("text-sm text-body"),
        child.text <-- state.signal.map(_.tasks.size).distinct.map {
          case 0     => "Clear the text?"
          case 1     => "Clear the task and the text?"
          case count => s"Clear all $count tasks and the text?"
        }
      ),
      button(
        `type`("button"),
        cls(secondaryButtonCls),
        "Keep",
        onClick.mapToUnit --> (_ => footerMode.set(Normal))
      ),
      button(
        `type`("button"),
        cls(primaryButtonCls),
        "Clear all",
        onClick.mapToUnit --> (_ => clearAll())
      )
    )

  private val footer: HtmlElement =
    div(
      cls("mt-4 flex flex-wrap items-center justify-between gap-x-3 gap-y-2"),
      p(cls("grow min-w-0 text-xs text-body-subtle"), child.text <-- submitBlocker.map(_.getOrElse(""))),
      child <-- footerMode.signal.map {
        case Normal       => footerButtons
        case ConfirmClose => closeConfirmation
        case ConfirmClear => clearConfirmation
      }
    )

  private def indexOf(key: UUID): Int = state.now().tasks.indexWhere(_.key == key)

  private def finishDrop(): Unit = {
    (dragKey.now(), dropInsertion.now()) match {
      case (Some(key), Some(insertion)) => reorder(BulkTaskList.moveToInsertion(_, indexOf(key), insertion))
      case _                            => ()
    }
    dragKey.set(None)
    dropInsertion.set(None)
  }

  private def dragModifiers(key: UUID): Seq[Modifier[HtmlElement]] =
    Seq(
      draggable(true),
      onDragStart --> { event =>
        event.dataTransfer.setData("text/plain", key.toString)
        event.dataTransfer.effectAllowed = dom.DataTransferEffectAllowedKind.move
        dragKey.set(Some(key))
      },
      onDragOver.preventDefault --> { event =>
        val rect = event.currentTarget.asInstanceOf[dom.html.Element].getBoundingClientRect()
        val below = event.clientY > rect.top + rect.height / 2
        if (dragKey.now().isDefined) dropInsertion.set(Some(indexOf(key) + (if (below) 1 else 0)))
      },
      onDrop.preventDefault --> (_ => finishDrop()),
      onDragEnd --> { _ =>
        dragKey.set(None)
        dropInsertion.set(None)
      },
      cls <-- dragKey.signal.map(dragged => if (dragged.contains(key)) "opacity-50" else ""),
      cls <-- selected.signal.map(chosen => if (chosen.contains(key)) "bg-neutral-tertiary-medium" else ""),
      cls <-- dropIndicator(key)
    )

  private def dropIndicator(key: UUID): Signal[String] = {
    val position = state.signal.map(current => (current.tasks.indexWhere(_.key == key), current.tasks.size))
    dropInsertion.signal.combineWith(position).map {
      case (target, index, _) if target.contains(index)                        => "border-t-2 border-t-blue-500"
      case (target, index, size) if target.contains(size) && index == size - 1 => "border-b-2 border-b-blue-500"
      case _                                                                   => ""
    }
  }

  private def moveButton(key: UUID, label: String, glyph: String, delta: Int): HtmlElement = {
    val disabledSignal = state.signal.map { current =>
      val index = current.tasks.indexWhere(_.key == key)
      if (delta < 0) index <= 0 else index >= current.tasks.size - 1
    }
    button(
      `type`("button"),
      cls("px-1.5 text-body-subtle hover:text-heading disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"),
      aria.label(label),
      title(label),
      disabled <-- disabledSignal,
      glyph,
      onClick.mapToUnit --> (_ => reorder(BulkTaskList.moveBy(_, indexOf(key), delta)))
    )
  }

  private def controls(key: UUID): HtmlElement =
    div(
      cls("flex items-center shrink-0"),
      moveButton(key, "Move task up", "↑", -1),
      moveButton(key, "Move task down", "↓", 1),
      span(cls("ps-1.5 text-body-subtle cursor-grab select-none"), title("Drag to reorder"), "⠿")
    )

  private def loadProjects(): Unit =
    projectDataLoader.loadProjects().map(projects => state.update(_.copy(projects = projects))).unsafeRunAndForget()

  private def loadProjectData(projectId: Option[UUID]): Unit = {
    state.update(_.copy(projectData = TaskProjectData()))
    projectId.foreach { id =>
      projectDataLoader
        .loadProjectData(id)
        .map(data => if (state.now().projectId.contains(id)) state.update(_.copy(projectData = data)))
        .unsafeRunAndForget()
    }
  }

  private def issueText(issue: BulkParseIssue): String = {
    val reason = issue.reason match {
      case BulkParseIssueReason.MissingPrefix => "a task starts with \"- \""
      case BulkParseIssueReason.EmptyName     => "the task name is empty"
    }
    s"Line ${issue.lineNumber}: $reason"
  }

  private def projectPicker: Signal[HtmlElement] =
    state.signal.map(_.projects).distinct.map { projects =>
      TgSelectDropdown(
        inputSignal = state.signal.map(_.projectId.map(_.toString).getOrElse("")),
        inputStateUpdater = Observer[String](id => state.update(_.withProject(UUID.fromString(id)))),
        labelValue = "Project",
        options = projects.map { project =>
          val label = s"${project.icon} ${project.name}".trim
          TgSelectDropdownOption(project.id.toString, DropdownMeta(label, ""), () => span(label))
        },
        placeholder = "Select a project"
      ).htmlElement
    }

  // One grid for both panels, so the row under the "Parsed tasks" label (select all and the bulk edit toolbar, which
  // only that panel has) makes the text area start where the parsed tasks start, whatever height that row has. On a
  // narrow screen the cells just stack in this order.
  private val labelCls = "block mb-2.5 text-sm font-medium text-heading"

  private val tasksLabel = label(cls(labelCls), cls("md:col-start-1 md:row-start-1"), "Tasks")

  private val editor =
    div(
      cls("mb-4 md:mb-0 flex flex-col md:col-start-1 md:row-start-3"),
      textArea(
        cls(tgInputStyle),
        // grows with the column, which the grid stretches to the height of the parsed tasks beside it
        cls("grow font-mono min-h-[24rem] resize-y"),
        placeholder("- Task name 01\n- Task name 02"),
        spellCheck(false),
        autoFocus(true),
        controlled(
          value <-- state.signal.map(_.rawInput),
          onInput.mapToValue --> (raw => state.update(_.copy(rawInput = raw)))
        )
      ),
      ul(
        cls("mt-2 text-xs text-fg-danger-strong"),
        children <-- parsedSignal.map(_.issues).split(_.lineNumber)((_, issue, _) => li(issueText(issue)))
      )
    )

  private val parsedLabel = label(cls(labelCls), cls("md:col-start-2 md:row-start-1"), "Parsed tasks")

  private val selectionRow =
    div(
      cls("md:col-start-2 md:row-start-2"),
      child.maybe <-- state.signal.map(_.tasks.nonEmpty).distinct.map(Option.when(_)(selectionBar))
    )

  private val preview =
    div(
      cls("md:col-start-2 md:row-start-3"),
      cls("min-h-[24rem] max-h-[32rem] overflow-y-auto rounded-base border border-default-medium p-3"),
      child.maybe <-- state.signal
        .map(_.tasks.isEmpty)
        .distinct
        .map(isEmpty =>
          Option.when(isEmpty)(p(cls("text-sm text-body-subtle"), "Tasks you type on the left show up here"))
        ),
      div(
        cls("flex flex-col gap-2"),
        children <-- state.signal.map(_.tasks).split(_.key) { (key, _, task) =>
          BulkTaskCard.element(
            key,
            task,
            state.signal.map(_.lookups).distinct,
            popups,
            selectCheckbox(key),
            controls(key),
            dragModifiers(key) ++ selectOnClick(key)
          )
        }
      )
    )

  val element: HtmlElement =
    div(
      cls("fixed inset-0 z-[100] flex items-start justify-center pt-16 px-4 bg-white/90 dark:bg-neutral-primary/90"),
      onClick.filter(event => event.target == event.currentTarget).mapToUnit --> (_ => requestClose()),
      onMountCallback { _ =>
        loadProjects()
        if (restoredDraft.isDefined) toast.success("Restored your unfinished bulk add")
      },
      state.signal.map(_.draft).distinct.changes.debounce(DraftSaveDebounce.toMillis.toInt) --> (_ => saveDraft()),
      // the last change may still be waiting for its save when the page goes away
      windowEvents(_.onBeforeUnload) --> (_ => saveDraft()),
      // on the document, not the wrapper, so Escape closes even when focus has dropped to the page body;
      // a popup of the bulk edit (toolbar or badge) closes on Escape first (this listener runs before the popup's
      // own), and Escape on the discard question means keep editing
      documentEvents(_.onKeyDown).filter(_.key == "Escape").filterNot(_ => popups.isPopupOpen).mapToUnit -->
        (_ => if (footerMode.now() != Normal) footerMode.set(Normal) else requestClose()),
      state.signal.map(_.tasks.map(_.key)).distinct --> (keys => selected.update(BulkSelection.prune(_, keys))),
      state.signal.map(_.projectId).distinct --> (projectId => loadProjectData(projectId)),
      parsedSignal --> (parsed => state.update(current => current.copy(tasks = reconciled(current, parsed)))),
      // statuses and work days arrive after the modal opens, and change with the project
      state.signal.map(_.defaults).distinct.changes --> (_ => state.update(_.afterDefaultsChange)),
      div(
        cls(ModalStyles.contentBoxCls),
        cls("w-full max-w-6xl"),
        div(
          cls(ModalStyles.headerCls),
          h3(cls(ModalStyles.titleCls), "Bulk add tasks"),
          button(
            `type`("button"),
            cls(ModalStyles.closeButtonCls),
            aria.label("Close"),
            TgIcons.closeIcon("w-4 h-4"),
            onClick.mapToUnit --> (_ => requestClose())
          )
        ),
        div(cls("mb-4 max-w-sm"), child <-- projectPicker),
        div(
          cls("grid grid-cols-1 md:grid-cols-2 md:gap-x-4"),
          tasksLabel,
          editor,
          parsedLabel,
          selectionRow,
          preview
        ),
        BulkSummaryBar.element(
          summary = state.signal.map(current => BulkSummary.of(current.tasks, current.workDays)).distinct,
          lookups = state.signal.map(_.lookups).distinct,
          warnings = parsedSignal.map(_.issues.size),
          projectName = state.signal.map(_.projectName).distinct
        ),
        footer
      )
    )
}
