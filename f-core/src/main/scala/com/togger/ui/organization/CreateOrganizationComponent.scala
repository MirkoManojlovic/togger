package com.togger.ui.organization

import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.components.button.TgHtmlButton
import com.togger.dto.SearchAccountDto
import com.togger.dto.organization.{CreateOrganizationReq, FindOrganizationResp, UpdateOrganizationReq}
import com.togger.organization.OrganizationEndpoints
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth._
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.emoji.TgEmojiField.TgEmojiFieldSyntax
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.navbar.SidebarRefresh
import com.togger.ui.project.{FindAccountService, FindAccountsComponent}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
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

  /** Admin only: the organizations of every tenant. */
  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindOrganizationResp]]

  def findById(id: UUID): TgRespIO[FindOrganizationResp]

  def deleteOrganization(id: UUID): TgRespIO[TgEmptyBody]

  def update(dto: UpdateOrganizationReq): TgRespIO[FindOrganizationResp]

  def findByAccountId(accountId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindOrganizationResp]]

  /** Every organization of the account, for pickers and the sidebar; see [[PaginatedLoader]] for the bound. */
  def findAllByAccountId(accountId: UUID): TgRespIO[Seq[FindOrganizationResp]] =
    PaginatedLoader.loadAll(findByAccountId(accountId, _))

  def findMembers(organizationId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[SearchAccountDto]]

  /** Every member of the organization, for the member editor; see [[PaginatedLoader]] for the bound. */
  def findAllMembers(organizationId: UUID): TgRespIO[Seq[SearchAccountDto]] =
    PaginatedLoader.loadAll(findMembers(organizationId, _))

}

class OrganizationServiceFeImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends OrganizationServiceFe {
  val organizationEndpoints = new OrganizationEndpoints(securedEndpoint)

  override def createOrganization(req: CreateOrganizationReq): TgRespIO[FindOrganizationResp] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.saveEndpoint)(req)

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindOrganizationResp]] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.findAllEndpoint)(page)

  override def findByAccountId(
    accountId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindOrganizationResp]] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.findByAccountIdEndpoint)((accountId, page))

  override def findById(id: UUID): TgRespIO[FindOrganizationResp] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.findByIdEndpoint)(id)

  override def deleteOrganization(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.deleteByIdEndpoint)(id)

  override def update(dto: UpdateOrganizationReq): TgRespIO[FindOrganizationResp] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.updateEndpoint)(dto)

  override def findMembers(
    organizationId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[SearchAccountDto]] =
    protectedBackendClient.protectedHttpCall(organizationEndpoints.findAllMembersEndpoint)((organizationId, page))
}

class CreateOrganizationComponent(
  findAccountService: FindAccountService,
  organizationService: OrganizationServiceFe,
  accountService: AccountService,
  spinnerService: SpinnerService,
  localStorage: LocalStorage,
  sidebarRefresh: SidebarRefresh,
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
      .toastMessageCodeBlock("Organization created successfully")(sidebarRefresh.request())
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
    placeholderValue = "Describe the description"
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
              nameInput.htmlElement.amend(cls("mb-4 max-w-[60ch]")),
              iconInput.htmlElement.amend(cls("mb-4")),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
//              findAccountsComponent.element.amend(width.px(500)),
              div(cls("flex justify-end"), TgHtmlButton.createSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
