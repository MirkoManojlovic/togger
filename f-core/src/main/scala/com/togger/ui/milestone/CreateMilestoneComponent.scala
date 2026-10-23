package com.togger.ui.milestone

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.raquo.laminar.api.L._
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.{PageRequest, PaginatedResponse, TgClock}
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
import com.togger.style.TgCommonStyleJs.justifyCenterFlex
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.auth.{TgCard, TgInputType}
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.project.ProjectService
import com.togger.ui.task.bulk.BulkTaskDefaults
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
        .map(_.fold(error => println(error), success => applyProjectWorkDays(success.body.get.workDays.map(DayOfWeek.of))))
    )
    .unsafeRunAndForget()

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

  val firstMilestoneDate = new CreateMilestoneDateComponent(milestoneStartDate, tgClock)
  val milestoneDatesVar = Var(Seq(firstMilestoneDate))

  lazy val addNewMilestoneDate =
    TgHtmlButton
      .extraSmall("+")
      .amend(
        typ("button"),
        cls("mb-4"),
        onClick --> (_ =>
          milestoneDatesVar.update(state => state :+ new CreateMilestoneDateComponent(milestoneStartDate, tgClock))
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
              dateBadges.element.amend(cls("mb-4")),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
              h5(cls("text-heading mb-2"), "Additional Milestone dates"),
              addNewMilestoneDate,
              child <-- milestoneDatesVar.signal.map(dateComponents =>
                div(
                  dateComponents.map(
                    _.htmlElement.amend(cls("border border-gray-300 dark:border-gray-600 rounded-lg p-4 mb-4"))
                  )
                )
              ),
              div(cls("flex justify-end"), TgHtmlButton.createSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
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
