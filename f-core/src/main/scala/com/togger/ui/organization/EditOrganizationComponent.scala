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
import com.togger.ui.components.wysiwyg.WYSIWYGTextEditorSplit.WYSIWYGTextEditorSplitSyntax
import com.togger.ui.auth._
import com.togger.ui.navbar.SidebarRefresh
import com.togger.ui.emoji.TgEmojiField.TgEmojiFieldSyntax
import com.togger.ui.localstorage.LocalStorage
import com.togger.ui.project.{FindAccountService, FindAccountsComponent}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.raquo.laminar.api.L._
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom

import java.util.UUID

case class EditOrganizationForm(
  id: UUID = UUID.randomUUID(),
  name: String = "",
  description: String = "",
  icon: String = ""
)

class EditOrganizationComponent(
  organizationId: UUID,
  organizationService: OrganizationServiceFe,
  findAccountService: FindAccountService,
  accountService: AccountService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  localStorage: LocalStorage,
  sidebarRefresh: SidebarRefresh,
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
      // The member editor lists every member, read page by page (bounded by PaginatedLoader).
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
                      description = dto.description,
                      icon = dto.icon
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
      icon = state.icon,
      membersToAdd = findAccountsComponent.membersToAdd.map(_.id),
      membersToRemove = findAccountsComponent.membersToRemove.map(_.id)
    )

    spinnerService
      .runWithSpinner(organizationService.update(dto))
      .toastMessageFn("Organization updated successfully") { _ =>
        sidebarRefresh.request()
        router.route(EditOrganizationPage(organizationId))
      }
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
        header = TgCard.CardHeader.plainText("Edit organization", TgCard.CardHeaderSize.H3),
        body = TgCard.HtmlCardContent(
          div(
            form(
              onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
              nameInput.htmlElement.amend(cls("mb-4")),
              iconInput.htmlElement.amend(cls("mb-4")),
              descriptionEditor.htmlElement.amend(cls("mb-4")),
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
