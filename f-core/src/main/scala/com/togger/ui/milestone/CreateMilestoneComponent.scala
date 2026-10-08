package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.{DateRangeValidation, PageRequest, PaginatedResponse}
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{
  CreateMilestoneDateReq,
  CreateMilestoneReq,
  FindMilestoneResp,
  MilestoneFilter,
  UpdateMilestoneReq
}
import com.togger.milestone.MilestoneEndpoints
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.project.ProjectService
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.time.{DayOfWeek, LocalDate}
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
  spinnerService: SpinnerService,
  dateRangeValidation: DateRangeValidation,
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
        .map(_.fold(error => println(error), success => workDaysVar.set(success.body.get.workDays.map(DayOfWeek.of))))
    )
    .unsafeRunAndForget()

  private val milestoneStartDate: Signal[String] = formState.signal.map(_.startDate)

  val firstMilestoneDate = new CreateMilestoneDateComponent(milestoneStartDate)
  val milestoneDatesVar = Var(Seq(firstMilestoneDate))

  lazy val addNewMilestoneDate =
    TgHtmlButton
      .extraSmall("+")
      .amend(
        typ("button"),
        cls("mb-4"),
        onClick --> (_ =>
          milestoneDatesVar.update(state => state :+ new CreateMilestoneDateComponent(milestoneStartDate))
        )
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

  lazy val submitter = Observer[(CreateMilestoneForm, Seq[CreateMilestoneDateComponent])] {
    case (form, dateComponents) =>
      val dto = CreateMilestoneReq(
        projectId = form.projectId,
        name = form.name,
        description = form.description,
        startDate = LocalDate.parse(form.startDate),
        dueDate = LocalDate.parse(form.dueDate)
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
    minWidthRem = 0
  )

  private val startDateErrors: EventStream[Seq[String]] = formState.signal.changes.map { form =>
    if (form.startDate.nonEmpty && dateRangeValidation.isWeekend(LocalDate.parse(form.startDate), workDaysVar.now()))
      Seq("Start date must be one of this project's configured work days")
    else Seq.empty
  }

  private val dueDateErrors: EventStream[Seq[String]] = formState.signal.changes.map { form =>
    val orderError =
      if (
        form.startDate.nonEmpty && form.dueDate.nonEmpty &&
        LocalDate.parse(form.dueDate).isBefore(LocalDate.parse(form.startDate))
      )
        Seq("Due date must be on or after start date")
      else Seq.empty
    val weekendError =
      if (form.dueDate.nonEmpty && dateRangeValidation.isWeekend(LocalDate.parse(form.dueDate), workDaysVar.now()))
        Seq("Due date must be one of this project's configured work days")
      else Seq.empty
    orderError ++ weekendError
  }

  private val startDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.startDate),
    errors = startDateErrors,
    labelValue = "Start date",
    tgInputType = TgInputType.Date
  )

  private val dueDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.dueDate),
    errors = dueDateErrors,
    labelValue = "Due date",
    tgInputType = TgInputType.Date
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a milestone", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now() -> milestoneDatesVar.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
              startDateInput.htmlElement.amend(cls("mb-4")),
              dueDateInput.htmlElement.amend(cls("mb-4")),
              h5(cls("mb-2"), "Additional Milestone dates"),
              addNewMilestoneDate,
              child <-- milestoneDatesVar.signal.map(dateComponents =>
                div(
                  dateComponents.map(
                    _.htmlElement.amend(cls("border border-gray-300 dark:border-gray-600 rounded-lg p-4 mb-4"))
                  )
                )
              ),
              TgHtmlButton.create(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}

class MilestoneFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val milestoneEndpoints = new MilestoneEndpoints(securedEndpoint)

  def createMilestone(dto: CreateMilestoneReq): TgRespIO[FindMilestoneResp] =
    protectedBackendClient.protectedHttpCall(milestoneEndpoints.saveEndpoint)(dto)

  def findMilestoneById(id: UUID): TgRespIO[FindMilestoneResp] =
    protectedBackendClient.protectedHttpCall(milestoneEndpoints.findByIdEndpoint)(id)

  def updateMilestone(dto: UpdateMilestoneReq): TgRespIO[FindMilestoneResp] =
    protectedBackendClient.protectedHttpCall(milestoneEndpoints.updateEndpoint)(dto)

  def findByProjectId(
    projectId: UUID,
    filter: MilestoneFilter,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneResp]] =
    protectedBackendClient.protectedHttpCall(milestoneEndpoints.findByProjectIdEndpoint)((projectId, filter, page))

  /** Every milestone of the project, for the pickers, filters and charts offering all of them; see [[PaginatedLoader]]
    * for the bound.
    */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[FindMilestoneResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, MilestoneFilter.empty, _))
}
