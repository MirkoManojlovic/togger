package com.togger.controllers.account

import com.togger.account._
import com.togger.common.TgJsonSuccess
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.{SearchAccountDto, SearchAccountResp}

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
  val searchByFirstNameLastNameEmail = accountEndpoints.searchByFirstNameLastNameEmail.serverLogic {
    auth => searchTerm =>
      val m: TgRespIO[SearchAccountResp] = accountServiceCore
        .searchByFirstNameLastNameEmail(searchTerm)
        .map(accounts =>
          TgJsonSuccess.justBodyRight(
            SearchAccountResp(
              term = searchTerm,
              accounts = accounts.map(account =>
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
        )

//      TgJsonSuccess.justBodyRightIO(
//        SearchAccountResp(
//          term = "mirko",
//          accounts = Seq(
//            SearchAccountDto(1, "John", "Smith", "john@email.com", 1),
//            SearchAccountDto(2, "Jack", "Curry", "jack@email.com", 1),
//            SearchAccountDto(3, "Steve", "Wiggins", "steve@email.com", 1),
//            SearchAccountDto(4, "George", "Washington", "geogrge@email.com", 1)
//          )
//        )
//      )

      m
  }
}
