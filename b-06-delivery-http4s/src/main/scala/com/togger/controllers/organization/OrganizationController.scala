package com.togger.controllers.organization

import cats.effect.IO
import com.togger.account.AdminCheck
import com.togger.common.{Forbidden, TgClock, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.dto.SearchAccountDto
import com.togger.dto.organization.FindOrganizationResp
import com.togger.organization._

import java.util.UUID

class OrganizationController(
  organizationEndpoints: OrganizationEndpoints,
  organizationService: OrganizationService,
  adminCheck: AdminCheck,
  tgClock: TgClock
) {

  import organizationEndpoints._

  val findById =
    findByIdEndpoint.serverLogic { auth => organizationId =>
      organizationService
        .findBy(organizationId)
        .map { case (organization, members) =>
          TgJsonSuccess.justBodyRight(
            FindOrganizationResp(
              id = organization.id,
              name = organization.name,
              description = organization.description,
              icon = organization.icon,
              members = members.map(account =>
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

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val organization = Organization(
      id = UUID.randomUUID(),
      name = dto.name,
      description = dto.description,
      icon = dto.icon,
      createdAt = now,
      updatedAt = now
    )

    organizationService
      .saveOrganization(organization, auth.body.get.id, dto.membersToAdd)
      .map { case (organization, members) =>
        val respDto = FindOrganizationResp(
          id = organization.id,
          name = organization.name,
          description = dto.description,
          icon = organization.icon,
          members = members.map(account =>
            SearchAccountDto(
              id = account.id,
              firstName = account.firstName,
              lastName = account.lastName,
              email = account.email,
              accountTypeId = account.accountTypeId
            )
          )
        )
        TgJsonSuccess.justBodyRight(respDto)
      }
  }

  val update = updateEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val organizationToUpdate = Organization(
      id = dto.id,
      name = dto.name,
      description = dto.description,
      icon = dto.icon,
      createdAt = now,
      updatedAt = now
    )

    organizationService
      .updateOrganization(
        organization = organizationToUpdate,
        membersToAdd = dto.membersToAdd,
        membersToRemove = dto.membersToRemove
      )
      .map { case (organization, members) =>
        TgJsonSuccess.justBodyRight(
          FindOrganizationResp(
            id = organization.id,
            name = organization.name,
            description = organization.description,
            icon = organization.icon,
            members = members.map(account =>
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

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    organizationService.deleteWithAllData(id, auth.body.get.id).map {
      case false => Left(Forbidden)
      case true  => TgJsonSuccess.justMessageRight("Organization deleted successfully")
    }
  }

  // Reads the organizations of every tenant, so only admins may call it.
  val findAll = findAllEndpoint.serverLogic { auth => pageRequest =>
    adminCheck.isAdmin(auth.body.get.id).flatMap {
      case false => IO.pure(Left(Forbidden))
      case true =>
        PageRequestValidation.withPage(pageRequest) { page =>
          organizationService
            .findPaginated(page)
            .map(organizationsPage => TgJsonSuccess.justBodyRight(organizationsPage.mapItem(toListDto)))
        }
    }
  }

  val findByAccountId = findByAccountIdEndpoint.serverLogic { auth => request =>
    val (accountId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      organizationService
        .findPaginatedByAccountId(accountId, page)
        .map(organizationsPage => TgJsonSuccess.justBodyRight(organizationsPage.mapItem(toListDto)))
    }
  }

  val findAllMembers = findAllMembersEndpoint.serverLogic { auth => request =>
    val (organizationId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      organizationService
        .findPaginatedMembers(organizationId, page)
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

  // Organization list rows carry no members; the members endpoint pages them.
  private def toListDto(organization: Organization): FindOrganizationResp =
    FindOrganizationResp(
      id = organization.id,
      name = organization.name,
      description = organization.description,
      icon = organization.icon,
      members = Seq.empty
    )

}
