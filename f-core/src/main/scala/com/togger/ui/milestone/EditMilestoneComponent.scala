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
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.components.{
  CalendarPickerPopup,
  ChartColorBadgeColor,
  QuickOption,
  QuickOptionsPopup,
  TgBadge,
  TgBadgeColor
}
import com.togger.ui.modal.ModalStyles
import com.togger.ui.task.TgDateFormater
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import org.scalajs.dom

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID
import scala.util.Try

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

  private def addNewDate(): Unit =
    newMilestoneDatesVar.update(state => state :+ new CreateMilestoneDateComponent(milestoneStartDate, tgClock))

  lazy val addNewMilestoneDate: HtmlElement =
    TgBadge(text = "+ Add additional date", color = TgBadgeColor.Alternative, bordered = true, extraCls = "cursor-pointer").element
      .amend(
        role("button"),
        tabIndex(0),
        onClick --> (_ => addNewDate()),
        onKeyDown.filter(event => event.key == "Enter" || event.key == " ").preventDefault --> (_ => addNewDate())
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
        success =>
          milestoneDatesVar.set(success.body.get.map(new EditMilestoneDateComponent(_, milestoneStartDate, tgClock)))
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

  private def save(
    form: EditMilestoneForm,
    dateComponents: Seq[EditMilestoneDateComponent],
    newDateComponents: Seq[CreateMilestoneDateComponent],
    successMessage: String
  ): Unit = {
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
      .toastMessageDto(_ => successMessage)(_ =>
        updateMilestoneDates(dateComponents) >> createNewMilestoneDates(newDateComponents)
      )
      .unsafeRunAndForget()
  }

  lazy val submitter =
    Observer[(EditMilestoneForm, Seq[EditMilestoneDateComponent], Seq[CreateMilestoneDateComponent])] {
      case (form, dateComponents, newDateComponents) =>
        save(form, dateComponents, newDateComponents, "Milestone updated successfully")
    }

  // ---- close

  private val closeDialogOpen = Var(false)
  private val closeEndDate = Var(tgClock.currentDate())
  private val closePickerOpen = Var(false)
  private val tgDateFormater = new TgDateFormater

  private def closeMilestone(): Unit = {
    val closed =
      formState.now().copy(status = MilestoneStatus.Done.value.toString, endDate = closeEndDate.now().toString)
    closeDialogOpen.set(false)
    closePickerOpen.set(false)
    formState.set(closed)
    save(closed, milestoneDatesVar.now(), newMilestoneDatesVar.now(), "Milestone closed")
  }

  private def closePicker: HtmlElement =
    CalendarPickerPopup(
      initialMonth = closeEndDate.now(),
      today = tgClock.currentDate(),
      tgDateFormater = tgDateFormater,
      selected = Some(closeEndDate.now()),
      minDate = Try(LocalDate.parse(formState.now().startDate)).toOption,
      isDisabled = day => !workDaysVar.now().contains(day.getDayOfWeek),
      onPick = day => closeEndDate.set(day),
      onClose = () => closePickerOpen.set(false)
    ).element

  private val closeEndDateField: HtmlElement =
    div(
      cls("relative"),
      TgBadge(text = "", color = TgBadgeColor.Gray, bordered = true).element
        .amend(
          cls("cursor-pointer"),
          child.text <-- closeEndDate.signal.map(tgDateFormater.asAmericanDateWithDayAbrv),
          // stopPropagation keeps this click from reaching the popup's own outside-click listener
          onClick.stopPropagation.mapToUnit --> (_ => closePickerOpen.update(!_))
        ),
      child.maybe <-- closePickerOpen.signal.map(Option.when(_)(closePicker))
    )

  private val closeDialog: HtmlElement =
    div(
      cls(ModalStyles.wrapperCls),
      ModalStyles.closeOnBackdropOrEscape(Observer(_ => closeDialogOpen.set(false))),
      div(
        cls(ModalStyles.contentBoxCls),
        cls("w-full max-w-sm"),
        h3(cls(ModalStyles.titleCls), cls("mb-4"), "Close milestone"),
        label(cls("block mb-2.5 text-sm font-medium text-heading"), "End date"),
        closeEndDateField,
        div(
          cls(ModalStyles.footerCls),
          cls("justify-end mt-72"),
          button(
            typ("button"),
            cls(ModalStyles.secondaryButtonCls),
            "Cancel",
            onClick.mapToUnit --> (_ => closeDialogOpen.set(false))
          ),
          button(
            typ("button"),
            cls(ModalStyles.primaryButtonCls),
            "Close milestone",
            onClick.mapToUnit --> (_ => closeMilestone())
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

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Milestone name",
    tgInputType = TgInputType.Text
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Milestone description",
    placeholderValue = "Describe the milestone"
  )

  private val statusMenuOpen = Var(false)

  private val statusBadge: HtmlElement =
    div(
      cls("relative"),
      child <-- formState.signal.map(_.status).distinct.map { value =>
        val status = MilestoneStatus.withValue(value.toInt)
        TgBadge(text = status.toStringWithEmoji, color = ChartColorBadgeColor.of(status), bordered = true).element
          .amend(
            cls("cursor-pointer"),
            cls("ring-2 ring-blue-500") <-- statusMenuOpen.signal,
            // stopPropagation keeps this click from reaching the menu's own outside-click listener
            onClick.stopPropagation.mapToUnit --> (_ => statusMenuOpen.update(!_))
          )
      },
      child.maybe <-- statusMenuOpen.signal.map(Option.when(_)(statusMenu))
    )

  private def statusMenu: HtmlElement =
    QuickOptionsPopup(
      options = MilestoneStatus.values.map(status =>
        QuickOption(
          status.value.toString,
          status.toStringWithEmoji,
          status.description,
          formState.now().status == status.value.toString,
          Some(ChartColorBadgeColor.of(status))
        )
      ),
      onChoose = option => {
        formState.update(_.copy(status = option.id))
        statusMenuOpen.set(false)
      },
      widthCls = "w-64"
    ).element.amend(
      onMountBind { ctx =>
        val rootNode = ctx.thisNode.ref
        documentEvents(_.onClick)
          .filter(event => !rootNode.contains(event.target.asInstanceOf[dom.Node]))
          .mapToUnit --> (_ => statusMenuOpen.set(false))
      },
      onMountBind(_ =>
        documentEvents(_.onKeyDown).filter(_.key == "Escape").mapToUnit --> (_ => statusMenuOpen.set(false))
      )
    )

  private lazy val editMilestoneCard: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit milestone", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(
                (formState.now(), milestoneDatesVar.now(), newMilestoneDatesVar.now())
              ) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4 max-w-[60ch]")),
              div(cls("mb-4 flex flex-wrap items-center gap-1.5"), statusBadge, dateBadges.element),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
              div(
                cls("mb-4"),
                h4(cls("text-sm font-medium text-heading mb-2"), "Additional milestone dates"),
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
                addNewMilestoneDate
              ),
              div(cls("flex justify-end gap-2"), closeButton, TgHtmlButton.updateSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
      .amend(cls("min-w-0 lg:w-[80rem] lg:shrink-0"))

  val element =
    div(
      justifyCenterFlex,
      child.maybe <-- closeDialogOpen.signal.map(Option.when(_)(closeDialog)),
      editMilestoneCard
    )

}
