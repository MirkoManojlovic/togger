package com.togger.controllers.organization

import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto.SearchAccountDto
import com.togger.dto.organization.{FindOrganizationResp, FindOrganizationsAndProjects, OrganizationProjectResp}
import com.togger.organization._

class OrganizationController(
  organizationEndpoints: OrganizationEndpoints,
  organizationService: OrganizationService,
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
              members = members.map(account =>
                SearchAccountDto(
                  id = account.id,
                  firstName = account.firstName,
                  lastName = account.lastName,
                  email = account.email,
                  accountTypeId = account.accountTypeId.toString
                )
              )
            )
          )
        }
    }

  val save = saveEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val organization = Organization(
      id = -1,
      name = dto.name,
      description = dto.description,
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
          members = members.map(account =>
            SearchAccountDto(
              id = account.id,
              firstName = account.firstName,
              lastName = account.lastName,
              email = account.email,
              accountTypeId = account.accountTypeId.toString
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
            members = members.map(account =>
              SearchAccountDto(
                id = account.id,
                firstName = account.firstName,
                lastName = account.lastName,
                email = account.email,
                accountTypeId = account.accountTypeId.toString
              )
            )
          )
        )
      }
  }

  val delete = deleteByIdEndpoint.serverLogic { auth => id =>
    organizationService
      .deleteById(id)
      .map(_ => TgJsonSuccess.justMessageRight("Task status deleted successfully"))
  }

  val findAll = findAllEndpoint.serverLogic { auth => _ =>
    organizationService
      .findAll()
      .map(organizations =>
        TgJsonSuccess.justBodyRight(
          organizations.map(organization =>
            FindOrganizationResp(
              id = organization.id,
              name = organization.name,
              description = organization.description,
              members = Seq.empty
            )
          )
        )
      )
  }

  val findByAccountId = findByAccountIdEndpoint.serverLogic { auth => accountId =>
    organizationService
      .findAllOrgByAccountId(accountId)
      .map(organizations =>
        TgJsonSuccess.justBodyRight(
          organizations.map(organization =>
            FindOrganizationResp(
              id = organization.id,
              name = organization.name,
              description = organization.description,
              members = Seq.empty
            )
          )
        )
      )
  }

  val findAllMembers = findAllMembersEndpoint.serverLogic { auth => organizationId =>
    organizationService
      .findAllMembers(organizationId)
      .map(accounts =>
        TgJsonSuccess.justBodyRight(
          accounts.map(account =>
            SearchAccountDto(
              id = account.id,
              firstName = account.firstName,
              lastName = account.lastName,
              email = account.email,
              accountTypeId = account.accountTypeId.toString
            )
          )
        )
      )
  }

  val findOrgAndProjectsByAccountId = findOrgAndProjectsByAccountIdEndpoint.serverLogic { auth => organizationId =>
    organizationService
      .findOrganizationsAndProjectsByAccountId(organizationId)
      .map(orgsAndProjects =>
        TgJsonSuccess.justBodyRight(
          orgsAndProjects.map { case (organization, projects) =>
            FindOrganizationsAndProjects(
              id = organization.id,
              name = organization.name,
              description = organization.description,
              projects = projects.map(project =>
                OrganizationProjectResp(
                  id = project.id,
                  name = project.name,
                  description = project.description
                )
              )
            )
          }
        )
      )
  }

}
