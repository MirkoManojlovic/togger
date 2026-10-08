package com.togger.ui.organization

import cats.effect.IO
import cats.implicits._
import com.togger.common.{TgJsonFailure, TgJsonSuccess}
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.FindProjectResp
import com.togger.dto.organization.{FindOrganizationResp, OrganizationProjectResp}
import com.togger.ui.project.ProjectService

import java.util.UUID

case class OrganizationWithProjects(
  id: UUID,
  name: String,
  description: String,
  icon: String,
  projects: Seq[OrganizationProjectResp]
)

/** The organizations of an account with the projects of each, for the sidebar and the task pickers. The backend pages
  * organizations and projects separately, so the organizations are read page by page, and then the projects of every
  * organization through the paginated project call (see [[com.togger.ui.components.pagination.PaginatedLoader]] for
  * the bound on each).
  */
class OrganizationProjectsLoader(organizationService: OrganizationServiceFe, projectService: ProjectService) {

  def loadForAccount(accountId: UUID): TgRespIO[Seq[OrganizationWithProjects]] =
    organizationService.findAllByAccountId(accountId).flatMap {
      case Left(failure) => IO.pure(Left(failure))
      case Right(success) =>
        success.body
          .getOrElse(Seq.empty)
          .toList
          .parTraverse(withProjects)
          .map(_.sequence.map(TgJsonSuccess.justBody(_)))
    }

  private def withProjects(organization: FindOrganizationResp): IO[Either[TgJsonFailure, OrganizationWithProjects]] =
    projectService
      .findAllByOrganizationId(organization.id)
      .map(_.map { success =>
        OrganizationWithProjects(
          id = organization.id,
          name = organization.name,
          description = organization.description,
          icon = organization.icon,
          projects = success.body.getOrElse(Seq.empty).map(toProject)
        )
      })

  private def toProject(project: FindProjectResp): OrganizationProjectResp =
    OrganizationProjectResp(
      id = project.id,
      name = project.name,
      description = project.description,
      icon = project.icon,
      workDays = project.workDays
    )
}
