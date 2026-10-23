package com.togger.ui.task

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.TgJavaTimeSyntax.TgInstantOps
import com.togger.common._
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.markdown.TgMarkdown
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.task.TaskPriority
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.components._
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.logentry.{LogEntryComponent, LogEntryForm}
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.modal.{ModalFactory, ModalStyles, PlainModalVariant, PlainTextModalMessage}
import com.togger.ui.project.ProjectService
import com.togger.ui.slacker.{SlackerMissingDays, SlackerScope}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.bulk.BulkTask
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID
import scala.util.Try

case class LogEntryPanelState(
  newEntryComponent: Option[LogEntryComponent] = None,
  logEntryComponents: Seq[(LocalDate, LogEntryComponent)] = Seq.empty,
  calendarComponent: Option[LogEntryCalendarComponent] = None
)

case class EditTaskForm(
  taskId: UUID,
  projectId: UUID,
  name: String = "",
  description: String = "",
  createBy: UUID = TgIds.UNASSIGNED_UUID,
  endDate: String = "",
  archived: Boolean = false
)

class EditTaskComponent(
  taskId: UUID,
  projectId: UUID,
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  milestoneService: MilestoneFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  val toast: Toast,
  tgMarkdown: TgMarkdown,
  createSlacker: (SlackerScope, () => Unit, () => Unit) => HtmlElement,
  modalFactory: ModalFactory
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditTaskForm(taskId = taskId, projectId = projectId))
  val linksVar: Var[Seq[TaskLinkComponent]] = Var(Seq.empty)
  val logEntryPanelVar: Var[LogEntryPanelState] = Var(LogEntryPanelState())
  val workDaysVar = Var(Set.empty[DayOfWeek])
  private val calendarSelectedDay = Var(Option.empty[LocalDate])

  private val tgDateFormater = new TgDateFormater

  private val ArchiveButtonCls =
    "text-fg-warning bg-warning-soft border border-warning-subtle hover:bg-warning-medium focus:ring-4 " +
      "focus:ring-warning-subtle font-medium leading-5 rounded-base text-xs px-3 py-1.5 focus:outline-none"
  private val UnarchiveButtonCls =
    "text-white bg-brand border border-transparent hover:bg-brand-strong focus:ring-4 focus:ring-brand-medium " +
      "font-medium leading-5 rounded-base text-xs px-3 py-1.5 focus:outline-none"

  private val badges = new TaskFormBadges(
    BulkTask(
      key = taskId,
      name = "",
      priority = TaskPriority.Medium,
      statusId = None,
      startDate = tgClock.currentDate(),
      dueDate = tgClock.currentDate(),
      endDate = None,
      assigneeId = None,
      labelIds = Set.empty,
      milestoneId = None,
      dueDateManual = true
    ),
    tgClock,
    Some(
      TaskProgressComponent
        .fromDescription(formState.signal.map(_.description), ringSizeCls = "w-3 h-3", textSizeCls = "text-xs")
        .element
    )
  )

  spinnerService
    .runWithSpinner {
      // fetched first, not in parallel with the rest -- `logEntryComponents`/`calendarComponent` below are
      // constructed from a plain `Set[DayOfWeek]` snapshot of `workDaysVar`, not a reactive signal, so they need
      // it to already be resolved by the time they're built.
      projectService
        .findById(projectId)
        .map(
          _.fold(
            e => println(e),
            success => {
              val workDays = success.body.get.workDays.map(DayOfWeek.of)
              workDaysVar.set(workDays)
              badges.setWorkDays(workDays)
            }
          )
        )
        .flatMap { _ =>
          val statusesAndTaskCall: IO[Unit] =
            // The status picker offers every status, read page by page (bounded by PaginatedLoader).
            taskStatusFeService
              .findAllByProjectId(projectId)
              .map(response => response.map(success => badges.setStatuses(success.body.get)))
              .flatMap(_ =>
                taskService
                  .findTaskById(taskId)
                  .map(
                    _.fold(
                      error => dom.window.alert("Oops an error: " + error),
                      success => {
                        val dto = success.body.get
                        formState.set(
                          EditTaskForm(
                            taskId = dto.task.id,
                            projectId = dto.task.projectId,
                            name = dto.task.name,
                            description = dto.task.description,
                            createBy = dto.task.createdBy,
                            endDate = dto.task.endDate.map(_.toString).getOrElse(""),
                            archived = dto.task.archived
                          )
                        )
                        badges.taskVar.set(
                          BulkTask(
                            key = dto.task.id,
                            name = dto.task.name,
                            priority = dto.task.priority,
                            statusId = Some(dto.task.taskStatusId),
                            startDate = dto.task.startDate,
                            dueDate = dto.task.dueDate,
                            endDate = dto.task.endDate,
                            assigneeId = dto.task.assignedTo,
                            labelIds = dto.task.taskLabelIds.toSet,
                            milestoneId = dto.task.milestone,
                            dueDateManual = true
                          )
                        )
                        linksVar.set(
                          dto.links.map(linkDto => new TaskLinkComponent(Some(TaskLinkFormData.fromResp(linkDto))))
                        )
                        rememberSaved()

                        val taskStartDate = dto.task.startDate
                        val taskEndDate = dto.task.endDate
                        def dateOf(logEntry: LogEntryDto) = logEntry.createdAt.toLocalDate()
                        val allLogEntryDates =
                          dto.logEntries.map(logEntry => dateOf(logEntry) -> logEntry.status).toMap

                        val logEntryComponents =
                          dto.logEntries
                            .sortBy(_.createdAt)
                            .reverse
                            .map(logEntry =>
                              dateOf(logEntry) -> new LogEntryComponent(
                                LogEntryForm.fromDto(logEntry),
                                taskService,
                                spinnerService,
                                accountService,
                                tgClock,
                                router,
                                toast,
                                tgMarkdown,
                                taskStartDate,
                                taskEndDate,
                                (allLogEntryDates - dateOf(logEntry)),
                                workDaysVar.now()
                              )
                            )

                        val newEntryComponent =
                          new LogEntryComponent(
                            LogEntryForm.default(dto.task.id, tgClock),
                            taskService,
                            spinnerService,
                            accountService,
                            tgClock,
                            router,
                            toast,
                            tgMarkdown,
                            taskStartDate,
                            taskEndDate,
                            allLogEntryDates,
                            workDaysVar.now()
                          )

                        calendarSelectedDay
                          .set(LogEntryCalendarComponent.lastCalendarDay(dto.logEntries, workDaysVar.now()))
                        val calendarComponent = new LogEntryCalendarComponent(
                          dto.logEntries,
                          tgDateFormater,
                          workDaysVar.now(),
                          calendarSelectedDay
                        )
                        logEntryPanelVar.set(
                          LogEntryPanelState(
                            newEntryComponent = Some(newEntryComponent),
                            logEntryComponents = logEntryComponents,
                            calendarComponent = Some(calendarComponent)
                          )
                        )
                      }
                    )
                  )
              )

          // The assignee picker offers every member, read page by page (bounded by PaginatedLoader).
          val membersCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[SearchAccountDto]]]] =
            projectService.findAllMembers(projectId)

          // The label picker offers every label, read page by page (bounded by PaginatedLoader).
          val labelsCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskLabelResp]]]] =
            taskLabelFeService.findAllByProjectId(projectId)

          // The milestone picker offers every milestone, read page by page (bounded by PaginatedLoader).
          val milestonesCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindMilestoneResp]]]] =
            milestoneService.findAllByProjectId(projectId)

          (statusesAndTaskCall, membersCall, labelsCall, milestonesCall).parMapN {
            (_, membersEither, labelsEither, milestonesEither) =>
              membersEither.map(success => badges.setMembers(success.body.get))
              labelsEither.map(success => badges.setLabels(success.body.get))
              milestonesEither.map(success => badges.setMilestones(success.body.get))
          }
        }
    }
    .unsafeRunAndForget()

  private def updateRequest(
    form: EditTaskForm,
    linkComponents: Seq[TaskLinkComponent],
    edited: BulkTask,
    statusId: UUID,
    endDate: Option[LocalDate]
  ): UpdateTaskReq = {
    val now = tgClock.currentInstant

    val (linksToCreate, linksToUpdate) = linkComponents.partition(_.formState.now().id.isEmpty)

    UpdateTaskReq(
      task = UpdateTaskReq1(
        id = form.taskId,
        projectId = form.projectId,
        name = form.name,
        description = form.description,
        assignedTo = edited.assigneeId,
        createdBy = form.createBy,
        taskStatusId = statusId,
        createdAt = now,
        updatedAt = now,
        priority = edited.priority,
        milestone = edited.milestoneId,
        startDate = Some(edited.startDate),
        dueDate = Some(edited.dueDate),
        endDate = endDate,
        taskLabelIds = edited.labelIds.toSeq,
        archived = form.archived
      ),
      linksForUpdate = linksToUpdate.map { linkComponent =>
        val linkForm = linkComponent.formState.now()
        UpdateLinkReq(
          id = linkForm.id.get,
          name = linkForm.name,
          url = linkForm.url
        )
      },
      linksToCreate = linksToCreate.map { linkComponent =>
        val linkForm = linkComponent.formState.now()
        CreateLinkReq(
          name = linkForm.name,
          url = linkForm.url
        )
      },
      linksToDelete = linksToDelete.now().toSeq
    )
  }

  val submitter = Observer[(EditTaskForm, Seq[TaskLinkComponent], BulkTask)] { case (form, linkComponents, edited) =>
    val dto = updateRequest(
      form,
      linkComponents,
      edited,
      edited.statusId.get,
      if (form.endDate.isEmpty) None else Some(LocalDate.parse(form.endDate))
    )
    spinnerService
      .runWithSpinner(sendUpdate(dto))
      .toastMessageJustMessage("Task updated successfully")
      .unsafeRunAndForget()
  }

  // ---- close

  private val closeDialogOpen = Var(false)
  private val closeEndDate = Var(tgClock.currentDate().toString)
  private val slackerVar: Var[Option[HtmlElement]] = Var(None)

  private def closeEndDateErrors: Signal[Seq[String]] =
    closeEndDate.signal.combineWith(badges.taskVar.signal, workDaysVar.signal).map { case (picked, task, workDays) =>
      TaskClose.endDateErrors(Try(LocalDate.parse(picked)).toOption, task.startDate, workDays)
    }

  private def closeTask(endDate: LocalDate): Unit =
    TaskClose.doneStatus(badges.lookupsNow().statuses).fold(toast.error("This project has no Done status")) { done =>
      val dto = updateRequest(formState.now(), linksVar.now(), badges.taskVar.now(), done.id, Some(endDate))
      spinnerService
        .runWithSpinner(sendUpdate(dto))
        .toastMessageCodeBlock("Task closed")(router.refresh())
        .unsafeRunAndForget()
    }

  private def openSlacker(endDate: LocalDate): Unit = {
    val close = () => slackerVar.set(None)
    slackerVar.set(
      Some(createSlacker(SlackerScope(taskId, projectId, endDate), close, () => closeTask(endDate)))
    )
  }

  /** Closes the task at once when every work day up to the end date has a log entry, else lets the user fill in the
    * missing ones in the slacker first.
    */
  private def requestClose(endDate: LocalDate): Unit =
    spinnerService
      .runWithSpinner(taskService.findTaskById(taskId))
      .flatMap(
        _.fold(
          error => IO(toast.error(error)),
          success =>
            IO {
              val task = success.body.get
              val missing = SlackerMissingDays.of(
                task.logEntries.map(_.createdAt.toLocalDate()),
                task.task.startDate,
                endDate,
                workDaysVar.now()
              )
              closeDialogOpen.set(false)
              if (missing.isEmpty) closeTask(endDate) else openSlacker(endDate)
            }
        )
      )
      .unsafeRunAndForget()

  private val closeDialog: HtmlElement =
    div(
      cls(ModalStyles.wrapperCls),
      ModalStyles.closeOnBackdropOrEscape(Observer(_ => closeDialogOpen.set(false))),
      div(
        cls(ModalStyles.contentBoxCls),
        cls("w-full max-w-sm"),
        h3(cls(ModalStyles.titleCls), cls("mb-4"), "Close task"),
        label(cls("block mb-2.5 text-sm font-medium text-heading"), "End date"),
        input(
          typ("date"),
          cls(
            "bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base block w-full px-3 py-2.5"
          ),
          controlled(value <-- closeEndDate.signal, onInput.mapToValue --> closeEndDate)
        ),
        children <-- closeEndDateErrors.map(_.map(error => p(cls("mt-2 text-sm text-danger"), error))),
        div(
          cls(ModalStyles.footerCls),
          cls("justify-end"),
          button(
            typ("button"),
            cls(ModalStyles.secondaryButtonCls),
            "Cancel",
            onClick.mapToUnit --> (_ => closeDialogOpen.set(false))
          ),
          button(
            typ("button"),
            cls(ModalStyles.primaryButtonCls),
            cls("disabled:opacity-50 disabled:cursor-not-allowed"),
            disabled <-- closeEndDateErrors.map(_.nonEmpty),
            "Close task",
            onClick.mapToUnit --> (_ => Try(LocalDate.parse(closeEndDate.now())).foreach(requestClose))
          )
        )
      )
    )

  /** An update call that only flips the archived flag; the rest of the form goes along as the Update button sends it.
    */
  private def setArchived(archived: Boolean): Unit = {
    val form = formState.now()
    val edited = badges.taskVar.now()
    val dto = updateRequest(
      form.copy(archived = archived),
      linksVar.now(),
      edited,
      edited.statusId.get,
      Option.when(form.endDate.nonEmpty)(LocalDate.parse(form.endDate))
    )
    spinnerService
      .runWithSpinner(sendUpdate(dto))
      .toastMessageCodeBlock(if (archived) "Task archived" else "Task unarchived")(
        formState.update(_.copy(archived = archived))
      )
      .unsafeRunAndForget()
  }

  // ---- unsaved changes

  private val savedDetails = Var(Option.empty[(String, String, BulkTask)])
  private val savedLinks = Var(Option.empty[Seq[TaskLinkFormData]])

  private val currentDetails: Signal[(String, String, BulkTask)] =
    formState.signal.combineWith(badges.taskVar.signal).map { case (form, task) => (form.name, form.description, task) }

  private def detailsDiffer(saved: Option[(String, String, BulkTask)], current: (String, String, BulkTask)): Boolean =
    saved.exists(_ != current)

  private def linksDiffer(saved: Option[Seq[TaskLinkFormData]], current: Seq[TaskLinkFormData]): Boolean =
    saved.exists(_ != current)

  private def hasUnsavedChanges(): Boolean = {
    val form = formState.now()
    detailsDiffer(savedDetails.now(), (form.name, form.description, badges.taskVar.now())) ||
    linksDiffer(savedLinks.now(), linksVar.now().map(_.formState.now()))
  }

  private val currentLinks: Signal[Seq[TaskLinkFormData]] =
    linksVar.signal.flatMapSwitch(components =>
      if (components.isEmpty) Val(Seq.empty) else Signal.sequence(components.map(_.formState.signal))
    )

  /** What the backend has now; the notes below compare the form against it. */
  private def rememberSaved(): Unit = {
    val form = formState.now()
    savedDetails.set(Some((form.name, form.description, badges.taskVar.now())))
    savedLinks.set(Some(linksVar.now().map(_.formState.now())))
  }

  private def changedNote(text: String, changed: Signal[Boolean]): Signal[Option[HtmlElement]] =
    changed.distinct.map(
      Option.when(_)(
        div(
          cls("px-2 py-1 mb-2 border-s-4 rounded-base bg-warning-soft border-warning-subtle text-fg-warning text-xs"),
          text
        )
      )
    )

  /** Held against every navigation away from the page: asks first when there is something that was not saved. */
  private val leaveGuard: (() => Unit) => Unit = proceed =>
    if (hasUnsavedChanges())
      modalFactory.plainModal(
        message = PlainTextModalMessage("You have unsaved changes. Leave this page without pressing Update?"),
        title = "Unsaved changes",
        variant = PlainModalVariant.Danger,
        confirmButtonMsg = "Leave without saving",
        cancelButtonMsg = "Stay"
      ) { close =>
        close()
        proceed()
      }
    else proceed()

  private val unsavedNotes: HtmlElement =
    div(
      child.maybe <-- changedNote(
        "The task details have changed. Press Update to save them.",
        savedDetails.signal.combineWith(currentDetails).map { case (saved, name, description, task) =>
          detailsDiffer(saved, (name, description, task))
        }
      ),
      child.maybe <-- changedNote(
        "The links have changed. Press Update to save them.",
        savedLinks.signal.combineWith(currentLinks).map { case (saved, current) => linksDiffer(saved, current) }
      )
    )

  private val archivedNote =
    div(
      cls("p-4 mb-4 border-s-4 rounded-base bg-warning-soft border-warning-subtle text-fg-warning text-sm"),
      "This task is archived."
    )

  private val archiveButton =
    button(
      typ("button"),
      child.text <-- formState.signal.map(_.archived).distinct.map(if (_) "Unarchive" else "Archive"),
      cls <-- formState.signal.map(_.archived).distinct.map(if (_) UnarchiveButtonCls else ArchiveButtonCls),
      onClick.mapToUnit --> (_ => setArchived(!formState.now().archived))
    )

  private val closeButton =
    button(
      typ("button"),
      cls(TgHtmlButton.tailwindExtraSmallButton),
      "Close",
      onClick.mapToUnit --> (_ => closeDialogOpen.set(true))
    )

  /** The popup that is open: the one that creates a link, or the one that changes the link of a badge. */
  private val openLinkEditor: Var[Option[Option[TaskLinkComponent]]] = Var(None)

  /** Saved links that were removed, deleted by the backend with the next update of the task. */
  private val linksToDelete = Var(Set.empty[UUID])

  private def removeLink(link: TaskLinkComponent): Unit = {
    closeLinkEditor()
    link.formState.now().id.foreach(id => linksToDelete.update(_ + id))
    linksVar.update(_.filterNot(_ eq link))
  }

  /** The update call; what it was told to delete is gone once it went through. */
  private def sendUpdate(dto: UpdateTaskReq): IO[TgResponseTypes.TgResp[FindTaskResp]] =
    taskService
      .updateTask(dto)
      .flatTap(response =>
        IO(
          if (response.isRight) {
            linksToDelete.set(Set.empty)
            rememberSaved()
          }
        )
      )

  private def closeLinkEditor(): Unit = openLinkEditor.set(None)

  private def linkEditorFor(link: Option[TaskLinkComponent]): HtmlElement =
    link.fold(
      new TaskLinkEditor(
        TaskLinkFormData(),
        "Create",
        data => {
          linksVar.update(_ :+ new TaskLinkComponent(Some(data)))
          closeLinkEditor()
        },
        () => closeLinkEditor()
      ).element
    ) { existing =>
      new TaskLinkEditor(
        existing.formState.now(),
        "Update",
        data => {
          existing.formState.update(old => data.copy(id = old.id))
          closeLinkEditor()
        },
        () => closeLinkEditor()
      ).element
    }

  private def linkWithEditor(link: Option[TaskLinkComponent], trigger: HtmlElement): HtmlElement =
    div(
      cls("relative inline-flex"),
      trigger,
      child.maybe <-- openLinkEditor.signal.map(_.contains(link)).distinct.map(Option.when(_)(linkEditorFor(link)))
    )

  /** The links as badges, each with a pencil that opens the popup to change it, and the badge that opens the popup to
    * create one in a row of its own. Links are saved with the Update button of the form.
    */
  private lazy val linksSection: HtmlElement =
    div(
      h4(cls("text-sm font-medium text-heading mb-2"), "Links"),
      child <-- linksVar.signal.map { components =>
        div(
          Option.when(components.nonEmpty)(
            div(
              cls("flex flex-wrap items-center gap-1.5 mb-2"),
              components.map { link =>
                linkWithEditor(
                  Some(link),
                  span(
                    child <-- link.formState.signal.map(data =>
                      TaskLinkBadges.badge(data, () => openLinkEditor.set(Some(Some(link))), () => removeLink(link))
                    )
                  )
                )
              }
            )
          ),
          div(cls("flex items-center mb-2"), linkWithEditor(None, addNewLink))
        )
      }
    )

  lazy val addNewLink: HtmlElement =
    TgBadge(text = "+ Add link", color = TgBadgeColor.Alternative, bordered = true, extraCls = "cursor-pointer").element
      .amend(
        role("button"),
        tabIndex(0),
        onClick --> (_ => openLinkEditor.set(Some(None))),
        onKeyDown.filter(event => event.key == "Enter" || event.key == " ").preventDefault -->
          (_ => openLinkEditor.set(Some(None)))
      )

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Task name",
    tgInputType = TgInputType.Text
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Description",
    placeholderValue = "Describe the task"
  )

  private lazy val editTaskCard: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit task", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault
                .mapTo((formState.now(), linksVar.now(), badges.taskVar.now())) --> submitter,
              unsavedNotes,
              child.maybe <-- formState.signal.map(_.archived).distinct.map(Option.when(_)(archivedNote)),
              nameInput.htmlElement.amend(cls("mb-4 max-w-[60ch]")),
              badges.element.amend(cls("mb-4")),
              linksSection.amend(cls("mb-4")),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
              div(
                cls("flex justify-end gap-2"),
                archiveButton,
                closeButton,
                TgHtmlButton.updateSmall(EventStream.fromValue(false))
              )
            )
          )
        )
      )
      .element

  val element =
    div(
      display.flex,
      flexDirection.column,
      alignItems.center,
      onMountUnmountCallback(_ => router.setLeaveGuard(leaveGuard), _ => router.clearLeaveGuard(leaveGuard)),
      // the browser asks by itself when the tab is closed or reloaded
      windowEvents(_.onBeforeUnload).filter(_ => hasUnsavedChanges()) --> (event => {
        event.preventDefault()
        event.returnValue = ""
      }),
      child.maybe <-- closeDialogOpen.signal.map(Option.when(_)(closeDialog)),
      child.maybe <-- slackerVar.signal,
      div(
        cls("flex w-full flex-col items-start gap-4 mb-4 lg:flex-row"),
        editTaskCard.amend(cls("min-w-0 lg:w-[64rem] lg:shrink-0")),
        div(
          cls("flex w-full min-w-0 flex-col gap-8 lg:flex-1 lg:basis-[48rem]"),
          child.maybe <-- logEntryPanelVar.signal.map(_.newEntryComponent.map(_.element)),
          child <-- logEntryPanelVar.signal.map(logEntriesCard)
        )
      )
    )

  private val logDateAttr = dataAttr("log-date")

  /** Scrolls the pane to the first log entry of the day, within the pane only so the page itself stays where it is. */
  private def scrollToDay(pane: dom.html.Element, day: LocalDate): Unit =
    Option(pane.querySelector(s"[data-log-date='$day']")).foreach { entry =>
      pane.scrollTop = entry.getBoundingClientRect().top - pane.getBoundingClientRect().top + pane.scrollTop
    }

  /** The log entries on the left in a pane that scrolls and the calendar on the right outside of it, so the calendar
    * stays where it is; a click on a day of the calendar scrolls the pane to the entry of that day.
    */
  private def logEntriesCard(state: LogEntryPanelState): HtmlElement = {
    // the card that was clicked is already in view, the pane only scrolls for a day clicked in the calendar
    var scrollOnSelect = true
    def selectFromCard(day: LocalDate): Unit =
      if (!calendarSelectedDay.now().contains(day)) {
        scrollOnSelect = false
        calendarSelectedDay.set(Some(day))
      }
    val pane = div(
      // room around the cards for the ring of the selected one, which the scrolling pane would cut
      cls("max-h-[70vh] overflow-y-auto scroll-smooth p-1 pe-2"),
      if (state.logEntryComponents.isEmpty) p(cls("text-sm text-body-subtle"), "No log entries yet")
      else
        state.logEntryComponents.map { case (day, entry) =>
          div(
            logDateAttr := day.toString,
            cls("cursor-pointer rounded-base"),
            cls <-- calendarSelectedDay.signal.map(selected =>
              if (selected.contains(day)) "ring-2 ring-blue-400" else ""
            ),
            onClick.mapToUnit --> (_ => selectFromCard(day)),
            entry.element
          )
        }
    )
    // a click on the day that is already selected clears it, the day that was clicked is then the one before
    val clickedDays: EventStream[LocalDate] =
      calendarSelectedDay.signal.changes
        .scanLeft((Option.empty[LocalDate], Option.empty[LocalDate])) { case ((_, previous), next) =>
          (next.orElse(previous), next)
        }
        .changes
        .collect { case (Some(day), _) => day }
    div(
      cls("w-full bg-neutral-primary-soft p-6 border border-default rounded-base shadow-xs"),
      clickedDays --> Observer[LocalDate] { day =>
        if (scrollOnSelect) scrollToDay(pane.ref, day)
        scrollOnSelect = true
      },
      div(
        cls("flex items-start gap-6"),
        div(cls("min-w-0 flex-1"), h4(cls("text-lg font-semibold text-heading mb-2"), "Log entries"), pane),
        div(
          cls("w-fit shrink-0"),
          h4(cls("text-lg font-semibold text-heading mb-2"), "Calendar"),
          state.calendarComponent.map(_.element).getOrElse(emptyNode)
        )
      )
    )
  }
}
