package com.togger.controllers.project

import cats.effect.IO
import com.togger.common.TgError._
import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.account.Account
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

  private def toFindProjectResp(
    project: Project,
    members: Seq[Account],
    projectLabels: Seq[ProjectLabel]
  ): FindProjectResp =
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

  val findByid = findByIdEndpoint.serverLogic { auth => id =>
    projectService
      .findById(id)
      .map { case (foundProject, members, projectLabels) =>
        TgJsonSuccess.justBodyRight(toFindProjectResp(foundProject, members, projectLabels))
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
        TgJsonSuccess.justBodyRight(toFindProjectResp(savedProject, members, Seq.empty))
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
          TgJsonSuccess.justBodyRight(toFindProjectResp(project, members, projectLabels))
        }
    }
  }

  val findByOrganizationIdAndAccountId = findByOrganizationIdAndAccountIdEndpoint.serverLogic { auth => request =>
    val (organizationId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      projectService
        .findPaginatedByOrganizationIdAndAccountId(organizationId, auth.body.get.id, page)
        .flatMap { projectsPage =>
          projectService.findProjectLabelsByProjectIds(projectsPage.items.map(_.id)).map { projectLabelsByProjectId =>
            TgJsonSuccess.justBodyRight(
              projectsPage.mapItem(project =>
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
  }

  val findAllMembers = findAllMembersEndpoint.serverLogic { auth => request =>
    val (projectId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      projectService
        .findPaginatedMembers(projectId, page)
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

  val searchByLabels = searchByLabelsEndpoint.serverLogic { auth => request =>
    val (dto, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      projectService
        .searchByLabels(auth.body.get.id, dto.projectLabelIds, page)
        .map { resultsPage =>
          TgJsonSuccess.justBodyRight(resultsPage.mapItem { case (project, members, projectLabels) =>
            toFindProjectResp(project, members, projectLabels)
          })
        }
    }
  }

}
