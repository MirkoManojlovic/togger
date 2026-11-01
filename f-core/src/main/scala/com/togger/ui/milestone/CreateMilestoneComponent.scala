package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.components.button.TgHtmlButton
import com.togger.dto.CreateMilestoneDateReq
import com.togger.dto.CreateMilestoneReq
import com.togger.dto.FindMilestoneResp
import com.togger.style.TgCommonStyleJs.heightAndWidthFitContent
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.dto.FindMilestoneTemplateResp
import com.togger.dto.MilestoneFilter
import com.togger.dto.UpdateMilestoneReq
import com.togger.milestone.DefaultMilestoneStep
import com.togger.milestone.MilestoneEndpoints
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgCard
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgInputType
import com.togger.ui.components.TgBadge
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.project.ProjectService
import com.togger.ui.auth.TgCard
import com.togger.ui.auth.TgInputType
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.components.TgBadge
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.milestonetemplate.MilestoneTemplateFeService
import com.togger.ui.milestonetemplate.MilestoneTemplatePicker
import com.togger.ui.milestonetemplate.MilestoneTemplateStepsEditing
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.TaskConfigTableSupport.iconButton
import com.togger.ui.task.TaskConfigTableSupport.rowButtonMarginCls
import com.togger.ui.task.TaskConfigTableSupport.rowIconButtonCls
import com.togger.ui.task.TaskConfigTableSupport.rowIconButtonDangerCls
import com.togger.ui.task.bulk.BulkTaskDefaults
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.task.bulk.BulkTaskDefaults
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

case class CreateMilestoneForm(
  projectId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  startDate: String = "",
  dueDate: String = ""
)

