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
import com.togger.ui.auth.{AccountService, TgCard, TgCheckbox, TgInputType}
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.components._
import com.togger.ui.logentry.{LogEntryComponent, LogEntryForm}
import com.togger.ui.milestone.MilestoneFeService
import com.togger.ui.modal.ModalStyles
import com.togger.ui.slacker.{SlackerMissingDays, SlackerScope}
import com.togger.ui.project.ProjectService
import com.togger.ui.task.bulk.BulkTask
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID
import scala.util.Try

case class LogEntryPanelState(
  newEntryComponent: Option[LogEntryComponent] = None,
  logEntryComponents: Seq[LogEntryComponent] = Seq.empty,
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
  createSlacker: (SlackerScope, () => Unit, () => Unit) => HtmlElement
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditTaskForm(taskId = taskId, projectId = projectId))
  val linksVar: Var[Seq[TaskLinkComponent]] = Var(Seq.empty)
  val logEntryPanelVar: Var[LogEntryPanelState] = Var(LogEntryPanelState())
  val workDaysVar = Var(Set.empty[DayOfWeek])

  private val tgDateFormater = new TgDateFormater

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
                              new LogEntryComponent(
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

                        val calendarComponent = new LogEntryCalendarComponent(
                          dto.logEntries,
                          tgDateFormater,
                          workDaysVar.now(),
                          Var(LogEntryCalendarComponent.lastCalendarDay(dto.logEntries, workDaysVar.now()))
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
      }
    )
  }

  val submitter = Observer[(EditTaskForm, Seq[TaskLinkComponent], BulkTask)] {
    case (form, linkComponents, edited) =>
      val dto = updateRequest(
        form,
        linkComponents,
        edited,
        edited.statusId.get,
        if (form.endDate.isEmpty) None else Some(LocalDate.parse(form.endDate))
      )
      spinnerService
        .runWithSpinner(taskService.updateTask(dto))
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
        .runWithSpinner(taskService.updateTask(dto))
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
          cls("bg-neutral-secondary-medium border border-default-medium text-heading text-sm rounded-base block w-full px-3 py-2.5"),
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

  private val closeButton =
    button(
      typ("button"),
      cls(TgHtmlButton.tailwindExtraSmallButton),
      "Close",
      onClick.mapToUnit --> (_ => closeDialogOpen.set(true))
    )

  lazy val addNewLink =
    TgHtmlButton
      .extraSmall("+")
      .amend(cls("mb-4"), onClick --> (_ => linksVar.update(state => state :+ new TaskLinkComponent())))

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

  private val archivedCheckbox = TgCheckbox.checkBox(
    inputSignal = formState.signal.map(_.archived),
    inputStateUpdater = formState.updater[Boolean]((form, archived) => form.copy(archived = archived)),
    labelValue = "Archived"
  )

  val element =
    div(
      display.flex,
      flexDirection.column,
      alignItems.center,
      child.maybe <-- closeDialogOpen.signal.map(Option.when(_)(closeDialog)),
      child.maybe <-- slackerVar.signal,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Edit task", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              form(
                onSubmit.preventDefault
                  .mapTo((formState.now(), linksVar.now(), badges.taskVar.now())) --> submitter,
                nameInput.htmlElement.amend(cls("mb-4")),
                badges.element.amend(cls("mb-4")),
                descriptionEditor.htmlElement.amend(cls("mb-4")),
                archivedCheckbox.htmlElement.amend(cls("mb-4")),
                child <-- linksVar.signal.map(taskLinkComponent =>
                  div(addNewLink, taskLinkComponent.map(_.htmlElement))
                ),
                div(cls("flex justify-end gap-2"), closeButton, TgHtmlButton.updateSmall(EventStream.fromValue(false)))
              )
            )
          )
        )
        .element
        .amend(cls("mb-4")),
      hr(alignSelf.stretch),
      child <-- logEntryPanelVar.signal.map { state =>
        div(
          display.flex,
          columnGap("2rem"),
          alignItems.flexStart,
          div(
            flexGrow(1),
            minWidth("0"),
            h4(cls("text-lg font-semibold text-heading mb-2"), "Add new log status"),
            state.newEntryComponent.map(_.element).getOrElse(emptyNode)
          ),
          div(
            flexGrow(1),
            minWidth("0"),
            h4(cls("text-lg font-semibold text-heading mb-2"), "Log entries"),
            div(state.logEntryComponents.map(_.element))
          ),
          div(
            flexShrink(0),
            cls("w-fit"),
            h4(cls("text-lg font-semibold text-heading mb-2"), "Calendar"),
            state.calendarComponent.map(_.element).getOrElse(emptyNode)
          )
        )
      }
    )

}
