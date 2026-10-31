package com.togger.ui.milestone

import cats.effect.IO
import com.togger.common.DateRangeValidation
import com.togger.common.TgError._
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.{CreateMilestoneReq, FindMilestoneResp, MilestoneFilter, UpdateMilestoneReq}
import com.togger.milestone.MilestoneStatus
import com.togger.preview._

import java.time.DayOfWeek
import java.util.UUID

class MilestoneFeServicePreview(
  ctx: PreviewContext,
  projects: ProjectsPreviewData,
  data: MilestonesPreviewData,
  dateRangeValidation: DateRangeValidation = new DateRangeValidation
) extends MilestoneFeService {

  private val NotFoundMessage = "Milestone not found"

  override def createMilestone(dto: CreateMilestoneReq): TgRespIO[FindMilestoneResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(projects.projects.find(dto.projectId) match {
        case None => PreviewResponses.notFound[FindMilestoneResp]
        case Some(project) =>
          dateRangeValidation.validate(Some(dto.startDate), Some(dto.dueDate), None, workDays(project)) match {
            case Left(errors) => errors.toBadRequestLeftIO[FindMilestoneResp]
            case Right(_) =>
              val now = ctx.clock.currentInstant
              val created = data.milestones.upsert(
                PreviewMilestone(
                  id = UUID.randomUUID(),
                  projectId = dto.projectId,
                  createdAt = now,
                  latestVersion = UUID.randomUUID(),
                  name = dto.name,
                  description = dto.description,
                  statusValue = MilestoneStatus.InProgress.value,
                  startDate = dto.startDate,
                  dueDate = dto.dueDate,
                  endDate = None,
                  updatedAt = now
                )
              )
              PreviewResponses.ok(created.toDto)
          }
      })
    )

  override def findMilestoneById(id: UUID): TgRespIO[FindMilestoneResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        data.milestones.find(id).fold(PreviewResponses.badRequest[FindMilestoneResp](NotFoundMessage))(m =>
          PreviewResponses.ok(m.toDto)
        )
      )
    )

  override def updateMilestone(dto: UpdateMilestoneReq): TgRespIO[FindMilestoneResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(data.milestones.find(dto.id) match {
        case None => PreviewResponses.badRequest[FindMilestoneResp](NotFoundMessage)
        case Some(current) =>
          val startDate = dto.startDate.getOrElse(current.startDate)
          val dueDate = dto.dueDate.getOrElse(current.dueDate)
          val endDate = dto.endDate.orElse(current.endDate)
          val project = projects.projects.find(current.projectId)
          dateRangeValidation.validate(Some(startDate), Some(dueDate), endDate, project.fold(allDays)(workDays)) match {
            case Left(errors) => errors.toBadRequestLeftIO[FindMilestoneResp]
            case Right(_) =>
              val updated = data.milestones.upsert(
                current.copy(
                  latestVersion = UUID.randomUUID(),
                  name = dto.name,
                  description = dto.description,
                  statusValue = dto.status.getOrElse(current.status).value,
                  startDate = startDate,
                  dueDate = dueDate,
                  endDate = endDate,
                  updatedAt = ctx.clock.currentInstant
                )
              )
              PreviewResponses.ok(updated.toDto)
          }
      })
    )

  override def findByProjectId(
    projectId: UUID,
    filter: MilestoneFilter,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        val search = filter.search.map(_.trim.toLowerCase).filter(_.nonEmpty)
        val matching = data.milestones
          .filter(_.projectId == projectId)
          .filter(m => filter.statuses.isEmpty || filter.statuses.contains(m.status))
          .filter(m => search.forall(term => m.name.toLowerCase.contains(term) || m.description.toLowerCase.contains(term)))
          .sortBy(m => (m.dueDate.toEpochDay, m.id.toString))
        PreviewResponses.paged(matching.map(_.toDto), page)
      }
    )

  private val allDays: Set[DayOfWeek] = DayOfWeek.values().toSet

  private def workDays(project: PreviewProject): Set[DayOfWeek] = project.workDays.map(DayOfWeek.of)
}
