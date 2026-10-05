package com.togger.controllers.project

import cats.effect.IO
import com.togger.common.TgError._
import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto.{FindProjectResp, FindTechStackResp, SearchAccountDto}
import com.togger.project._
import com.togger.techstack.TechStack

import java.time.DayOfWeek
import java.util.UUID

class ProjectController(projectEndpoints: ProjectEndpoints, projectService: ProjectService, tgClock: TgClock) {

  import projectEndpoints._

  private def toTechStackDto(techStack: TechStack): FindTechStackResp =
    FindTechStackResp(
      id = techStack.id,
      name = techStack.name,
      description = techStack.description,
      categoryId = techStack.categoryId
    )

  val findByid = findByIdEndpoint.serverLogic { auth => id =>
    projectService
      .findById(id)
      .map { case (foundProject, members, techStacks) =>
        TgJsonSuccess.justBodyRight(
          FindProjectResp(
            foundProject.id,
            foundProject.organizationId,
            foundProject.name,
            foundProject.description,
            foundProject.icon,
            workDays = foundProject.workDays.map(_.getValue),
            members = members.map(account =>
              SearchAccountDto(
                id = account.id,
                firstName = account.firstName,
                lastName = account.lastName,
                email = account.email,
                accountTypeId = account.accountTypeId
              )
            ),
            techStacks = techStacks.map(toTechStackDto)
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
      icon = dto.icon,
      workDays = Project.DefaultWorkDays,
      createdAt = now,
      updatedAt = now
    )

    projectService
      .saveProject(project, auth.body.get.id, dto.membersToAdd, dto.techStackIds)
      .map { case (savedProject, members) =>
        TgJsonSuccess.justBodyRight(
          FindProjectResp(
            id = savedProject.id,
            organizationId = savedProject.organizationId,
            name = savedProject.name,
            description = savedProject.description,
            icon = savedProject.icon,
            workDays = savedProject.workDays.map(_.getValue),
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
    val workDays = dto.workDays.map(DayOfWeek.of)

    if (workDays.isEmpty) {
      IO.pure(ProjectError.EmptyWorkDays.toBadRequestLeft[FindProjectResp])
    } else {
      val now = tgClock.currentInstant

      val projectToUpdate = Project(
        id = dto.id,
        organizationId = dto.organizationId,
        name = dto.name,
        description = dto.description,
        icon = dto.icon,
        workDays = workDays,
        createdAt = now,
        updatedAt = now
      )

      projectService
        .updateProject(
          project = projectToUpdate,
          membersToAdd = dto.membersToAdd,
          membersToRemove = dto.membersToRemove,
          techStackIds = dto.techStackIds
        )
        .map { case (project, members, techStacks) =>
          TgJsonSuccess.justBodyRight(
            FindProjectResp(
              id = project.id,
              organizationId = project.organizationId,
              name = project.name,
              description = project.description,
              icon = project.icon,
              workDays = project.workDays.map(_.getValue),
              members = members.map(account =>
                SearchAccountDto(
                  id = account.id,
                  firstName = account.firstName,
                  lastName = account.lastName,
                  email = account.email,
                  accountTypeId = account.accountTypeId
                )
              ),
              techStacks = techStacks.map(toTechStackDto)
            )
          )
        }
    }
  }

  val findByOrganizationIdAndAccountId = findByOrganizationIdAndAccountIdEndpoint.serverLogic {
    auth => organizationId =>
      projectService
        .findAllByOrganizationIdAndAccountId(organizationId, auth.body.get.id)
        .flatMap { projects =>
          projectService.findTechStacksByProjectIds(projects.map(_.id)).map { techStacksByProjectId =>
            TgJsonSuccess.justBodyRight(
              projects
                .map(project =>
                  FindProjectResp(
                    id = project.id,
                    organizationId = project.organizationId,
                    name = project.name,
                    description = project.description,
                    icon = project.icon,
                    workDays = project.workDays.map(_.getValue),
                    members = Seq.empty,
                    techStacks = techStacksByProjectId.getOrElse(project.id, List.empty).map(toTechStackDto)
                  )
                )
            )
          }
        }
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
