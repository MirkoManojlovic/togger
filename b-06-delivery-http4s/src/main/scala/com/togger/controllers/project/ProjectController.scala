package com.togger.controllers.project

import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto.{FindProjectResp, SearchAccountDto}
import com.togger.project._

import java.util.UUID

class ProjectController(projectEndpoints: ProjectEndpoints, projectService: ProjectService, tgClock: TgClock) {

  import projectEndpoints._

  val findByid = findByIdEndpoint.serverLogic { auth => id =>
    projectService
      .findById(id)
      .map { case (foundProject, members) =>
        TgJsonSuccess.justBodyRight(
          FindProjectResp(
            foundProject.id,
            foundProject.organizationId,
            foundProject.name,
            foundProject.description,
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

    val project = Project(
      id = UUID.randomUUID(),
      organizationId = dto.organizationId,
      name = dto.name,
      description = dto.description,
      createdAt = now,
      updatedAt = now
    )

    projectService
      .saveProject(project, auth.body.get.id, dto.membersToAdd)
      .map { case (savedProject, members) =>
        TgJsonSuccess.justBodyRight(
          FindProjectResp(
            id = savedProject.id,
            organizationId = savedProject.organizationId,
            name = savedProject.name,
            description = savedProject.description,
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

  val update = updateEndpoint.serverLogic { auth => dto =>
    val now = tgClock.currentInstant

    val projectToUpdate = Project(
      id = dto.id,
      organizationId = dto.organizationId,
      name = dto.name,
      description = dto.description,
      createdAt = now,
      updatedAt = now
    )

    projectService
      .updateProject(
        project = projectToUpdate,
        membersToAdd = dto.membersToAdd,
        membersToRemove = dto.membersToRemove
      )
      .map { case (project, members) =>
        TgJsonSuccess.justBodyRight(
          FindProjectResp(
            id = project.id,
            organizationId = project.organizationId,
            name = project.name,
            description = project.description,
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

  val findByOrganizationIdAndAccountId = findByOrganizationIdAndAccountIdEndpoint.serverLogic {
    auth => organizationId =>
      projectService
        .findAllByOrganizationIdAndAccountId(organizationId, auth.body.get.id)
        .map(projects =>
          TgJsonSuccess.justBodyRight(
            projects
              .map(project =>
                FindProjectResp(
                  id = project.id,
                  organizationId = project.organizationId,
                  name = project.name,
                  description = project.description,
                  members = Seq.empty
                )
              )
          )
        )
  }

  val findAllMembers = findAllMembersEndpoint.serverLogic { auth => projectId =>
    projectService
      .findAllMembers(projectId)
      .map(accounts =>
        TgJsonSuccess.justBodyRight(
          accounts.map(account =>
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
