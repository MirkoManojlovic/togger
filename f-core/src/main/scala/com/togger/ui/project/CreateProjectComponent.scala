package com.togger.ui.project

import cats.effect.unsafe.IORuntime
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.dto.organization.FindOrganizationResp
import com.togger.project.ProjectEndpoints
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.ui.auth._
import com.togger.ui.components.{MultiSelectItem, TableFilterComponent}
import com.togger.ui.emoji.TgEmojiField.TgEmojiFieldSyntax
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.organization.OrganizationServiceFe
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.techstack.TechStackFeService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class CreateProjectForm(
  organizationId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  icon: String = "",
  members: Seq[UUID] = Seq.empty
)

trait ProjectService {
  def createProject(dto: CreateProjectReq): TgRespIO[FindProjectResp]

  def updateProject(dto: UpdateProjectReq): TgRespIO[FindProjectResp]

  def findByOrganizationId(organizationId: UUID): TgRespIO[Seq[FindProjectResp]]

  def findById(id: UUID): TgRespIO[FindProjectResp]

  def findAllMembers(projectId: UUID): TgRespIO[Seq[SearchAccountDto]]

}

class ProjectServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends ProjectService {
  val projectEndpoints = new ProjectEndpoints(securedEndpoint)

  override def createProject(dto: CreateProjectReq): TgRespIO[FindProjectResp] =
    protectedBackendClient.protectedHttpCall(projectEndpoints.saveEndpoint)(dto)

  override def findByOrganizationId(organizationId: UUID): TgRespIO[Seq[FindProjectResp]] =
    protectedBackendClient.protectedHttpCall(projectEndpoints.findByOrganizationIdAndAccountIdEndpoint)(organizationId)

  override def findById(id: UUID): TgRespIO[FindProjectResp] =
    protectedBackendClient.protectedHttpCall(projectEndpoints.findByIdEndpoint)(id)

  override def updateProject(dto: UpdateProjectReq): TgRespIO[FindProjectResp] =
    protectedBackendClient.protectedHttpCall(projectEndpoints.updateEndpoint)(dto)

  override def findAllMembers(projectId: UUID): TgRespIO[Seq[SearchAccountDto]] =
    protectedBackendClient.protectedHttpCall(projectEndpoints.findAllMembersEndpoint)(projectId)

}

class CreateProjectComponent(
  findAccountService: FindAccountService,
  projectService: ProjectService,
  techStackService: TechStackFeService,
  organizationServiceFe: OrganizationServiceFe,
  accountService: AccountService,
  spinnerService: SpinnerService,
  localStorage: LocalStorage,
  organizationId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(CreateProjectForm(organizationId = organizationId))
  val organizationsVar = Var(Seq.empty[FindOrganizationResp])
  val techStacksVar = Var(Seq.empty[FindTechStackResp])
  val findAccountsComponent = new FindAccountsComponent(
    findAccountService = findAccountService,
    accountService = accountService,
    excludeUserAccount = true
  )

  private val techStackPicker = new TableFilterComponent(
    techStacksVar.signal.map(_.sortBy(_.name).map(ts => MultiSelectItem(id = ts.id.toString, name = ts.name, isSelected = false))),
    label = "Select tech stacks"
  )

  spinnerService
    .runWithSpinner {
      val orgCall = organizationServiceFe
        .findByAccountId(accountService.get.get.id)
        .map(
          _.fold(
            e => println(e),
            success => {
              organizationsVar.set(success.body.get)
            }
          )
        )

      val techStackCall = techStackService
        .findAll()
        .map(_.map(success => techStacksVar.set(success.body.get)))

      orgCall.flatMap(_ => techStackCall)
    }
    .unsafeRunAndForget()

  lazy val createSubmitter = Observer[(CreateProjectForm, Set[String])] { case (state, selectedTechStackIds) =>
    val request = CreateProjectReq(
      organizationId = state.organizationId,
      name = state.name,
      description = state.description,
      icon = state.icon,
      membersToAdd = findAccountsComponent.membersToAdd.map(_.id),
      techStackIds = selectedTechStackIds.map(UUID.fromString).toSeq
    )

    spinnerService
      .runWithSpinner(projectService.createProject(request))
      .toastMessageJustMessage("Project created successfully")
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Project name",
    tgInputType = TgInputType.Text
  )

  private val descriptionTextArea = formState.tgTextAreaManualErrorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Project description"
  )

  private val iconInput = formState.tgEmojiPicker(
    inputAndUpdater = selectorAndUpdaterMacro(_.icon),
    errors = EventStream.empty,
    labelValue = "Project icon",
    localStorage = localStorage
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Create a project", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault
                .mapTo(formState.now())
                .compose(_.withCurrentValueOf(techStackPicker.selectedIds)) --> createSubmitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              iconInput.htmlElement.amend(cls("mb-4")),
              descriptionTextArea.htmlElement.amend(cls("mb-4")),
              techStackPicker.element.amend(cls("mb-4")),
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
