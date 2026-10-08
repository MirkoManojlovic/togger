package com.togger.ui.projectlabel

import cats.effect.unsafe.IORuntime
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.{PageRequest, PaginatedResponse, TgClock}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.projectlabel.ProjectLabelEndpoints
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID
import scala.collection.immutable.ListMap

case class CreateProjectLabelForm(
  name: String = "",
  description: String = "",
  categorySelected: String = ""
)

class CreateProjectLabelComponent(
  projectLabelService: ProjectLabelFeService,
  projectLabelCategoryService: ProjectLabelCategoryFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateProjectLabelForm())
  val categoriesVar = Var(Seq.empty[FindProjectLabelCategoryResp])

  spinnerService
    .runWithSpinner(
      // The category picker offers every category, read page by page (bounded by PaginatedLoader).
      projectLabelCategoryService
        .findEvery()
        .map(response => response.map(success => categoriesVar.set(success.body.get)))
    )
    .unsafeRunAndForget()

  lazy val submitter = Observer[CreateProjectLabelForm] { form =>
    val dto = CreateProjectLabelReq(
      name = form.name,
      description = form.description,
      categoryId = UUID.fromString(form.categorySelected)
    )
    spinnerService
      .runWithSpinner(projectLabelService.createProjectLabel(dto))
      .toastMessageJustMessage("Project label created successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Name",
    tgInputType = TgInputType.Text
  )

  private val descriptionInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Description",
    tgInputType = TgInputType.Text
  )

  def renderElement(categories: Seq[FindProjectLabelCategoryResp]) = {
    val categorySelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.categorySelected),
      labelValue = "Category",
      valuesAndDisplays = ListMap.from(categories.sortBy(_.name).map(c => c.id.toString -> c.name))
    )

    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Create a project label", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              form(
                onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
                nameInput.htmlElement.amend(cls("mb-4")),
                descriptionInput.htmlElement.amend(cls("mb-4")),
                categorySelect.htmlElement.amend(cls("mb-4")),
                TgHtmlButton.create(EventStream.fromValue(false))
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    )
  }

  val element = div(
    child <-- categoriesVar.signal.map(categories => renderElement(categories))
  )

}

class ProjectLabelFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val projectLabelEndpoints = new ProjectLabelEndpoints(securedEndpoint)

  def createProjectLabel(dto: CreateProjectLabelReq): TgRespIO[CreateProjectLabelResp] =
    protectedBackendClient.protectedHttpCall(projectLabelEndpoints.saveEndpoint)(dto)

  def updateProjectLabel(dto: UpdateProjectLabelResp): TgRespIO[FindProjectLabelResp] =
    protectedBackendClient.protectedHttpCall(projectLabelEndpoints.updateEndpoint)(dto)

  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindProjectLabelResp]] =
    protectedBackendClient.protectedHttpCall(projectLabelEndpoints.findAllEndpoint)(page)

  /** Every project label, for the pickers offering all of them; see [[PaginatedLoader]] for the bound. */
  def findEvery(): TgRespIO[Seq[FindProjectLabelResp]] =
    PaginatedLoader.loadAll(findAll)

  def findProjectLabelById(id: UUID): TgRespIO[FindProjectLabelResp] =
    protectedBackendClient.protectedHttpCall(projectLabelEndpoints.findByIdEndpoint)(id)

  def deleteProjectLabelById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(projectLabelEndpoints.deleteByIdEndpoint)(id)
}
