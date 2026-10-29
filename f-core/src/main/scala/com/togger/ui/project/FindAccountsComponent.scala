package com.togger.ui.project

import cats.effect.unsafe.IORuntime
import com.togger.account.AccountEndpoints
import com.togger.ui.account.HiddenAccounts
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{AccountSearchTerm, PageRequest, PaginatedResponse}
import com.togger.components.button.TgHtmlButton
import com.togger.dto.SearchAccountDto
import com.togger.organization.OrganizationEndpoints
import com.togger.ui.auth.TgCheckbox.TgCheckboxSyntax
import com.togger.ui.auth.TgInput.TgInputSyntax
import com.togger.ui.auth.{AccountService, TgInputType}
import com.togger.ui.components.TgPaginationComponent
import com.togger.ui.components.pagination.PaginationState
import com.togger.ui.organization.TgIcons
import com.togger.ui.spinner.SpinnerService
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.util.FrontendMacros.selectorAndUpdaterMacro
import org.scalajs.dom.HTMLDivElement

import java.util.UUID

//class FindAccountServiceImpl extends FindAccountService {
//
//  val accounts = Seq(
//    SearchAccountDto(1, "John", "Smith", "john@email.com", 1),
//    SearchAccountDto(2, "Jack", "Curry", "jack@email.com", 1),
//    SearchAccountDto(3, "Steve", "Wiggins", "steve@email.com", 1),
//    SearchAccountDto(4, "George", "Washington", "geogrge@email.com", 1)
//  )
//
//  override def findByFirstNameLastNameOrEmail(input: String): TgRespIO[List[SearchAccountDto]] = {
//    val foundAccounts = accounts.filter(dto => dto.fullNameAndEmail.contains(input))
//    IO(
//      Either.cond(foundAccounts.nonEmpty, TgJsonSuccess.justBody(foundAccounts.toList), TgJsonFailure.notFound())
//    )
//  }
//
//}

trait FindAccountService {
  def findByFirstNameLastNameOrEmail(input: String, page: PageRequest): TgRespIO[PaginatedResponse[SearchAccountDto]]
}

class FindAccountServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends FindAccountService {
  val accountEndpoints = new AccountEndpoints(securedEndpoint)
  val organizationEndpoints = new OrganizationEndpoints(securedEndpoint)

  override def findByFirstNameLastNameOrEmail(
    searchTerm: String,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[SearchAccountDto]] =
    HiddenAccounts.visiblePage(
      protectedBackendClient.protectedHttpCall(accountEndpoints.searchByFirstNameLastNameEmail)((searchTerm, page))
    )
}

