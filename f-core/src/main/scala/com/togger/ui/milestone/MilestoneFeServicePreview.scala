package com.togger.ui.milestone

import cats.effect.IO
import cats.implicits._
import com.togger.common.DateRangeValidation
import com.togger.common.PageRequest
import com.togger.common.PaginatedResponse
import com.togger.common.TgError
import com.togger.common.TgError._
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.CreateMilestoneReq
import com.togger.dto.FindMilestoneResp
import com.togger.dto.MilestoneFilter
import com.togger.dto.NewMilestoneStepReq
import com.togger.dto.UpdateMilestoneReq
import com.togger.milestone.DefaultMilestoneStep
import com.togger.milestone.MilestoneStatus
import com.togger.milestone.MilestoneStepStatus
import com.togger.preview._

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

class MilestoneFeServicePreview(
  ctx: PreviewContext,
  projects: ProjectsPreviewData,
  data: MilestonesPreviewData,
  steps: MilestoneStepsPreviewData,
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
              stepErrors(dto, workDays(project)) match {
                case Left(errors) => errors.toBadRequestLeftIO[FindMilestoneResp]
                case Right(_)     => IO.defer(PreviewResponses.ok(storeMilestone(dto)))
              }
          }
      })
    )

  /** Like the backend: the step dates are checked the way the milestone dates are, and against the milestone range. */
  private def stepErrors(dto: CreateMilestoneReq, projectWorkDays: Set[DayOfWeek]) = {
    val perStep = dto.steps.map { step =>
      List(
        dateRangeValidation.validate(Some(step.startDate), Some(step.dueDate), None, projectWorkDays),
        dateRangeValidation.validateWithinRange(step.startDate, step.dueDate, dto.startDate, dto.dueDate)
      ).traverse_(_.toValidated).toEither.leftMap(_.map(error => StepError(step.name, error.message): TgError))
    }
    perStep.traverse_(_.toValidated).toEither
  }

  private case class StepError(stepName: String, reason: String) extends TgError {
    override def message: String = s"Step '$stepName': $reason"
  }

  private def storeMilestone(dto: CreateMilestoneReq): FindMilestoneResp = {
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
    // like the backend: the Shelf step is always the first step
    val shelf = NewMilestoneStepReq(
      DefaultMilestoneStep.Shelf.stepName,
      DefaultMilestoneStep.Shelf.description,
      dto.startDate,
      dto.dueDate
    )
    (shelf +: dto.steps).zipWithIndex.foreach { case (step, position) =>
      val stored = steps.steps.upsert(PreviewMilestoneStep(UUID.randomUUID(), created.id, now))
      steps.stepVersions.upsert(
        PreviewMilestoneStepVersion(
          version = UUID.randomUUID(),
          milestoneStepId = stored.id,
          name = step.name,
          description = step.description,
          statusValue = MilestoneStepStatus.Planned.value,
          position = position,
          startDate = step.startDate,
          dueDate = step.dueDate,
          endDate = None,
          createdAt = now,
          updatedAt = now,
          changeComment = ""
        )
      )
    }
    withCounts(created)
  }

  private def withCounts(milestone: PreviewMilestone): FindMilestoneResp = {
    val (total, closed) = steps.counts(milestone.id)
    milestone.toDto.copy(stepsTotal = total, stepsClosed = closed)
  }

  override def findMilestoneById(id: UUID): TgRespIO[FindMilestoneResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        data.milestones
          .find(id)
          .fold(PreviewResponses.badRequest[FindMilestoneResp](NotFoundMessage))(m =>
            PreviewResponses.ok(withCounts(m))
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
            case Right(_) if stepsOutside(dto.id, startDate, dueDate).nonEmpty =>
              PreviewResponses.badRequest[FindMilestoneResp](
                s"These steps would lie outside the milestone range $startDate - $dueDate: " +
                  stepsOutside(dto.id, startDate, dueDate).map(_.name).mkString(", ")
              )
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
              PreviewResponses.ok(withCounts(updated))
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
          .filter(m =>
            search.forall(term => m.name.toLowerCase.contains(term) || m.description.toLowerCase.contains(term))
          )
          .sortBy(m => (m.dueDate.toEpochDay, m.id.toString))
        PreviewResponses.paged(matching.map(withCounts), page)
      }
    )

  private def stepsOutside(milestoneId: UUID, start: LocalDate, due: LocalDate) =
    steps
      .latestVersionsOf(milestoneId)
      .filterNot(step => DefaultMilestoneStep.isDefaultName(step.name))
      .filter(step => step.startDate.isBefore(start) || step.dueDate.isAfter(due))

  private val allDays: Set[DayOfWeek] = DayOfWeek.values().toSet

  private def workDays(project: PreviewProject): Set[DayOfWeek] = project.workDays.map(DayOfWeek.of)
}
