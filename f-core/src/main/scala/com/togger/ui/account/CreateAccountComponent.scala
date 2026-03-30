package com.togger.ui.account

//case class CreateAccountFormState(
//  firstName: String = "",
//  lastName: String = "",
//  email: String = ""
//)
//
//class CreateAccountComponent(
//  router: TgRouter[FrontendPage],
//  spinnerService: SpinnerService,
//  val toast: Toast
//)(implicit ioRuntime: IORuntime)
//    extends ToastMessages {
//
//  private val formState = Var(CreateAccountFormState())
//
//  private val firstNameInput = formState.tgInputManualErrorLPSig(
//    inputAndUpdater = selectorAndUpdaterMacro(_.firstName),
//    errors = EventStream.empty,
//    labelValue = "First name",
//    tgInputType = TgInputType.Text
//  )
//
//  private val lastNameInput = formState.tgInputManualErrorLPSig(
//    inputAndUpdater = selectorAndUpdaterMacro(_.lastName),
//    errors = EventStream.empty,
//    labelValue = "Last name",
//    tgInputType = TgInputType.Text
//  )
//
//  private val emailInput = formState.tgInputManualErrorLPSig(
//    inputAndUpdater = selectorAndUpdaterMacro(_.firstName),
//    errors = EventStream.empty,
//    labelValue = "Email",
//    tgInputType = TgInputType.Text
//  )
//
////  private val dateOfBirth = formState.tgInputAutoError(
////    inputAndUpdaterAndError = sue(_.dateOfBirth),
////    labelValue = "Date of birth",
////    placeholderValue = "",
////    tgInputType = TgInputType.Date
////  )
//
////  private val passwordInput = formState.tgInputAutoErrorLP(
////    inputAndUpdaterAndError = sue(_.password),
////    labelValue = "Password",
////    tgInputType = TgInputType.Password
////  )
////
////  private val confirmPasswordInput = formState.tgInputManualErrorLP(
////    inputAndUpdater = selectorAndUpdaterMacro(_.confirmPassword),
////    errorsFn = _.confirmNewPasswordValid,
////    labelValue = "Confirm New Password",
////    tgInputType = TgInputType.Password
////  )
////
//
//  private val submitter = Observer[CreateAccountFormState] { state =>
//    val dto = RegistrationDataDto(
//      firstName = state.firstName,
//      lastName = state.lastName,
//      dateOfBirth = LocalDate.parse(state.dateOfBirth),
//      sex = state.sex,
//      email = state.email,
//      password = state.password,
//      confirmedPassword = state.confirmPassword
//    )
//    spinnerService
//      .runWithSpinner(customerAccountRegistrationService.register(dto))
//      .toastMessageCodeBlock("Registration successful")(router.route(LoginPage))
//      .unsafeRunAndForget()
//  }
//
//  val element: ReactiveHtmlElement[html.Div] = div(
//    h3("Create"),
//    form(
//      onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
//      emailInput.htmlElement,
//      passwordInput.htmlElement,
//      confirmPasswordInput.htmlElement,
//      firstNameInput.htmlElement,
//      lastNameInput.htmlElement,
//      sexInput.htmlElement,
//      dateOfBirth.htmlElement,
//      TgHtmlButton.generic(formState.signal.changes.map(_.hasErrors), "Register")
//    )
//  )
//}
