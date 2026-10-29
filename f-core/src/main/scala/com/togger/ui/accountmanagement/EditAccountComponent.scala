package com.togger.ui.accountmanagement

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.raquo.laminar.api.L._
import com.togger.auth.AccountInfoValidation
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{AccountManagementDto, AccountTypeDto, UpdateAccountReq}
import com.togger.routes.Pages.ShowAllAccountsPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.style.TgCommonStyleJs.{heightAndWidthFitContent, justifyCenterFlex}
import com.togger.toast.ToastMessages
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.TgSelect.TgSelectSyntax
import com.togger.ui.auth.{FormErrors, TgCard}
import com.togger.ui.spinner.SpinnerService
import com.togger.ui.toast.Toast
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro

import java.util.UUID
import scala.collection.immutable.ListMap

/** `accountTypeId` is the id as text, because the select works on text. */
case class EditAccountForm(
  id: UUID = UUID.randomUUID(),
  firstName: String = "",
  lastName: String = "",
  email: String = "",
  accountTypeId: String = ""
) extends FormErrors {

  def firstNameValid: Seq[String] = AccountInfoValidation.firstName(firstName).map(_.message)

  def lastNameValid: Seq[String] = AccountInfoValidation.lastName(lastName).map(_.message)

  def emailValid: Seq[String] = AccountInfoValidation.email(email).map(_.message)

  def accountTypeValid: Seq[String] = if (accountTypeId.isEmpty) Seq("Account type is required") else Seq.empty

  override def additionalErrors: Seq[String] = firstNameValid ++ lastNameValid ++ emailValid ++ accountTypeValid

  def toUpdateReq: UpdateAccountReq =
    UpdateAccountReq(id, firstName, lastName, email, UUID.fromString(accountTypeId))
}

object EditAccountForm {
  def fromDto(dto: AccountManagementDto): EditAccountForm =
    EditAccountForm(dto.id, dto.firstName, dto.lastName, dto.email, dto.accountTypeId.toString)
}

class EditAccountComponent(
  accountService: AccountManagementFeService,
  router: TgRouter[FrontendPage],
  spinnerService: SpinnerService,
  accountId: UUID,
  val toast: Toast
)(implicit ioRuntime: IORuntime)
    extends ToastMessages {

  private val formState = Var(EditAccountForm(id = accountId))

  private val accountTypesVar = Var(Seq.empty[AccountTypeDto])

  private val loadedVar = Var(false)

  private def load(): IO[Unit] =
    for {
      account <- accountService.findById(accountId)
      types <- accountService.findAccountTypes
    } yield (account, types) match {
      case (Right(accountSuccess), Right(typesSuccess)) =>
        formState.set(EditAccountForm.fromDto(accountSuccess.body.get))
        accountTypesVar.set(typesSuccess.body.getOrElse(List.empty))
        loadedVar.set(true)
      case (Left(failure), _) => toast.error(failure)
      case (_, Left(failure)) => toast.error(failure)
    }

  spinnerService.runWithSpinner(load()).unsafeRunAndForget()

  private val submitter = Observer[EditAccountForm] { form =>
    spinnerService
      .runWithSpinner(accountService.update(form.toUpdateReq))
      .toastBackendMessageCodeBlock {
        router.route(ShowAllAccountsPage())
      }
      .unsafeRunAndForget()
  }

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

  private def accountTypeSelect(types: Seq[AccountTypeDto]) =
    formState.tgSelectManualError(
      inputAndUpdater = selectorAndUpdaterMacro(_.accountTypeId),
      errorsFn = _.accountTypeValid,
      labelValue = "Account type",
      emptyValue = Some("Select an account type"),
      valuesAndDisplays = ListMap(types.map(accountType => accountType.id.toString -> accountType.name): _*)
    )

  val element =
    div(
      justifyCenterFlex,
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText("Edit an account", TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              child <-- loadedVar.signal.combineWith(accountTypesVar.signal).map {
                case (false, _) => div()
                case (true, types) =>
                  form(
                    onSubmit.preventDefault.mapTo(formState.now()) --> submitter,
                    firstNameInput.htmlElement.amend(cls("mb-4")),
                    lastNameInput.htmlElement.amend(cls("mb-4")),
                    emailInput.htmlElement.amend(cls("mb-4")),
                    accountTypeSelect(types).htmlElement.amend(cls("mb-4")),
                    div(cls("flex justify-end"), TgHtmlButton.updateSmall(formState.signal.changes.map(_.hasErrors)))
                  )
              }
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    )
}
