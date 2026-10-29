package com.togger.controllers.account

import cats.effect.IO
import com.togger.account._
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{Forbidden, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.dto._

import java.util.UUID

/** Every endpoint reads or changes accounts across tenants, so only admins may call them. */
class AccountManagementController(
  accountEndpoints: AccountEndpoints,
  accountManagementService: AccountManagementService,
  adminCheck: AdminCheck
) {

  private def adminOnly[A](callerId: UUID)(f: => TgRespIO[A]): TgRespIO[A] =
    adminCheck.isAdmin(callerId).flatMap {
      case false => IO.pure(Left(Forbidden))
      case true  => f
    }

  val findAll = accountEndpoints.findAllEndpoint.serverLogic { auth => pageRequest =>
    adminOnly(auth.body.get.id) {
      PageRequestValidation.withPage(pageRequest) { page =>
        accountManagementService
          .findPaginated(page)
          .map(accountsPage => TgJsonSuccess.justBodyRight(accountsPage.mapItem(toDto)))
      }
    }
  }

  val findAccountTypes = accountEndpoints.findAccountTypesEndpoint.serverLogic { auth => _ =>
    adminOnly(auth.body.get.id) {
      accountManagementService.findAccountTypes
        .map(types => TgJsonSuccess.justBodyRight(types.map(t => AccountTypeDto(t.id, t.name))))
    }
  }

  val findById = accountEndpoints.findByIdEndpoint.serverLogic { auth => id =>
    adminOnly(auth.body.get.id) {
      accountManagementService.findById(id).map {
        case Right(account) => TgJsonSuccess.justBodyRight(toDto(account))
        case Left(error)    => error.toBadRequestLeft[AccountManagementDto]
      }
    }
  }

  val update = accountEndpoints.updateEndpoint.serverLogic { auth => req =>
    adminOnly(auth.body.get.id) {
      accountManagementService
        .update(req.id, req.firstName.trim, req.lastName.trim, req.email.trim, req.accountTypeId)
        .map {
          case Right(account) => TgJsonSuccess.justBodyRight(toDto(account))
          case Left(error)    => error.toBadRequestLeft[AccountManagementDto]
        }
    }
  }

  val delete = accountEndpoints.deleteByIdEndpoint.serverLogic { auth => id =>
    adminOnly(auth.body.get.id) {
      accountManagementService.delete(auth.body.get.id, id).map {
        case Right(_)    => TgJsonSuccess.justMessageRight("Account deleted successfully")
        case Left(error) => error.toBadRequestLeft[TgEmptyBody]
      }
    }
  }

  private def toDto(account: AccountWithProjects): AccountManagementDto =
    AccountManagementDto(
      id = account.id,
      firstName = account.firstName,
      lastName = account.lastName,
      email = account.email,
      accountTypeId = account.accountTypeId,
      accountTypeName = account.accountTypeName,
      projects = account.projects.map(p => AccountProjectDto(p.projectId, p.organizationId, p.projectName))
    )
}
