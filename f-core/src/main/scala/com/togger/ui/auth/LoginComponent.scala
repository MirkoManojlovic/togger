package com.togger.ui.auth

import cats.effect.unsafe.IORuntime
import com.raquo.airstream.core.EventStream
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.components.button.TgHtmlButton
import com.togger.dto.login.{DeviceInfo, LoginDto}
import com.togger.routes.Pages.{HomePage, LoginPage}
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.icons.TgSvgIcons
import com.togger.ui.spinner.SpinnerService
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import com.togger.ui.modal.{ModalFactory, PlainModalVariant}
import org.scalajs.dom
case class LoginFormState(email: String = "admin@email.com", password: String = "A!good_1_password_is_good") {}

class LoginComponent(
  router: TgRouter[FrontendPage],
  loginService: LoginService,
  spinnerService: SpinnerService,
  modalFactory: ModalFactory
)(implicit runtime: IORuntime) {
  private val formState = Var(LoginFormState())

  private val emailInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.email),
    errors = EventStream.empty,
    labelValue = "Email",
    tgInputType = TgInputType.Text
  )

  private val passwordInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.password),
    errors = EventStream.empty,
    labelValue = "Password",
    tgInputType = TgInputType.Password
  )

  private val submitter = Observer[LoginFormState] { formState =>
    val dto = LoginDto(
      email = formState.email,
      password = formState.password,
      deviceInfo = DeviceInfo(
        browser = dom.window.navigator.userAgent,
        ipAddress = "0.0.0.0"
      )
    )

    spinnerService
      .runWithSpinner(
        loginService
          .login(dto)
          .map { result =>
            result.fold(
              error => modalFactory.alertModal("Wrong login credentials", "Login failed", PlainModalVariant.Danger),
              _ => {
                val currentPage = router.currentPageSignal.now()
                if (currentPage == LoginPage) router.route(HomePage)
                else router.route(currentPage)
              }
            )
          }
      )
      .unsafeRunAndForget()
  }

  val element: ReactiveHtmlElement[org.scalajs.dom.html.Div] =
    div(
      justifyCenterFlex,
      div(
        div(
          justifyCenterFlex,
          marginTop.rem(5),
          marginBottom.rem(2),
          div(
            justifyCenterFlex,
            TgSvgIcons.tgIconApp
          )
        ),
        div(
          justifyCenterFlex,
          TgCard
            .headerNoFooter(
              header = TgCard.CardHeader.plainText("📝 Welcome to Togger! 🪵", TgCard.CardHeaderSize.H3),
              body = TgCard.HtmlCardContent(
                div(
                  form(
                    onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
                    emailInput.htmlElement.amend(cls("mb-4")),
                    passwordInput.htmlElement.amend(cls("mb-4")),
                    TgHtmlButton.primary("Login")
                  )
                )
              )
            )
            .element
            .amend(heightAndWidthFitContent)
        )
      )
    )

}
