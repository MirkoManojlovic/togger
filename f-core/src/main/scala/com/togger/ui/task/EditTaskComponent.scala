package com.togger.ui.task

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.{TgClock, TgIds, TgJsonFailure, TgJsonSuccess}
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.logentry.{LogEntryComponent, LogEntryForm}
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.util.UUID
import scala.collection.immutable.ListMap

case class EditTaskForm(
  taskId: UUID,
  projectId: UUID,
  name: String = "",
  description: String = "",
  createBy: UUID = TgIds.UNASSIGNED_UUID,
  assignedToSelected: String = "",
  taskStatusSelected: String = ""
)

class EditTaskComponent(
  taskId: UUID,
  projectId: UUID,
  taskService: TaskFeService,
  taskStatusFeService: TaskStatusFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  projectService: ProjectService,
  router: TgRouter[FrontendPage],
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditTaskForm(taskId = taskId, projectId = projectId))
  val linksVar: Var[Seq[TaskLinkComponent]] = Var(Seq.empty)
  val createLogEntryVar: Var[Seq[LogEntryComponent]] = Var(Seq.empty)
  val statusesVar = Var(TaskStatusesState())
  val membersVar = Var(Seq.empty[SearchAccountDto])

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
                        taskStatusSelected = dto.task.taskStatusId.toString
                      )
                    )
                    linksVar
                      .set(dto.links.map(linkDto => new TaskLinkComponent(Some(TaskLinkFormData.fromResp(linkDto)))))
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
                            toast
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
                        toast
                      )

                    val allComps = Seq(newEntryComponent).++(logEntryComponents)
                    createLogEntryVar.set(allComps)
                  }
                )
              )
          )

      val membersCall: IO[Either[TgJsonFailure, TgJsonSuccess[Seq[SearchAccountDto]]]] =
        projectService.findAllMembers(projectId)

      (statusesAndTaskCall, membersCall).parMapN { (_, membersEither) =>
        membersEither.map(success => membersVar.set(success.body.get))
      }
    }
    .unsafeRunAndForget()

  val submitter = Observer[(EditTaskForm, Seq[TaskLinkComponent])] { case (form, linkComponents) =>
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
        updatedAt = now
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

  private val descriptionTextArea = formState.tgTextAreaManualErrorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Task name"
  )

  def renderElement(selectValues: Seq[TaskStatusSelect], members: Seq[SearchAccountDto]) = {
    val statusInputSelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.taskStatusSelected),
      labelValue = "Status",
      valuesAndDisplays = ListMap.from(selectValues.map(_.asTuple))
    )

    val assignedToSelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.assignedToSelected),
      labelValue = "Assigned to",
      emptyValue = Some("Unassigned"),
      valuesAndDisplays = ListMap.from(members.map(member => member.id.toString -> member.fullName))
    )

    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Edit task", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              form(
                onSubmit.preventDefault.mapTo(formState.now() -> linksVar.now()) --> submitter,
                nameInput.htmlElement.amend(cls("mb-4")),
                descriptionTextArea.htmlElement.amend(cls("mb-4")),
                statusInputSelect.htmlElement.amend(cls("mb-4")),
                assignedToSelect.htmlElement.amend(cls("mb-4")),
                child <-- linksVar.signal.map(taskLinkComponent =>
                  div(addNewLink, taskLinkComponent.map(_.htmlElement))
                ),
                TgHtmlButton.update(EventStream.fromValue(false))
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent, cls("mr-4")),
      hr(),
      child <-- createLogEntryVar.signal.map(logEntryComponents => div(logEntryComponents.map(_.element)))
    )
  }

  val element = div(
    child <-- statusesVar.signal.combineWith(membersVar.signal).map { case (state, members) =>
      renderElement(state.asTaskStatusSelect, members)
    }
  )

}
