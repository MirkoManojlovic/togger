package com.togger.ui.project

import cats.effect.unsafe.IORuntime
import com.togger.components.button.TgHtmlButton
import com.togger.dto._
import com.togger.dto.organization.FindOrganizationResp
import com.togger.routes.Pages.EditProjectPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.ui.auth._
import com.togger.ui.organization.OrganizationServiceFe
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID

case class EditProjectForm(
  projectId: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  organizationId: UUID = UUID.randomUUID()
)

class EditProjectComponent(
  findAccountService: FindAccountService,
  projectService: ProjectService,
  organizationServiceFe: OrganizationServiceFe,
  accountService: AccountService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  projectId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditProjectForm())
  val organizationsVar = Var(Seq.empty[FindOrganizationResp])
  val existingMembers: Var[Seq[SearchAccountDto]] = Var(Seq.empty)

  val findAccountsComponent = new FindAccountsComponent(
    findAccountService = findAccountService,
    accountService = accountService,
    existingAccountsVar = existingMembers,
    excludeUserAccount = true
  )

  spinnerService
    .runWithSpinner {
      val membersCall = projectService.findAllMembers(projectId).map { membersResp =>
        membersResp.fold(
          error => println(error),
          success => {
            val dto = success.body.get
            existingMembers.set(dto)
          }
        )
      }
      val orgCall = organizationServiceFe
        .findByAccountId(accountService.get.get.id)
        .map(
          _.fold(
            e => println(e),
            success => organizationsVar.set(success.body.get)
          )
        )

      val projectOrg = projectService
        .findById(projectId)
        .map(
          _.fold(
            e => println(e),
            success => {
              val dto = success.body.get
              formState.set(
                EditProjectForm(
                  projectId = dto.id,
                  name = dto.name,
                  description = dto.description,
                  organizationId = dto.organizationId
                )
              )
            }
          )
        )

      membersCall.flatMap(_ => orgCall.flatMap(_ => projectOrg))
    }
    .unsafeRunAndForget()

  lazy val updateSubmitter = Observer[EditProjectForm] { state =>
    val request = UpdateProjectReq(
      id = state.projectId,
      organizationId = state.organizationId,
      name = state.name,
      description = state.description,
      membersToAdd = findAccountsComponent.membersToAdd.map(_.id),
      membersToRemove = findAccountsComponent.membersToRemove.map(_.id)
    )

    spinnerService
      .runWithSpinner(projectService.updateProject(request))
      .toastMessageFn("Project updated successfully")(_ => router.route(EditProjectPage(projectId)))
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

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit a project", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> updateSubmitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              descriptionTextArea.htmlElement.amend(cls("mb-4")),
              findAccountsComponent.element.amend(width.px(500)),
              TgHtmlButton.update(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent)
  )

}
