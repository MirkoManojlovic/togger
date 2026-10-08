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
import com.togger.projectlabel.ProjectLabelCategoryEndpoints
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class CreateProjectLabelCategoryForm(name: String = "")

class CreateProjectLabelCategoryComponent(
  projectLabelCategoryService: ProjectLabelCategoryFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateProjectLabelCategoryForm())

  lazy val submitter = Observer[CreateProjectLabelCategoryForm] { form =>
    val dto = CreateProjectLabelCategoryReq(name = form.name)
    spinnerService
      .runWithSpinner(projectLabelCategoryService.createProjectLabelCategory(dto))
      .toastMessageJustMessage("Project label category created successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Name",
    tgInputType = TgInputType.Text
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a project label category", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              TgHtmlButton.create(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}

class ProjectLabelCategoryFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val projectLabelCategoryEndpoints = new ProjectLabelCategoryEndpoints(securedEndpoint)

  def createProjectLabelCategory(dto: CreateProjectLabelCategoryReq): TgRespIO[CreateProjectLabelCategoryResp] =
    protectedBackendClient.protectedHttpCall(projectLabelCategoryEndpoints.saveEndpoint)(dto)

  def updateProjectLabelCategory(dto: UpdateProjectLabelCategoryResp): TgRespIO[FindProjectLabelCategoryResp] =
    protectedBackendClient.protectedHttpCall(projectLabelCategoryEndpoints.updateEndpoint)(dto)

  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindProjectLabelCategoryResp]] =
    protectedBackendClient.protectedHttpCall(projectLabelCategoryEndpoints.findAllEndpoint)(page)

  /** Every project label category, for the pickers and name lookups needing all of them; see [[PaginatedLoader]] for
    * the bound.
    */
  def findEvery(): TgRespIO[Seq[FindProjectLabelCategoryResp]] =
    PaginatedLoader.loadAll(findAll)

  def findProjectLabelCategoryById(id: UUID): TgRespIO[FindProjectLabelCategoryResp] =
    protectedBackendClient.protectedHttpCall(projectLabelCategoryEndpoints.findByIdEndpoint)(id)

  def deleteProjectLabelCategoryById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(projectLabelCategoryEndpoints.deleteByIdEndpoint)(id)
}