class CreateMilestoneComponent(
  milestoneService: MilestoneFeService,
  milestoneDateService: MilestoneDateFeService,
  milestoneTemplateService: MilestoneTemplateFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  projectService: ProjectService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateMilestoneForm(projectId = projectId))
  val workDaysVar = Var(Set.empty[DayOfWeek])

  spinnerService
    .runWithSpinner(
      projectService
        .findById(projectId)
        .map(
          _.fold(error => println(error), success => applyProjectWorkDays(success.body.get.workDays.map(DayOfWeek.of)))
        )
    )
    .unsafeRunAndForget()

  private val templatesVar = Var(Seq.empty[FindMilestoneTemplateResp])
  private val selectedTemplateVar = Var(Option.empty[UUID])

  spinnerService
    .runWithSpinner(milestoneTemplateService.findAllByProjectId(projectId))
    .map(_.foreach(success => templatesVar.set(success.body.getOrElse(Seq.empty))))
    .unsafeRunAndForget()

  private val milestoneRange: Signal[(String, String)] = formState.signal.map(form => form.startDate -> form.dueDate).distinct

  val milestoneStepsVar = Var(List.empty[CreateMilestoneStepComponent])

  private def newStepComponent(draft: MilestoneStepDraft) =
    new CreateMilestoneStepComponent(draft, milestoneRange, workDaysVar.signal)

  /** The template's steps replace the current ones, with the milestone range spread evenly over them. */
  private def applyTemplate(template: Option[FindMilestoneTemplateResp]): Unit = {
    selectedTemplateVar.set(template.map(_.id))
    val form = formState.now()
    val drafts = template.fold(List.empty[MilestoneStepDraft])(chosen =>
      MilestoneStepsDraft.fromTemplate(chosen.steps, form.startDate, form.dueDate, workDaysVar.now())
    )
    milestoneStepsVar.set(drafts.map(newStepComponent))
  }

  private def addStep(): Unit = {
    val form = formState.now()
    milestoneStepsVar.update(_ :+ newStepComponent(MilestoneStepDraft(startDate = form.startDate, dueDate = form.dueDate)))
  }

  /** Spreads the milestone range over all steps again, whatever was typed. */
  private def spreadStepDates(): Unit = {
    val steps = milestoneStepsVar.now()
    val form = formState.now()
    steps.zip(MilestoneStepsDraft.spread(steps.size, form.startDate, form.dueDate, workDaysVar.now())).foreach {
      case (step, (start, due)) => step.setAutomaticDates(start, due)
    }
  }

  /** While no step has its own dates, the steps follow the milestone range when that changes. */
  private val followMilestoneRange: Observer[Unit] = Observer[Unit] { _ =>
    if (milestoneStepsVar.now().nonEmpty && !milestoneStepsVar.now().exists(_.datesEdited)) spreadStepDates()
  }

  private val stepsHaveErrors: Signal[Boolean] =
    milestoneStepsVar.signal
      .flatMapSwitch(steps => Signal.combineSeq(steps.map(_.errors)))
      .map(_.exists(_.nonEmpty))

  /** A new milestone starts on the next work day after next Monday and lasts the default number of work days. */
  private def applyProjectWorkDays(workDays: Set[DayOfWeek]): Unit = {
    workDaysVar.set(workDays)
    val defaults = BulkTaskDefaults.of(tgClock.currentDate(), workDays, Seq.empty)
    formState.update(_.copy(startDate = defaults.startDate.toString, dueDate = defaults.dueDate.toString))
  }

  private val dateBadges = new MilestoneDateBadges(
    dates = formState.signal.map(form => form.startDate -> form.dueDate),
    currentDates = () => formState.now().startDate -> formState.now().dueDate,
    currentWorkDays = () => workDaysVar.now(),
    onDates = (start, due) => formState.update(_.copy(startDate = start.toString, dueDate = due.toString)),
    tgClock = tgClock
  )

  private val milestoneStartDate: Signal[String] = formState.signal.map(_.startDate)

  val milestoneDatesVar = Var(Seq.empty[CreateMilestoneDateComponent])

  private def addNewDate(): Unit =
    milestoneDatesVar.update(state => state :+ new CreateMilestoneDateComponent(milestoneStartDate, tgClock))

  lazy val addNewMilestoneDate: HtmlElement =
    TgBadge(
      text = "+ Add additional date",
      color = TgBadgeColor.Alternative,
      bordered = true,
      extraCls = "cursor-pointer"
    ).element
      .amend(
        role("button"),
        tabIndex(0),
        onClick --> (_ => addNewDate()),
        onKeyDown.filter(event => event.key == "Enter" || event.key == " ").preventDefault --> (_ => addNewDate())
      )

  private def createMilestoneDates(
    milestone: FindMilestoneResp,
    dateComponents: Seq[CreateMilestoneDateComponent]
  ): IO[Unit] =
    dateComponents
      .map(_.formState.now())
      .filter(_.date.nonEmpty)
      .map { dateForm =>
        val dto = CreateMilestoneDateReq(
          milestoneId = milestone.id,
          name = dateForm.name,
          description = dateForm.description,
          date = dateForm.dateAsInstant
        )
        milestoneDateService.createMilestoneDate(dto).toastJustErrorsUnit(_ => ())
      }
      .toList
      .sequence
      .void

  lazy val submitter = Observer[(CreateMilestoneForm, Seq[CreateMilestoneDateComponent], Seq[MilestoneStepDraft])] {
    case (form, dateComponents, stepDrafts) =>
      val dto = CreateMilestoneReq(
        projectId = form.projectId,
        name = form.name,
        description = form.description,
        startDate = LocalDate.parse(form.startDate),
        dueDate = LocalDate.parse(form.dueDate),
        steps = MilestoneStepsDraft.toRequests(stepDrafts)
      )
      spinnerService
        .runWithSpinner(milestoneService.createMilestone(dto))
        .toastMessageDto(_ => "Milestone created successfully")(createMilestoneDates(_, dateComponents))
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
    placeholderValue = "Describe the milestone"
  )

  private def stepCard(step: CreateMilestoneStepComponent, index: Int): HtmlElement =
    div(
      cls("border border-gray-300 dark:border-gray-600 rounded-lg p-4 mb-4"),
      div(
        cls("mb-3 flex items-center justify-between"),
        span(cls("text-sm font-medium text-heading"), s"Step ${index + 1}"),
        div(
          iconButton(
            "Move up",
            span("↑"),
            rowIconButtonCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => milestoneStepsVar.update(MilestoneTemplateStepsEditing.move(_, index, -1)))
          ),
          iconButton(
            "Move down",
            span("↓"),
            rowIconButtonCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => milestoneStepsVar.update(MilestoneTemplateStepsEditing.move(_, index, 1)))
          ),
          iconButton(
            "Remove step",
            TgIcons.trashIcon(className = "w-4 h-4"),
            rowIconButtonDangerCls,
            rowButtonMarginCls,
            onClick.mapToUnit --> (_ => milestoneStepsVar.update(MilestoneTemplateStepsEditing.remove(_, index)))
          )
        )
      ),
      step.htmlElement
    )

  private val addStepBadge: HtmlElement =
    TgBadge(text = "+ Add step", color = TgBadgeColor.Alternative, bordered = true, extraCls = "cursor-pointer").element
      .amend(
        role("button"),
        tabIndex(0),
        onClick --> (_ => addStep()),
        onKeyDown.filter(event => event.key == "Enter" || event.key == " ").preventDefault --> (_ => addStep())
      )

  private val spreadDatesBadge: HtmlElement =
    TgBadge(text = "Spread dates evenly", color = TgBadgeColor.Alternative, bordered = true, extraCls = "cursor-pointer ml-2").element
      .amend(
        role("button"),
        tabIndex(0),
        onClick --> (_ => spreadStepDates()),
        onKeyDown.filter(event => event.key == "Enter" || event.key == " ").preventDefault --> (_ => spreadStepDates())
      )

  private val stepsSection: HtmlElement =
    div(
      cls("mb-4"),
      h4(cls("text-sm font-medium text-heading mb-2"), "Steps"),
      div(
        cls("mb-4"),
        child <-- MilestoneTemplatePicker(
          templatesVar.signal,
          selectedTemplateVar.signal,
          Observer[Option[FindMilestoneTemplateResp]](applyTemplate)
        )
      ),
      child.maybe <-- milestoneStepsVar.signal.map(steps =>
        Option.when(steps.isEmpty)(
          p(
            cls("mb-4 text-sm text-body"),
            s"Without steps, the milestone gets one step, ${DefaultMilestoneStep.Shelf.stepName}, with the dates of the milestone."
          )
        )
      ),
      children <-- milestoneStepsVar.signal.map(_.zipWithIndex.map { case (step, index) => stepCard(step, index) }),
      addStepBadge,
      child <-- milestoneStepsVar.signal.map(steps => if (steps.size > 1) spreadDatesBadge else emptyNode)
    )

  val element = div(
    justifyCenterFlex,
    EventStream.merge(milestoneRange.changes.mapToUnit, workDaysVar.signal.changes.mapToUnit) --> followMilestoneRange,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a milestone", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(
                (formState.now(), milestoneDatesVar.now(), milestoneStepsVar.now().map(_.formState.now()))
              ) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4 max-w-[60ch]")),
              div(
                cls("mb-4 flex flex-col gap-4"),
                dateBadges.element,
                MilestoneSummaryRangeCalendar.element(
                  formState.signal.map(form => form.startDate -> form.dueDate).distinct,
                  workDaysVar.signal,
                  (start, due) => formState.update(_.copy(startDate = start.toString, dueDate = due.toString))
                )
              ),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
              div(
                cls("mb-4"),
                h4(cls("text-sm font-medium text-heading mb-2"), "Additional milestone dates"),
                children <-- milestoneDatesVar.signal.map(dateComponents =>
                  dateComponents.map(
                    _.htmlElement.amend(cls("border border-gray-300 dark:border-gray-600 rounded-lg p-4 mb-4"))
                  )
                ),
                addNewMilestoneDate
              ),
              stepsSection,
              div(cls("flex justify-end gap-2"), TgHtmlButton.createSmall(stepsHaveErrors.changes))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
