package com.togger.controllers.account

import com.togger.account._
import com.togger.common.TgError._
import com.togger.common.{AccountSearchTerm, PaginatedResponse, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.dto.SearchAccountDto

//class AccountController(accountService: ProjectService, taskEndpoints: TaskEndpoints) {
class AccountController(accountEndpoints: AccountEndpoints, accountServiceCore: AccountServiceCore) {

  //  val findById: TgSecuredEndpointFull[Long, AccountDto] =
  //    findByIdEndpoint.serverLogic { auth => accountId =>
  //      TgJsonSuccess.justBodyRightIO(AccountDto(accountId, "Some task"))
  //    }
  //
  //  val findAll =
  //    findByAssigneeEndpoint.serverLogic { auth => assigneeId =>
  //      accountService.findAll.map(tasks =>
  //        TgJsonSuccess.justBodyRight(
  //          accounts.toSeq
  //            .map(account =>
  //              AccountDto1(
  //                id = account.id,
  //                name = account.name,
  //                description = account.description,
  //                assignedTo = Option(account.assignedTo),
  //                createdAt = account.createdAt,
  //                updatedAt = account.updatedAt
  //              )
  //            )
  //        )
  //      )
  //    }
  //
  val searchByFirstNameLastNameEmail = accountEndpoints.searchByFirstNameLastNameEmail.serverLogic { auth => request =>
    val (searchTerm, pageRequest) = request
    AccountSearchTerm.validate(searchTerm) match {
      case Left(error) => error.toBadRequestLeftIO[PaginatedResponse[SearchAccountDto]]
      case Right(term) =>
        PageRequestValidation.withPage(pageRequest) { page =>
          accountServiceCore
            .searchPaginated(term, page)
            .map(accountsPage =>
              TgJsonSuccess.justBodyRight(
                accountsPage.mapItem(account =>
                  SearchAccountDto(
                    id = account.id,
                    firstName = account.firstName,
                    lastName = account.lastName,
                    email = account.email,
                    accountTypeId = account.accountTypeId
                  )
                )
              )
            )
        }
    }
  }
}
