package com.togger.ui.task

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.TgJavaTimeSyntax.TgInstantOps
import com.togger.common.{TgClock, TgIds, TgJsonFailure, TgJsonSuccess}
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.markdown.TgMarkdown
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.task.TaskPriority
import com.togger.style.TgCommonStyleJs.heightAndWidthFitContent
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TgSelectDropdown.TgSelectDropdownSyntax
import com.togger.ui.components.{
  DropdownMeta,
  LogEntryCalendarComponent,
  TgBadge,
  TgSelectDropdownMultSelect,
  TgSelectDropdownOption
}
import com.togger.ui.logentry.{LogEntryComponent, LogEntryForm}
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.util.UUID
import scala.collection.immutable.ListMap

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
  assignedToSelected: String = "",
  taskStatusSelected: String = "",
  prioritySelected: String = ""
)

class EditTaskComponent(
  taskId: UUID,
  projectId: UUID,
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  taskLabelFeService: TaskLabelFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  val toast: Toast,
  tgMarkdown: TgMarkdown
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditTaskForm(taskId = taskId, projectId = projectId))
  val linksVar: Var[Seq[TaskLinkComponent]] = Var(Seq.empty)
  val logEntryPanelVar: Var[LogEntryPanelState] = Var(LogEntryPanelState())
  val statusesVar = Var(TaskStatusesState())
  val labelsVar = Var(TaskLabelsState())
  val currentLabelIdsVar = Var(Set.empty[String])
  val membersVar = Var(Seq.empty[SearchAccountDto])

  private val tgDateFormater = new TgDateFormater

  spinnerService
    .runWithSpinner {
      val statusesAndTaskCall: IO[Unit] =
        taskStatusFeService
          .findByProjectId(projectId)
          .map(response => response.map(success => statusesVar.set(TaskStatusesState(success.body.get))))
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
                        assignedToSelected = dto.task.assignedTo.map(_.toString).getOrElse(""),
                        taskStatusSelected = dto.task.taskStatusId.toString,
                        prioritySelected = dto.task.priority.value.toString
                      )
                    )
                    currentLabelIdsVar.set(dto.task.taskLabelIds.map(_.toString).toSet)
                    linksVar
                      .set(dto.links.map(linkDto => new TaskLinkComponent(Some(TaskLinkFormData.fromResp(linkDto)))))

                    val taskCreatedAt = dto.task.createdAt.toLocalDate()
                    def dateOf(logEntry: LogEntryDto) = logEntry.createdAt.toLocalDate()
                    val allLogEntryDates = dto.logEntries.map(dateOf)

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
                            taskCreatedAt,
                            (allLogEntryDates.toSet - dateOf(logEntry))
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
                        taskCreatedAt,
                        allLogEntryDates.toSet
                      )

                    val calendarComponent = new LogEntryCalendarComponent(
                      dto.logEntries,
                      tgDateFormater,
                      Var(LogEntryCalendarComponent.lastCalendarDay(dto.logEntries))
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

      val membersCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[SearchAccountDto]]]] =
        projectService.findAllMembers(projectId)

      val labelsCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[FindTaskLabelResp]]]] =
        taskLabelFeService.findByProjectId(projectId)

      (statusesAndTaskCall, membersCall, labelsCall).parMapN { (_, membersEither, labelsEither) =>
        membersEither.map(success => membersVar.set(success.body.get))
        labelsEither.map(success => labelsVar.set(TaskLabelsState(success.body.get)))
      }
    }
    .unsafeRunAndForget()

  val submitter = Observer[(EditTaskForm, Seq[TaskLinkComponent], Set[String])] {
    case (form, linkComponents, selectedLabelIds) =>
      val now = tgClock.currentInstant

      val (linksToCreate, linksToUpdate) = linkComponents.partition(_.formState.now().id.isEmpty)

      val dto = UpdateTaskReq(
        task = UpdateTaskReq1(
          id = form.taskId,
          projectId = form.projectId,
          name = form.name,
          description = form.description,
          assignedTo = Option.when(form.assignedToSelected.nonEmpty)(UUID.fromString(form.assignedToSelected)),
          createdBy = form.createBy,
          taskStatusId = UUID.fromString(form.taskStatusSelected),
          createdAt = now,
          updatedAt = now,
          priority = TaskPriority.withValue(form.prioritySelected.toInt),
          taskLabelIds = selectedLabelIds.map(UUID.fromString(_)).toSeq
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

  private val progress = TaskProgressComponent.fromDescription(formState.signal.map(_.description))

  private val priorityOptions: Seq[TgSelectDropdownOption] =
    TaskPriority.values.map { priority =>
      val meta = DropdownMeta(
        label = s"${priority.shortCode.toUpperCase} - ${priority.priorityName}",
        description = priority.description
      )
      TgSelectDropdownOption(
        value = priority.value.toString,
        meta = meta,
        render = () => TgBadge(text = meta.label, color = priority.badgeColor, bordered = true).element
      )
    }

  def renderElement(
    selectValues: Seq[TaskStatusSelect],
    members: Seq[SearchAccountDto],
    labels: Seq[FindTaskLabelResp]
  ) = {
    val statusInputSelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.taskStatusSelected),
      labelValue = "Status",
      valuesAndDisplays = ListMap.from(selectValues.map(_.asTuple))
    )

    val priorityInputSelect = formState.tgSelectDropdownManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.prioritySelected),
      labelValue = "Priority",
      options = priorityOptions
    )

    val assignedToSelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.assignedToSelected),
      labelValue = "Assigned to",
      emptyValue = Some("Unassigned"),
      valuesAndDisplays = ListMap.from(members.map(member => member.id.toString -> member.fullName))
    )

    val labelOptions: Seq[TgSelectDropdownOption] = labels.sortBy(_.name).map { dto =>
      TgSelectDropdownOption(
        value = dto.id.toString,
        meta = DropdownMeta(label = dto.name, description = dto.description),
        render = () => span(dto.name)
      )
    }

    val labelPicker = TgSelectDropdownMultSelect(
      inputSignal = currentLabelIdsVar.signal,
      inputStateUpdater = currentLabelIdsVar.writer,
      labelValue = "Labels",
      options = labelOptions
    )

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
                  .mapTo(formState.now() -> linksVar.now())
                  .compose(_.withCurrentValueOf(currentLabelIdsVar.signal)) --> submitter,
                div(
                  display.flex,
                  columnGap("2rem"),
                  cls("mb-4"),
                  div(
                    nameInput.htmlElement.amend(cls("mb-4")),
                    descriptionEditor.htmlElement.amend(cls("mb-4")),
                    progress.element
                  ),
                  div(
                    flexShrink(0),
                    width("280px"),
                    statusInputSelect.htmlElement.amend(cls("mb-4")),
                    priorityInputSelect.htmlElement.amend(cls("mb-4")),
                    assignedToSelect.htmlElement.amend(cls("mb-4")),
                    labelPicker.htmlElement
                  )
                ),
                child <-- linksVar.signal.map(taskLinkComponent =>
                  div(addNewLink, taskLinkComponent.map(_.htmlElement))
                ),
                TgHtmlButton.update(EventStream.fromValue(false))
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent, cls("mb-4")),
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

  val element = div(
    child <-- statusesVar.signal.combineWith(membersVar.signal, labelsVar.signal).map {
      case (state, members, labelsState) =>
        renderElement(state.asTaskStatusSelect, members, labelsState.taskLabels)
    }
  )

}
