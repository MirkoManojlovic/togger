package com.togger.ui.organization

import cats.effect.unsafe.IORuntime
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.components.button.TgHtmlButton
import com.togger.dto.SearchAccountDto
import com.togger.dto.organization.{
  CreateOrganizationReq,
  FindOrganizationResp,
  FindOrganizationsAndProjects,
  UpdateOrganizationReq
}
import com.togger.organization.OrganizationEndpoints
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.auth._
import com.togger.ui.emoji.TgEmojiField.TgEmojiFieldSyntax
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.project.{FindAccountService, FindAccountsComponent}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class CreateOrganizationForm(
  name: String = "",
  description: String = "",
  icon: String = "",
  members: Seq[UUID] = Seq.empty
)

trait OrganizationServiceFe {
  def createOrganization(organization: CreateOrganizationReq): TgRespIO[FindOrganizationResp]

  def findAll(): TgRespIO[Seq[FindOrganizationResp]]

  def findById(id: UUID): TgRespIO[FindOrganizationResp]

  def update(dto: UpdateOrganizationReq): TgRespIO[FindOrganizationResp]

  def findByAccountId(accountId: UUID): TgRespIO[Seq[FindOrganizationResp]]

  def findOrgAndProjectsByAccountId(accountId: UUID): TgRespIO[Seq[FindOrganizationsAndProjects]]

  def findAllMembers(organizationId: UUID): TgRespIO[Seq[SearchAccountDto]]

}

class OrganizationServiceFeImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends OrganizationServiceFe {
  val organizationEndpoints = new OrganizationEndpoints(securedEndpoint)

  override def createOrganization(req: CreateOrganizationReq): TgRespIO[FindOrganizationResp] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.saveEndpoint)(req)

  override def findAll(): TgRespIO[Seq[FindOrganizationResp]] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.findAllEndpoint)()

  override def findByAccountId(accountId: UUID): TgRespIO[Seq[FindOrganizationResp]] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.findByAccountIdEndpoint)(accountId)

  override def findById(id: UUID): TgRespIO[FindOrganizationResp] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.findByIdEndpoint)(id)

  override def update(dto: UpdateOrganizationReq): TgRespIO[FindOrganizationResp] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.updateEndpoint)(dto)

  override def findAllMembers(organizationId: UUID): TgRespIO[Seq[SearchAccountDto]] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.findAllMembersEndpoint)(organizationId)

  override def findOrgAndProjectsByAccountId(accountId: UUID): TgRespIO[Seq[FindOrganizationsAndProjects]] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.findOrgAndProjectsByAccountIdEndpoint)(accountId)
}

class CreateOrganizationComponent(
  findAccountService: FindAccountService,
  organizationService: OrganizationServiceFe,
  accountService: AccountService,
  spinnerService: SpinnerService,
  localStorage: LocalStorage,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateOrganizationForm())

  val findAccountsComponent = new FindAccountsComponent(
    findAccountService = findAccountService,
    accountService = accountService,
    excludeUserAccount = true
  )

  lazy val createSubmitter = Observer[CreateOrganizationForm] { state =>
    val request = CreateOrganizationReq(
      name = state.name,
      description = state.description,
      icon = state.icon,
      membersToAdd = findAccountsComponent.membersToAdd.map(_.id)
    )

    spinnerService
      .runWithSpinner(organizationService.createOrganization(request))
      .toastMessageJustMessage("Organization created successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Organization name",
    tgInputType = TgInputType.Text
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Organization description",
    minWidthRem = 0
  )

  private val iconInput = formState.tgEmojiPicker(
    inputAndUpdater = selectorAndUpdaterMacro(_.icon),
    errors = EventStream.empty,
    labelValue = "Organization icon",
    localStorage = localStorage
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create an organization", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> createSubmitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              iconInput.htmlElement.amend(cls("mb-4")),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
              findAccountsComponent.element.amend(width.px(500)),
              TgHtmlButton.create(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
