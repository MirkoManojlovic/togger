package com.togger.ui.project

import cats.effect.unsafe.IORuntime
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.dto.organization.FindOrganizationResp
import com.togger.project.ProjectEndpoints
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.auth._
import com.togger.ui.navbar.SidebarRefresh
import com.togger.ui.components.TgSelectDropdownMultiSelect.TgSelectDropdownMultiSelectSyntax
import com.togger.ui.components.pagination.PaginatedLoader
import com.togger.ui.components.{DropdownMeta, TgSelectDropdownOption}
import com.togger.ui.emoji.TgEmojiField.TgEmojiFieldSyntax
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.organization.OrganizationServiceFe
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.projectlabel.ProjectLabelFeService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class CreateProjectForm(
  organizationId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  icon: String = "",
  members: Seq[UUID] = Seq.empty,
  projectLabelSelected: Set[String] = Set.empty
)

trait ProjectService {
  def createProject(dto: CreateProjectReq): TgRespIO[FindProjectResp]

  def updateProject(dto: UpdateProjectReq): TgRespIO[FindProjectResp]

  def findByOrganizationId(organizationId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindProjectResp]]

  /** Every project of the organization the account is a member of, for the status board; see [[PaginatedLoader]] for
    * the bound.
    */
  def findAllByOrganizationId(organizationId: UUID): TgRespIO[Seq[FindProjectResp]] =
    PaginatedLoader.loadAll(findByOrganizationId(organizationId, _))

  def findById(id: UUID): TgRespIO[FindProjectResp]

  def findMembers(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[SearchAccountDto]]

  /** Every member of the project, for the assignee pickers and the member editor; see [[PaginatedLoader]] for the
    * bound.
    */
  def findAllMembers(projectId: UUID): TgRespIO[Seq[SearchAccountDto]] =
    PaginatedLoader.loadAll(findMembers(projectId, _))

  def searchByLabels(projectLabelIds: Seq[UUID], page: PageRequest): TgRespIO[PaginatedResponse[FindProjectResp]]

}

class ProjectServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends ProjectService {
  val projectEndpoints = new ProjectEndpoints(securedEndpoint)

  override def createProject(dto: CreateProjectReq): TgRespIO[FindProjectResp] =
    protectedBackendClient.protectedHttpCall(projectEndpoints.saveEndpoint)(dto)

  override def findByOrganizationId(
    organizationId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindProjectResp]] =
    protectedBackendClient.protectedHttpCall(projectEndpoints.findByOrganizationIdAndAccountIdEndpoint)(
      (organizationId, page)
    )

  override def findById(id: UUID): TgRespIO[FindProjectResp] =
    protectedBackendClient.protectedHttpCall(projectEndpoints.findByIdEndpoint)(id)

  override def updateProject(dto: UpdateProjectReq): TgRespIO[FindProjectResp] =
    protectedBackendClient.protectedHttpCall(projectEndpoints.updateEndpoint)(dto)

  override def findMembers(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[SearchAccountDto]] =
    protectedBackendClient.protectedHttpCall(projectEndpoints.findAllMembersEndpoint)((projectId, page))

  override def searchByLabels(
    projectLabelIds: Seq[UUID],
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindProjectResp]] =
    protectedBackendClient.protectedHttpCall(projectEndpoints.searchByLabelsEndpoint)(
      (SearchProjectsByLabelsReq(projectLabelIds), page)
    )

}

class CreateProjectComponent(
  findAccountService: FindAccountService,
  projectService: ProjectService,
  projectLabelService: ProjectLabelFeService,
  organizationServiceFe: OrganizationServiceFe,
  accountService: AccountService,
  spinnerService: SpinnerService,
  localStorage: LocalStorage,
  organizationId: UUID,
  sidebarRefresh: SidebarRefresh,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateProjectForm(organizationId = organizationId))
  val organizationsVar = Var(Seq.empty[FindOrganizationResp])
  val projectLabelsVar = Var(Seq.empty[FindProjectLabelResp])
  val findAccountsComponent = new FindAccountsComponent(
    findAccountService = findAccountService,
    accountService = accountService,
    excludeUserAccount = true
  )

  spinnerService
    .runWithSpinner {
      // The organization picker offers every organization of the account, read page by page.
      val orgCall = organizationServiceFe
        .findAllByAccountId(accountService.get.get.id)
        .map(
          _.fold(
            e => println(e),
            success => {
              organizationsVar.set(success.body.get)
            }
          )
        )

      // The label picker offers every project label, read page by page (bounded by PaginatedLoader).
      val projectLabelCall = projectLabelService
        .findEvery()
        .map(_.map(success => projectLabelsVar.set(success.body.get)))

      orgCall.flatMap(_ => projectLabelCall)
    }
    .unsafeRunAndForget()

  lazy val createSubmitter = Observer[CreateProjectForm] { state =>
    val request = CreateProjectReq(
      organizationId = state.organizationId,
      name = state.name,
      description = state.description,
      icon = state.icon,
      membersToAdd = findAccountsComponent.membersToAdd.map(_.id),
      projectLabelIds = state.projectLabelSelected.map(UUID.fromString).toSeq
    )

    spinnerService
      .runWithSpinner(projectService.createProject(request))
      .toastMessageCodeBlock("Project created successfully")(sidebarRefresh.request())
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Project name",
    tgInputType = TgInputType.Text
  )

  private val descriptionEditor = formState.wysiwygSplitEditorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    labelValue = "Project description",
    minWidthRem = 0
  )

  private val iconInput = formState.tgEmojiPicker(
    inputAndUpdater = selectorAndUpdaterMacro(_.icon),
    errors = EventStream.empty,
    labelValue = "Project icon",
    localStorage = localStorage
  )

  private def projectLabelSelectElement(projectLabels: Seq[FindProjectLabelResp]): HtmlElement = {
    val options = projectLabels
      .sortBy(_.name)
      .map(ts =>
        TgSelectDropdownOption(
          value = ts.id.toString,
          meta = DropdownMeta(label = ts.name, description = ""),
          render = () => span(ts.name)
        )
      )
    formState
      .tgSelectDropdownMultSelectManualError(
        inputAndUpdater = selectorAndUpdaterMacro(_.projectLabelSelected),
        labelValue = "Select project labels",
        options = options
      )
      .htmlElement
  }

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a project", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> createSubmitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              iconInput.htmlElement.amend(cls("mb-4")),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
              child <-- projectLabelsVar.signal.map(projectLabelSelectElement).map(_.amend(cls("mb-4"))),
              findAccountsComponent.element.amend(width.px(500)),
              div(cls("flex justify-end"), TgHtmlButton.createSmall(EventStream.fromValue(false)))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