class FindAccountsComponent(
  findAccountService: FindAccountService,
  accountService: AccountService,
  existingAccountsVar: Var[Seq[SearchAccountDto]] = Var(Seq.empty),
  excludeUserAccount: Boolean = false
)(implicit
  ioRuntime: IORuntime
) {

  case class FindAccountForm(
    searchTerm: String = "",
    existingMembers: Map[UUID, SearchAccountDto] = Map.empty,
    foundMembers: Either[String, Seq[SearchAccountDto]] = Right(List.empty),
    membersToAdd: Map[UUID, SearchAccountDto] = Map.empty,
    membersToRemove: Map[UUID, SearchAccountDto] = Map.empty
  )

  private val formState = Var(FindAccountForm())

  private def accountsToMap(accounts: Seq[SearchAccountDto]): Map[UUID, SearchAccountDto] =
    removeUserAccount(accounts)
      .groupMap(_.id)(identity)
      .map { case (id, accounts) => id -> accounts.head }

  private def removeUserAccount(accounts: Seq[SearchAccountDto]) =
    if (excludeUserAccount) accounts.filterNot(_.id == accountService.get.get.id)
    else accounts

  def membersToAdd: Seq[SearchAccountDto] =
    formState.now().membersToAdd.values.toSeq

  def membersToRemove: Seq[SearchAccountDto] =
    formState.now().membersToRemove.values.toSeq

  private val localRootSpinnerDiv = div(position.relative)
  private val localSpinner = SpinnerService.localSpinner(localRootSpinnerDiv, error => println(error))

  private val employeeSearchInput = formState.tgInputManualErrorLPSig(
    inputAndUpdater = selectorAndUpdaterMacro(_.searchTerm),
    errors = EventStream.empty,
    labelValue = "Find an employee",
    tgInputType = TgInputType.Text,
    isRequired = false
  )

  private val paginationVar = Var(Option.empty[PaginationState])

  // The term the shown page was searched with, so another page of the same search can be read.
  private var searchedTerm = ""

  private def searchPage(term: String, page: PageRequest): Unit =
    AccountSearchTerm.validate(term) match {
      case Left(_) =>
        paginationVar.set(None)
        formState.update(
          _.copy(foundMembers = Left(s"Enter at least ${AccountSearchTerm.MinLength} characters to search"))
        )
      case Right(validTerm) =>
        localSpinner
          .runWithSpinner(
            findAccountService
              .findByFirstNameLastNameOrEmail(validTerm, page)
              .map(
                _.fold(
                  _ => {
                    paginationVar.set(None)
                    formState.update(_.copy(foundMembers = Left("No members found")))
                  },
                  success => {
                    val response = success.body.get
                    val existingAccounts = existingAccountsVar.now()
                    val newFoundAccounts = response.items.diff(existingAccounts)
                    searchedTerm = validTerm
                    paginationVar.set(Some(PaginationState.fromResponse(response)))
                    formState.update(_.copy(foundMembers = Right(removeUserAccount(newFoundAccounts))))
                  }
                )
              )
          )
          .unsafeRunAndForget()
    }

  private val submitter: Observer[String] = Observer[String] { input =>
    val size = paginationVar.now().map(_.size).getOrElse(PageRequest.DefaultSize)
    searchPage(input, PageRequest(PageRequest.DefaultPage, size))
  }

  val element: ReactiveHtmlElement[HTMLDivElement] =
    div(
      form(
        onSubmit.preventDefault.mapTo(formState.now().searchTerm) --> submitter,
        employeeSearchInput.htmlElement.amend(cls("mb-4")),
        TgHtmlButton.primary("Search").amend(cls("mb-4"))
      ),
      div(
        cls("border rounded-md border-default-medium mt-4 mb-4"),
        h3(
          cls("flex items-center bg-neutral-secondary-medium border-b border-default-medium"),
          div(TgIcons.userIcon("w-5 h-5 text-body mr-1")),
          cls("font-semibold text-heading"),
          "Members"
        ),
        div(
          cls("pt-1 pb-1"),
          existingMembersElement
        )
      ),
      localRootSpinnerDiv.amend(foundMembersElement).amend(cls("mt-4 mb-4")),
      div(
        cls("border rounded-md border-default-medium mt-4 mb-4"),
        h3(
          cls("flex items-center bg-neutral-secondary-medium border-b border-default-medium"),
          div(TgIcons.checkIcon("w-5 h-5 text-green-600 mr-1")),
          cls("font-semibold text-heading"),
          "Members to add"
        ),
        div(
          cls("pt-1 pb-1"),
          membersToAddElement
        )
      ),
      div(
        cls("border rounded-md border-default-medium mt-4 mb-4"),
        h3(
          cls("flex items-center bg-neutral-secondary-medium border-b border-default-medium"),
          div(TgIcons.minusCircleIcon("w-5 h-5 text-red-600 mr-1")),
          cls("font-semibold text-heading"),
          "Members to remove"
        ),
        div(
          cls("pt-1 pb-1"),
          membersToRemoveElement
        )
      )
    )

  private lazy val foundMembersElement = div(
    child <-- formState.signal.map(state =>
      state.foundMembers.fold(
        message => div(message),
        dtos =>
          div(
            div(
              cls("border rounded-md border-default-medium"),
              h3(
                cls("flex items-center bg-neutral-secondary-medium border-b border-default-medium"),
                div(TgIcons.searchIcon("w-5 h-5 text-body mr-1")),
                cls("font-semibold text-heading"),
                "Found members"
              ),
              div(
                cls("pt-1 pb-1"),
                dtos.map { dto =>
                  import dto._
                  div(
                    formState
                      .tgCheckBox(
                        inputSignal = formState.signal.map(_.membersToAdd.contains(dto.id)),
                        inputStateUpdater = formState.updater(addMember(dto)),
                        labelValue = s"$firstName $lastName - $email"
                      )
                      .htmlElement
                  )
                }
              ),
              TgPaginationComponent(
                paginationVar.signal,
                (newPage, newSize) => searchPage(searchedTerm, PageRequest(newPage, newSize))
              )
            )
          )
      )
    )
  )

  private lazy val existingMembersElement = div(
    children <-- existingAccountsVar.signal
      .map(accountsToMap)
      .map { existingMap =>
        formState.update(state => state.copy(existingMembers = existingMap))
        existingMap
      }
      .map(asMap =>
        asMap.values.map { dto =>
          import dto._
          div(
            formState
              .tgCheckBox(
                inputSignal = formState.signal.map(_.membersToRemove.contains(dto.id)).map(a => !a),
                inputStateUpdater = formState.updater(removeMember(dto)),
                labelValue = s"$firstName $lastName - $email"
              )
              .htmlElement
          )
        }.toSeq
      )
  )

  private def removeMember(dto: SearchAccountDto)(state: FindAccountForm, isSelected: Boolean): FindAccountForm =
    state.copy(membersToRemove =
      if (state.membersToRemove.contains(dto.id) && isSelected) state.membersToRemove.removed(dto.id)
      else if (!state.membersToRemove.contains(dto.id) && !isSelected) state.membersToRemove ++ Map(dto.id -> dto)
      else state.membersToRemove
    )

  private lazy val membersToAddElement = div(
    children <-- formState.signal.map(state =>
      state.membersToAdd.values.map { dto =>
        import dto._
        div(
          formState
            .tgCheckBox(
              inputSignal = formState.signal.map(_.membersToAdd.contains(dto.id)),
              inputStateUpdater = formState.updater(addMember(dto)),
              labelValue = s"$firstName $lastName - $email"
            )
            .htmlElement
        )
      }.toSeq
    )
  )

  private def addMember(dto: SearchAccountDto)(state: FindAccountForm, isSelected: Boolean): FindAccountForm =
    state.copy(membersToAdd =
      if (state.membersToAdd.contains(dto.id) && !isSelected) state.membersToAdd.removed(dto.id)
      else if (!state.membersToAdd.contains(dto.id) && isSelected) state.membersToAdd ++ Map(dto.id -> dto)
      else state.membersToAdd
    )

  private lazy val membersToRemoveElement = div(
    children <-- formState.signal.map(state =>
      state.membersToRemove.values.map { dto =>
        import dto._
        div(
          formState
            .tgCheckBox(
              inputSignal = formState.signal.map(_.membersToRemove.contains(dto.id)),
              inputStateUpdater = formState.updater(cancelRemoveMember(dto)),
              labelValue = s"$firstName $lastName - $email"
            )
            .htmlElement
        )
      }.toSeq
    )
  )

  private def cancelRemoveMember(dto: SearchAccountDto)(state: FindAccountForm, isSelected: Boolean): FindAccountForm =
    state.copy(membersToRemove =
      if (state.membersToRemove.contains(dto.id) && !isSelected) state.membersToRemove.removed(dto.id)
      else if (!state.membersToRemove.contains(dto.id) && isSelected) state.membersToRemove ++ Map(dto.id -> dto)
      else state.membersToRemove
    )
}
