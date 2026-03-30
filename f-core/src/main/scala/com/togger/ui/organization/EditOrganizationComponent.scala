package com.togger.ui.organization

import cats.effect.unsafe.IORuntime
import com.togger.components.button.TgHtmlButton
import com.togger.dto.SearchAccountDto
import com.togger.dto.organization.UpdateOrganizationReq
import com.togger.routes.Pages.EditOrganizationPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgTextArea.TgTextAreaSyntax
import com.togger.ui.auth._
import com.togger.ui.project.{FindAccountService, FindAccountsComponent}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

case class EditOrganizationForm(
  id: Long = -1L,
  name: String = "",
  description: String = ""
)

class EditOrganizationComponent(
  organizationId: Long,
  organizationService: OrganizationServiceFe,
  findAccountService: FindAccountService,
  accountService: AccountService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  val formState = Var(EditOrganizationForm(id = organizationId))
  val existingMembers: Var[Seq[SearchAccountDto]] = Var(Seq.empty)

  val findAccountsComponent = new FindAccountsComponent(
    findAccountService = findAccountService,
    accountService = accountService,
    existingAccountsVar = existingMembers,
    excludeUserAccount = true
  )

  spinnerService
    .runWithSpinner(
      organizationService
        .findAllMembers(organizationId)
        .map { membersResp =>
          membersResp.fold(
            error => println(error),
            success => {
              val dto = success.body.get
              existingMembers.set(dto)
            }
          )
        }
        .flatMap { _ =>
          organizationService
            .findById(organizationId)
            .map(
              _.fold(
                error => dom.window.alert("Oops an error: " + error),
                success => {
                  val dto = success.body.get
                  formState.set(
                    EditOrganizationForm(
                      id = dto.id,
                      name = dto.name,
                      description = dto.description
                    )
                  )
                }
              )
            )
        }
    )
    .unsafeRunAndForget()

  val submitter = Observer[EditOrganizationForm] { state =>
    val dto = UpdateOrganizationReq(
      id = state.id,
      name = state.name,
      description = state.description,
      membersToAdd = findAccountsComponent.membersToAdd.map(_.id),
      membersToRemove = findAccountsComponent.membersToRemove.map(_.id)
    )

    spinnerService
      .runWithSpinner(organizationService.update(dto))
      .toastMessageFn("Organization updated successfully")(_ => router.route(EditOrganizationPage(organizationId)))
      .unsafeRunAndForget()
  }

  private val nameInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.name),
    errors = EventStream.empty,
    labelValue = "Organization name",
    tgInputType = TgInputType.Text
  )

  private val descriptionTextArea = formState.tgTextAreaManualErrorSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.description),
    errors = EventStream.empty,
    labelValue = "Organization description"
  )

  val element = div(
    justifyCenterFlex,
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Edit organization", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              descriptionTextArea.htmlElement.amend(cls("mb-4")),
              findAccountsComponent.element.amend(width.px(500)),
              TgHtmlButton.update(EventStream.fromValue(false))
            )
          )
        )
      )
      .element
      .amend(heightAndWidthFitContent, cls("mr-4"))
  )

}
