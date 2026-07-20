package com.togger.ui.auth

import cats.effect.unsafe.IORuntime
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgEndpoints.TgBaseEndpoint
import com.togger.common.TgIds
import com.togger.common.TgResponseTypes.TgRespEmptyBodyIO
import com.togger.components.button.TgHtmlButton
import com.togger.dto.login.ChangePasswordDto
import com.togger.login.AuthEndpoints
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastError
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.spinner.SpinnerService
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.scalaland.chimney.dsl._
import org.scalajs.dom

class ChangePasswordService(
  baseEndpoint: TgBaseEndpoint,
  securedEndpoint: SecuredEndpoint,
  protectedBackendClient: ProtectedBackendClient
) {

  val changePasswordEndpoint = new AuthEndpoints(baseEndpoint, securedEndpoint)

  def change(dto: ChangePasswordDto): TgRespEmptyBodyIO =
    protectedBackendClient.protectedHttpCall(changePasswordEndpoint.changePassword)(dto)

}

case class ChangePasswordFormState(
  oldPassword: String = "",
  newPassword: String = "",
  confirmNewPassword: String = ""
) extends FormErrors
    with PasswordValidator {

  override def password: String = newPassword

  def confirmNewPasswordValid: Seq[String] = passwordsMatch(password, confirmNewPassword)

  override def additionalErrors: Seq[String] = confirmNewPasswordValid

}

class ChangePasswordComponent(
  changePasswordService: ChangePasswordService,
  accountService: AccountService,
  spinnerService: SpinnerService
//  val toast: Toast
)(implicit ioRuntime: IORuntime) {

  private val formState = Var(ChangePasswordFormState())

  private val oldPasswordInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.oldPassword),
    errors = EventStream.empty,
    labelValue = "Old Password",
    tgInputType = TgInputType.Password
  )

  private val newPasswordInput = formState.tgInputManualErrorLP(
    inputAndUpdater = selectorAndUpdaterMacro(_.newPassword),
    errorsFn = _.passwordValid,
    labelValue = "New Password",
    tgInputType = TgInputType.Password
  )

  private val confirmNewPasswordInput = formState.tgInputManualErrorLP(
    inputAndUpdater = selectorAndUpdaterMacro(_.confirmNewPassword),
    errorsFn = _.confirmNewPasswordValid,
    labelValue = "Confirm New Password",
    tgInputType = TgInputType.Password
  )

  private val submitter = Observer[ChangePasswordFormState] { state =>
    val id = accountService.get.map(_.id).getOrElse(TgIds.UNASSIGNED_ACCOUNT_ID)
    val dto = state.into[ChangePasswordDto].withFieldComputed(_.accountId, _ => id).transform
    spinnerService
      .runWithSpinner(changePasswordService.change(dto))
      .map(
        _.fold(
          error => dom.window.alert(ToastError.errorMatch(error)),
          _ => dom.window.alert("Password successfully changed")
        )
      )
      .unsafeRunAndForget()
  }

  def element: ReactiveHtmlElement[org.scalajs.dom.html.Div] =
    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Change Password", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              form(
                onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
                oldPasswordInput.htmlElement.amend(cls("mb-4")),
                newPasswordInput.htmlElement.amend(cls("mb-4")),
                confirmNewPasswordInput.htmlElement.amend(cls("mb-4")),
                TgHtmlButton.update(formState.signal.changes.map(_.hasErrors))
              )
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    )

}
