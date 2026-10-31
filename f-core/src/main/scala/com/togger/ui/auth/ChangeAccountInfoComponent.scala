package com.togger.ui.auth

import cats.effect.unsafe.IORuntime
import com.togger.auth.{AccountInfoValidation, SecuredEndpoint}
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgEndpoints.TgBaseEndpoint
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.components.button.TgHtmlButton
import com.togger.dto.login.AccountInfoDto
import com.togger.login.AuthEndpoints
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastError
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.spinner.SpinnerService
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.scalaland.chimney.dsl._
import com.togger.ui.modal.{ModalFactory, PlainModalVariant}
import org.scalajs.dom

trait ChangeAccountInfoService {
  def change(dto: AccountInfoDto): TgRespIO[AccountInfoDto]
}

class ChangeAccountInfoServiceImpl(
  baseEndpoint: TgBaseEndpoint,
  securedEndpoint: SecuredEndpoint,
  protectedBackendClient: ProtectedBackendClient,
  loginRepo: LoginRepo
) extends ChangeAccountInfoService {

  private val authEndpoints = new AuthEndpoints(baseEndpoint, securedEndpoint)

  override def change(dto: AccountInfoDto): TgRespIO[AccountInfoDto] =
    protectedBackendClient
      .protectedHttpCall(authEndpoints.updateAccountInfo)(dto)
      .map { result =>
        result.foreach(_.body.foreach(loginRepo.updateInfo))
        result
      }

}

case class ChangeAccountInfoFormState(
  firstName: String = "",
  lastName: String = "",
  email: String = ""
) extends FormErrors {

  def firstNameValid: Seq[String] = AccountInfoValidation.firstName(firstName).map(_.message)

  def lastNameValid: Seq[String] = AccountInfoValidation.lastName(lastName).map(_.message)

  def emailValid: Seq[String] = AccountInfoValidation.email(email).map(_.message)

  override def additionalErrors: Seq[String] = firstNameValid ++ lastNameValid ++ emailValid

}

class ChangeAccountInfoComponent(
  changeAccountInfoService: ChangeAccountInfoService,
  accountService: AccountService,
  spinnerService: SpinnerService,
  modalFactory: ModalFactory
)(implicit ioRuntime: IORuntime) {

  private val formState = Var(
    accountService.get.fold(ChangeAccountInfoFormState())(account =>
      ChangeAccountInfoFormState(
        firstName = account.firstName.getOrElse(""),
        lastName = account.lastName.getOrElse(""),
        email = account.email.getOrElse("")
      )
    )
  )

  private val firstNameInput = formState.tgInputManualErrorLP(
    inputAndUpdater = selectorAndUpdaterMacro(_.firstName),
    errorsFn = _.firstNameValid,
    labelValue = "First Name"
  )

  private val lastNameInput = formState.tgInputManualErrorLP(
    inputAndUpdater = selectorAndUpdaterMacro(_.lastName),
    errorsFn = _.lastNameValid,
    labelValue = "Last Name"
  )

  private val emailInput = formState.tgInputManualErrorLP(
    inputAndUpdater = selectorAndUpdaterMacro(_.email),
    errorsFn = _.emailValid,
    labelValue = "Email"
  )

  private val submitter = Observer[ChangeAccountInfoFormState] { state =>
    spinnerService
      .runWithSpinner(changeAccountInfoService.change(state.into[AccountInfoDto].transform))
      .map(
        _.fold(
          error => modalFactory.alertModal(ToastError.errorMatch(error), "Error", PlainModalVariant.Danger),
          _ => modalFactory.alertModal("Account information successfully changed", "Success")
        )
      )
      .unsafeRunAndForget()
  }

  def element: ReactiveHtmlElement[org.scalajs.dom.html.Div] =
    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Account Information", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              form(
                onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
                firstNameInput.htmlElement.amend(cls("mb-4")),
                lastNameInput.htmlElement.amend(cls("mb-4")),
                emailInput.htmlElement.amend(cls("mb-4")),
                div(cls("flex justify-end"), TgHtmlButton.updateSmall(formState.signal.changes.map(_.hasErrors)))
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    )

}
