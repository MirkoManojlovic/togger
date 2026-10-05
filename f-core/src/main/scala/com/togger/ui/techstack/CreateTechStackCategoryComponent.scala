package com.togger.ui.techstack

import cats.effect.unsafe.IORuntime
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgClock
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.techstack.TechStackCategoryEndpoints
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class CreateTechStackCategoryForm(name: String = "")

class CreateTechStackCategoryComponent(
  techStackCategoryService: TechStackCategoryFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateTechStackCategoryForm())

  lazy val submitter = Observer[CreateTechStackCategoryForm] { form =>
    val dto = CreateTechStackCategoryReq(name = form.name)
    spinnerService
      .runWithSpinner(techStackCategoryService.createTechStackCategory(dto))
      .toastMessageJustMessage("Tech stack category created successfully")
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
        header = TgCard.CardHeader.plainText("Create a tech stack category", TgCard.CardHeaderSize.H3),
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

class TechStackCategoryFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val techStackCategoryEndpoints = new TechStackCategoryEndpoints(securedEndpoint)

  def createTechStackCategory(dto: CreateTechStackCategoryReq): TgRespIO[CreateTechStackCategoryResp] =
    protectedBackendClient.protectedHttpCall(techStackCategoryEndpoints.saveEndpoint)(dto)

  def updateTechStackCategory(dto: UpdateTechStackCategoryResp): TgRespIO[FindTechStackCategoryResp] =
    protectedBackendClient.protectedHttpCall(techStackCategoryEndpoints.updateEndpoint)(dto)

  def findAll(): TgRespIO[Seq[FindTechStackCategoryResp]] =
    protectedBackendClient.protectedHttpCall(techStackCategoryEndpoints.findAllEndpoint)()

  def findTechStackCategoryById(id: UUID): TgRespIO[FindTechStackCategoryResp] =
    protectedBackendClient.protectedHttpCall(techStackCategoryEndpoints.findByIdEndpoint)(id)

  def deleteTechStackCategoryById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(techStackCategoryEndpoints.deleteByIdEndpoint)(id)
}
