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
import com.togger.techstack.TechStackEndpoints
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth.{AccountService, TgCard, TgInputType}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID
import scala.collection.immutable.ListMap

case class CreateTechStackForm(
  name: String = "",
  description: String = "",
  categorySelected: String = ""
)

class CreateTechStackComponent(
  techStackService: TechStackFeService,
  techStackCategoryService: TechStackCategoryFeService,
  spinnerService: SpinnerService,
  tgClock: TgClock,
  accountService: AccountService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateTechStackForm())
  val categoriesVar = Var(Seq.empty[FindTechStackCategoryResp])

  spinnerService
    .runWithSpinner(
      techStackCategoryService
        .findAll()
        .map(response => response.map(success => categoriesVar.set(success.body.get)))
    )
    .unsafeRunAndForget()

  lazy val submitter = Observer[CreateTechStackForm] { form =>
    val dto = CreateTechStackReq(
      name = form.name,
      description = form.description,
      categoryId = UUID.fromString(form.categorySelected)
    )
    spinnerService
      .runWithSpinner(techStackService.createTechStack(dto))
      .toastMessageJustMessage("Tech stack created successfully")
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

  def renderElement(categories: Seq[FindTechStackCategoryResp]) = {
    val categorySelect = formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.categorySelected),
      labelValue = "Category",
      valuesAndDisplays = ListMap.from(categories.sortBy(_.name).map(c => c.id.toString -> c.name))
    )

    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Create a tech stack", TgCard.CardHeaderSize.H3),
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

class TechStackFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val techStackEndpoints = new TechStackEndpoints(securedEndpoint)

  def createTechStack(dto: CreateTechStackReq): TgRespIO[CreateTechStackResp] =
    protectedBackendClient.protectedHttpCall(techStackEndpoints.saveEndpoint)(dto)

  def updateTechStack(dto: UpdateTechStackResp): TgRespIO[FindTechStackResp] =
    protectedBackendClient.protectedHttpCall(techStackEndpoints.updateEndpoint)(dto)

  def findAll(): TgRespIO[Seq[FindTechStackResp]] =
    protectedBackendClient.protectedHttpCall(techStackEndpoints.findAllEndpoint)()

  def findTechStackById(id: UUID): TgRespIO[FindTechStackResp] =
    protectedBackendClient.protectedHttpCall(techStackEndpoints.findByIdEndpoint)(id)

  def deleteTechStackById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(techStackEndpoints.deleteByIdEndpoint)(id)
}
