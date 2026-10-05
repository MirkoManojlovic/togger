package com.togger.controllers.project

import cats.effect.IO
import com.togger.common.TgError._
import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto.{FindProjectResp, FindProjectLabelResp, SearchAccountDto}
import com.togger.project._
import com.togger.projectlabel.ProjectLabel

import java.time.DayOfWeek
import java.util.UUID

class ProjectController(projectEndpoints: ProjectEndpoints, projectService: ProjectService, tgClock: TgClock) {

  import projectEndpoints._

  private def toProjectLabelDto(projectLabel: ProjectLabel): FindProjectLabelResp =
    FindProjectLabelResp(
      id = projectLabel.id,
      name = projectLabel.name,
      description = projectLabel.description,
      categoryId = projectLabel.categoryId
    )

  val findByid = findByIdEndpoint.serverLogic { auth => id =>
    projectService
      .findById(id)
      .map { case (foundProject, members, projectLabels) =>
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
            projectLabels = projectLabels.map(toProjectLabelDto)
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
      .saveProject(project, auth.body.get.id, dto.membersToAdd, dto.projectLabelIds)
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
          projectLabelIds = dto.projectLabelIds
        )
        .map { case (project, members, projectLabels) =>
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
              projectLabels = projectLabels.map(toProjectLabelDto)
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
          projectService.findProjectLabelsByProjectIds(projects.map(_.id)).map { projectLabelsByProjectId =>
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
                    projectLabels = projectLabelsByProjectId.getOrElse(project.id, List.empty).map(toProjectLabelDto)
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
