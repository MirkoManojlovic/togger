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
import com.togger.ui.project.ProjectService
import com.togger.ui.task.bulk.BulkTask
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

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
  dateRangeValidation: DateRangeValidation,
  val toast: Toast,
  tgMarkdown: TgMarkdown
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

  val submitter = Observer[(EditTaskForm, Seq[TaskLinkComponent], BulkTask)] {
    case (form, linkComponents, edited) =>
      val now = tgClock.currentInstant

      val (linksToCreate, linksToUpdate) = linkComponents.partition(_.formState.now().id.isEmpty)

      val dto = UpdateTaskReq(
        task = UpdateTaskReq1(
          id = form.taskId,
          projectId = form.projectId,
          name = form.name,
          description = form.description,
          assignedTo = edited.assigneeId,
          createdBy = form.createBy,
          taskStatusId = edited.statusId.get,
          createdAt = now,
          updatedAt = now,
          priority = edited.priority,
          milestone = edited.milestoneId,
          startDate = Some(edited.startDate),
          dueDate = Some(edited.dueDate),
          endDate = if (form.endDate.isEmpty) None else Some(LocalDate.parse(form.endDate)),
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
      spinnerService
        .runWithSpinner(taskService.updateTask(dto))
        .toastMessageJustMessage("Task updated successfully")
        .unsafeRunAndForget()
  }

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

  private val endDateErrors: EventStream[Seq[String]] = formState.signal.changes.map { form =>
    val orderError =
      if (form.endDate.nonEmpty && LocalDate.parse(form.endDate).isBefore(badges.taskVar.now().startDate))
        Seq("End date must be on or after start date")
      else Seq.empty
    val weekendError =
      if (form.endDate.nonEmpty && dateRangeValidation.isWeekend(LocalDate.parse(form.endDate), workDaysVar.now()))
        Seq("End date must be one of this project's configured work days")
      else Seq.empty
    orderError ++ weekendError
  }

  private val archivedCheckbox = TgCheckbox.checkBox(
    inputSignal = formState.signal.map(_.archived),
    inputStateUpdater = formState.updater[Boolean]((form, archived) => form.copy(archived = archived)),
    labelValue = "Archived"
  )

  private val endDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.endDate),
    errors = endDateErrors,
    labelValue = "End date",
    tgInputType = TgInputType.Date,
    isRequired = false
  )

  val element =
    div(
      display.flex,
      flexDirection.column,
      alignItems.center,
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
                endDateInput.htmlElement.amend(cls("mb-4"), width("280px")),
                archivedCheckbox.htmlElement.amend(cls("mb-4")),
                child <-- linksVar.signal.map(taskLinkComponent =>
                  div(addNewLink, taskLinkComponent.map(_.htmlElement))
                ),
                div(cls("flex justify-end"), TgHtmlButton.updateSmall(EventStream.fromValue(false)))
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
