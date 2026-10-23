package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.{DateRangeValidation, TgClock}
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{CreateMilestoneDateReq, UpdateMilestoneDateReq, UpdateMilestoneReq}
import com.togger.milestone.MilestoneStatus
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID
import scala.collection.immutable.ListMap

case class EditMilestoneForm(
  id: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  status: String = MilestoneStatus.InProgress.value.toString,
  startDate: String = "",
  dueDate: String = "",
  endDate: String = ""
)

class EditMilestoneComponent(
  milestoneService: MilestoneFeService,
  milestoneDateService: MilestoneDateFeService,
  spinnerService: SpinnerService,
  dateRangeValidation: DateRangeValidation,
  tgClock: TgClock,
  projectService: ProjectService,
  milestoneId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditMilestoneForm(id = milestoneId))
  val milestoneDatesVar = Var(Seq.empty[EditMilestoneDateComponent])
  val newMilestoneDatesVar = Var(Seq.empty[CreateMilestoneDateComponent])
  val workDaysVar = Var(Set.empty[DayOfWeek])

  private val dateBadges = new MilestoneDateBadges(
    dates = formState.signal.map(form => form.startDate -> form.dueDate),
    currentDates = () => formState.now().startDate -> formState.now().dueDate,
    currentWorkDays = () => workDaysVar.now(),
    onDates = (start, due) => formState.update(_.copy(startDate = start.toString, dueDate = due.toString)),
    tgClock = tgClock
  )

  private val milestoneStartDate: Signal[String] = formState.signal.map(_.startDate)

  lazy val addNewMilestoneDate =
    TgHtmlButton
      .extraSmall("+")
      .amend(
        typ("button"),
        cls("mb-4"),
        onClick --> (_ =>
          newMilestoneDatesVar.update(state => state :+ new CreateMilestoneDateComponent(milestoneStartDate, tgClock))
        )
      )

  spinnerService
    .runWithSpinner(milestoneService.findMilestoneById(milestoneId))
    .flatMap(
      _.fold(
        error => IO(println(error)),
        success => {
          val dto = success.body.get
          formState.set(
            EditMilestoneForm(
              id = dto.id,
              name = dto.name,
              description = dto.description,
              status = dto.status.value.toString,
              startDate = dto.startDate.toString,
              dueDate = dto.dueDate.toString,
              endDate = dto.endDate.map(_.toString).getOrElse("")
            )
          )
          projectService
            .findById(dto.projectId)
            .map(
              _.fold(error => println(error), success => workDaysVar.set(success.body.get.workDays.map(DayOfWeek.of)))
            )
        }
      )
    )
    .unsafeRunAndForget()

  spinnerService
    .runWithSpinner(milestoneDateService.findAllByMilestoneId(milestoneId))
    .map(
      _.fold(
        error => println(error),
        success => milestoneDatesVar.set(success.body.get.map(new EditMilestoneDateComponent(_, milestoneStartDate, tgClock)))
      )
    )
    .unsafeRunAndForget()

  private def updateMilestoneDates(dateComponents: Seq[EditMilestoneDateComponent]): IO[Unit] =
    dateComponents
      .map(_.formState.now())
      .map { dateForm =>
        val dto = UpdateMilestoneDateReq(
          id = dateForm.id,
          name = dateForm.name,
          description = dateForm.description,
          date = dateForm.dateAsInstant
        )
        milestoneDateService.updateMilestoneDate(dto).toastJustErrorsUnit(_ => ())
      }
      .toList
      .sequence
      .void

  private def createNewMilestoneDates(dateComponents: Seq[CreateMilestoneDateComponent]): IO[Unit] =
    dateComponents
      .map(_.formState.now())
      .filter(_.date.nonEmpty)
      .map { dateForm =>
        val dto = CreateMilestoneDateReq(
          milestoneId = milestoneId,
          name = dateForm.name,
          description = dateForm.description,
          date = dateForm.dateAsInstant
        )
        milestoneDateService.createMilestoneDate(dto).toastJustErrorsUnit(_ => ())
      }
      .toList
      .sequence
      .void

  lazy val submitter =
    Observer[(EditMilestoneForm, Seq[EditMilestoneDateComponent], Seq[CreateMilestoneDateComponent])] {
      case (form, dateComponents, newDateComponents) =>
        val dto = UpdateMilestoneReq(
          id = form.id,
          name = form.name,
          description = form.description,
          status = Some(MilestoneStatus.withValue(form.status.toInt)),
          startDate = Some(LocalDate.parse(form.startDate)),
          dueDate = Some(LocalDate.parse(form.dueDate)),
          endDate = if (form.endDate.isEmpty) None else Some(LocalDate.parse(form.endDate))
        )
        spinnerService
          .runWithSpinner(milestoneService.updateMilestone(dto))
          .toastMessageDto(_ => "Milestone updated successfully")(_ =>
            updateMilestoneDates(dateComponents) >> createNewMilestoneDates(newDateComponents)
          )
          .unsafeRunAndForget()
    }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Milestone name",
    tgInputType = TgInputType.Text
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Milestone description",
    minWidthRem = 0
  )

  private val statusSelect = formState.tgSelectManualError(
    inputAndUpdater = selectorAndUpdaterMacro(_.status),
    labelValue = "Status",
    valuesAndDisplays =
      ListMap.from(MilestoneStatus.values.map(status => status.value.toString -> status.statusName))
  )

  private val endDateErrors: EventStream[Seq[String]] = formState.signal.changes.map { form =>
    val orderError =
      if (
        form.startDate.nonEmpty && form.endDate.nonEmpty &&
        LocalDate.parse(form.endDate).isBefore(LocalDate.parse(form.startDate))
      )
        Seq("End date must be on or after start date")
      else Seq.empty
    val weekendError =
      if (form.endDate.nonEmpty && dateRangeValidation.isWeekend(LocalDate.parse(form.endDate), workDaysVar.now()))
        Seq("End date must be one of this project's configured work days")
      else Seq.empty
    orderError ++ weekendError
  }

  private val endDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.endDate),
    errors = endDateErrors,
    labelValue = "End date",
    tgInputType = TgInputType.Date,
    isRequired = false
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit a milestone", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(
                (formState.now(), milestoneDatesVar.now(), newMilestoneDatesVar.now())
              ) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              dateBadges.element.amend(cls("mb-4")),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
              statusSelect.htmlElement.amend(cls("mb-4")),
              endDateInput.htmlElement.amend(cls("mb-4")),
              addNewMilestoneDate,
              h5(cls("text-heading mb-2"), "Additional Milestone dates"),
              children <-- milestoneDatesVar.signal.map(dateComponents =>
                dateComponents.map(
                  _.htmlElement.amend(cls("border border-gray-300 dark:border-gray-600 rounded-lg p-4 mb-4"))
                )
              ),
              children <-- newMilestoneDatesVar.signal.map(dateComponents =>
                dateComponents.map(
                  _.htmlElement.amend(cls("border border-gray-300 dark:border-gray-600 rounded-lg p-4 mb-4"))
                )
              ),
              div(cls("flex justify-end"), TgHtmlButton.updateSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
  )

}
