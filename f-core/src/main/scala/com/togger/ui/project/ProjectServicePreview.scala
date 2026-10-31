package com.togger.ui.project

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{Forbidden, PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.preview._

import java.util.UUID

/** `labelsOf` resolves project label ids to labels; the project label preview service plugs itself in here. */
class ProjectServicePreview(
  ctx: PreviewContext,
  accounts: AccountsPreviewData,
  organizations: OrganizationsPreviewData,
  projects: ProjectsPreviewData,
  labelsOf: Seq[UUID] => List[FindProjectLabelResp] = _ => List.empty
) extends ProjectService {

  private val defaultWorkDays = Set(1, 2, 3, 4, 5)

  override def createProject(dto: CreateProjectReq): TgRespIO[FindProjectResp] =
    PreviewResponses.authenticated(ctx.loginRepo) { callerId =>
      IO.defer {
        if (organizations.organizations.find(dto.organizationId).isEmpty) PreviewResponses.notFound[FindProjectResp]
        else {
          val created = PreviewProject(
            UUID.randomUUID(),
            dto.organizationId,
            dto.name,
            dto.description,
            dto.icon,
            defaultWorkDays,
            dto.projectLabelIds.distinct.toList
          )
          projects.projects.upsert(created)
          projects.addMembers(created.id, callerId +: dto.membersToAdd)
          projects.notifyProjectCreated(created.id)
          PreviewResponses.ok(toResp(created))
        }
      }
    }

  override def updateProject(dto: UpdateProjectReq): TgRespIO[FindProjectResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (dto.workDays.isEmpty)
          PreviewResponses.badRequest[FindProjectResp]("A project must have at least one work day selected")
        else {
          val updated = projects.projects.update(dto.id)(
            _.copy(
              organizationId = dto.organizationId,
              name = dto.name,
              description = dto.description,
              icon = dto.icon,
              workDays = dto.workDays,
              projectLabelIds = dto.projectLabelIds.distinct.toList
            )
          )
          updated.foreach { _ =>
            projects.removeMembers(dto.id, dto.membersToRemove)
            projects.addMembers(dto.id, dto.membersToAdd)
          }
          PreviewResponses.fromOption(updated.map(toResp))
        }
      }
    )

  override def deleteProject(id: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer {
        if (!projects.isMember(id, callerId) && !accounts.isAdmin(callerId)) IO.pure(Left(Forbidden))
        else {
          projects.deleteProject(id)
          PreviewResponses.okEmpty("Project deleted successfully")
        }
      }
    )

  override def findByOrganizationId(
    organizationId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindProjectResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer(PreviewResponses.paged(memberProjects(callerId).filter(_.organizationId == organizationId).map(toResp), page))
    )

  override def findById(id: UUID): TgRespIO[FindProjectResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(PreviewResponses.fromOption(projects.projects.find(id).map(toResp)))
    )

  override def findMembers(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[SearchAccountDto]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(PreviewResponses.paged(accounts.searchDtos(projects.memberIds(projectId)), page))
    )

  override def searchByLabels(
    projectLabelIds: Seq[UUID],
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindProjectResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer {
        val matching = memberProjects(callerId).filter(project => projectLabelIds.forall(project.projectLabelIds.contains))
        PreviewResponses.paged(matching.map(toResp), page)
      }
    )

  private def memberProjects(accountId: UUID): List[PreviewProject] = {
    val ids = projects.projectIdsOf(accountId)
    projects.projects.filter(project => ids.contains(project.id)).sortBy(project => (project.name, project.id.toString))
  }

  private def toResp(project: PreviewProject): FindProjectResp =
    FindProjectResp(
      id = project.id,
      organizationId = project.organizationId,
      name = project.name,
      description = project.description,
      icon = project.icon,
      workDays = project.workDays,
      members = accounts.searchDtos(projects.memberIds(project.id)),
      projectLabels = labelsOf(project.projectLabelIds)
    )
}
