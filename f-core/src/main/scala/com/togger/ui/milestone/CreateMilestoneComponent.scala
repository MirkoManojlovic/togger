package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{CreateMilestoneDateReq, CreateMilestoneReq, FindMilestoneResp, UpdateMilestoneReq}
import com.togger.milestone.MilestoneEndpoints
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.time.LocalDate
import java.util.UUID

case class CreateMilestoneForm(
  projectId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  startDate: String = "",
  estimatedEndDate: String = ""
)

class CreateMilestoneComponent(
  milestoneService: MilestoneFeService,
  milestoneDateService: MilestoneDateFeService,
  spinnerService: SpinnerService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateMilestoneForm(projectId = projectId))

  val firstMilestoneDate = new CreateMilestoneDateComponent()
  val milestoneDatesVar = Var(Seq(firstMilestoneDate))

  lazy val addNewMilestoneDate =
    TgHtmlButton
      .extraSmall("+")
      .amend(
        typ("button"),
        cls("mb-4"),
        onClick --> (_ => milestoneDatesVar.update(state => state :+ new CreateMilestoneDateComponent()))
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
        estimatedEndDate = LocalDate.parse(form.estimatedEndDate)
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

  private val descriptionTextArea = formState.tgTextAreaManualErrorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Milestone description"
  )

  private val estimatedEndDateErrors: EventStream[Seq[String]] = formState.signal.changes.map { form =>
    if (
      form.startDate.nonEmpty && form.estimatedEndDate.nonEmpty &&
      LocalDate.parse(form.estimatedEndDate).isBefore(LocalDate.parse(form.startDate))
    )
      Seq("Estimated end date must be on or after start date")
    else Seq.empty
  }

  private val startDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.startDate),
    errors = EventStream.empty,
    labelValue = "Start date",
    tgInputType = TgInputType.Date
  )

  private val estimatedEndDateInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.estimatedEndDate),
    errors = estimatedEndDateErrors,
    labelValue = "Estimated end date",
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
              descriptionTextArea.htmlElement.amend(cls("mb-4")),
              startDateInput.htmlElement.amend(cls("mb-4")),
              estimatedEndDateInput.htmlElement.amend(cls("mb-4")),
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

  def findByProjectId(projectId: UUID): TgRespIO[Seq[FindMilestoneResp]] =
    protectedBackendClient.protectedHttpCall(milestoneEndpoints.findByProjectIdEndpoint)(projectId)
}
