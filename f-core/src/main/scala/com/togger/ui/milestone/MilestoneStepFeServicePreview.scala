package com.togger.ui.milestone

import cats.effect.IO
import cats.implicits._
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{DateRangeValidation, PageRequest, PaginatedResponse, TgError}
import com.togger.dto._
import com.togger.milestone.MilestoneStepStatus
import com.togger.preview._

import java.time.{DayOfWeek, Instant, LocalDate}
import java.util.UUID

/** Mirrors the backend rules: dates inside the milestone and on work days, a comment on every change, a closed step has
  * an end date, and the position is part of the version.
  */
class MilestoneStepFeServicePreview(
  ctx: PreviewContext,
  projects: ProjectsPreviewData,
  milestones: MilestonesPreviewData,
  data: MilestoneStepsPreviewData,
  dateRangeValidation: DateRangeValidation = new DateRangeValidation
) extends MilestoneStepFeService {

  private val StepNotFound = "Milestone step not found"
  private val MilestoneNotFound = "Milestone not found"
  private val ChangeCommentEmpty = "Change comment must not be empty"
  private val EndDateRequiredWhenClosed = "End date is required when the status is Done or Abandoned"
  private val StepsDoNotMatchMilestone = "The ordered steps must be exactly the steps of the milestone"
  private val LastStepOfMilestone = "The last step of a milestone cannot be deleted"

  private def stepHasTasks(count: Int) =
    s"The step has $count ${if (count == 1) "task" else "tasks"}; move or delete them before deleting the step"

  private val MoveForNewStepComment = "Moved to make room for a new step"

  override def createMilestoneStep(dto: CreateMilestoneStepReq): TgRespIO[FindMilestoneStepResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(milestones.milestones.find(dto.milestoneId) match {
        case None => PreviewResponses.badRequest[FindMilestoneStepResp](MilestoneNotFound)
        case Some(milestone) =>
          validateDates(milestone, dto.startDate, dto.dueDate, None) match {
            case Left(errors) => errors.toBadRequestLeftIO[FindMilestoneStepResp]
            case Right(_)     => PreviewResponses.ok(create(milestone, dto))
          }
      })
    )

  override def updateMilestoneStep(dto: UpdateMilestoneStepReq): TgRespIO[FindMilestoneStepResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(stepWithMilestone(dto.id) match {
        case None => PreviewResponses.badRequest[FindMilestoneStepResp](StepNotFound)
        case Some((step, current, milestone)) =>
          val validation = List(
            validateDates(milestone, dto.startDate, dto.dueDate, dto.endDate),
            Either.cond(
              !dto.status.isClosing || dto.endDate.isDefined,
              (),
              cats.data.NonEmptyChain.one[TgError](Message(EndDateRequiredWhenClosed))
            ),
            validateComment(dto.changeComment)
          ).traverse_(_.toValidated).toEither
          validation match {
            case Left(errors) => errors.toBadRequestLeftIO[FindMilestoneStepResp]
            case Right(_) =>
              val now = ctx.clock.currentInstant
              val saved = data.stepVersions.upsert(
                current.copy(
                  version = UUID.randomUUID(),
                  name = dto.name,
                  description = dto.description,
                  statusValue = dto.status.value,
                  startDate = dto.startDate,
                  dueDate = dto.dueDate,
                  endDate = dto.endDate,
                  createdAt = now,
                  updatedAt = now,
                  changeComment = dto.changeComment.trim
                )
              )
              PreviewResponses.ok(toDto(step, saved))
          }
      })
    )

  override def reorderMilestoneSteps(dto: ReorderMilestoneStepsReq): TgRespIO[List[FindMilestoneStepResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        val current = data.latestVersionsOf(dto.milestoneId)
        if (dto.changeComment.trim.isEmpty) PreviewResponses.badRequest[List[FindMilestoneStepResp]](ChangeCommentEmpty)
        else if (current.map(_.milestoneStepId).toSet != dto.stepIds.toSet || dto.stepIds.distinct.size != current.size)
          PreviewResponses.badRequest[List[FindMilestoneStepResp]](StepsDoNotMatchMilestone)
        else {
          val byStepId = current.map(version => version.milestoneStepId -> version).toMap
          renumber(dto.stepIds.map(byStepId), 0, dto.changeComment.trim)
          PreviewResponses.ok(stepsOf(dto.milestoneId))
        }
      }
    )

  override def findByMilestoneId(milestoneId: UUID): TgRespIO[List[FindMilestoneStepResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ => IO.defer(PreviewResponses.ok(stepsOf(milestoneId))))

  override def findByProjectId(projectId: UUID): TgRespIO[List[FindMilestoneStepResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.ok(
          milestones.milestones
            .filter(_.projectId == projectId)
            .sortBy(_.id.toString)
            .flatMap(milestone => stepsOf(milestone.id))
        )
      )
    )

  override def findMilestoneStepById(id: UUID): TgRespIO[FindMilestoneStepResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        data.steps
          .find(id)
          .flatMap(step => data.latestVersion(id).map(toDto(step, _)))
          .fold(PreviewResponses.badRequest[FindMilestoneStepResp](StepNotFound))(PreviewResponses.ok(_))
      )
    )

  override def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[MilestoneStepVersionDto]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.paged(data.stepVersions.filter(_.milestoneStepId == id).reverse.map(toVersionDto), page)
      )
    )

  override def deleteMilestoneStepById(id: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        val step = data.steps.find(id)
        if (step.isEmpty) PreviewResponses.badRequest[TgEmptyBody](StepNotFound)
        else if (data.steps.filter(_.milestoneId == step.get.milestoneId).size <= 1)
          PreviewResponses.badRequest[TgEmptyBody](LastStepOfMilestone)
        else if (data.taskCounts(id)._1 > 0)
          PreviewResponses.badRequest[TgEmptyBody](stepHasTasks(data.taskCounts(id)._1))
        else {
          data.stepVersions.deleteWhere(_.milestoneStepId == id)
          data.steps.delete(id)
          PreviewResponses.okEmpty("Milestone step deleted successfully")
        }
      }
    )

  /** Appends the step, or inserts it at the position and moves the later steps down by one. */
  private def create(milestone: PreviewMilestone, dto: CreateMilestoneStepReq): FindMilestoneStepResp = {
    val now = ctx.clock.currentInstant
    val current = data.latestVersionsOf(milestone.id)
    val insertAt = dto.position.map(_.max(0)).filter(_ < current.size)
    val step = data.steps.upsert(PreviewMilestoneStep(UUID.randomUUID(), milestone.id, now))
    val first = data.stepVersions.upsert(
      PreviewMilestoneStepVersion(
        version = UUID.randomUUID(),
        milestoneStepId = step.id,
        name = dto.name,
        description = dto.description,
        statusValue = MilestoneStepStatus.Planned.value,
        position = insertAt.getOrElse(current.lastOption.map(_.position + 1).getOrElse(0)),
        startDate = dto.startDate,
        dueDate = dto.dueDate,
        endDate = None,
        createdAt = now,
        updatedAt = now,
        changeComment = ""
      )
    )
    insertAt.foreach { index =>
      renumber(current.take(index), 0, MoveForNewStepComment)
      renumber(current.drop(index), index + 1, MoveForNewStepComment)
    }
    toDto(step, first)
  }

  /** Gives `ordered` the positions `firstPosition`, `firstPosition + 1`, ... and versions only the ones that move. */
  private def renumber(ordered: List[PreviewMilestoneStepVersion], firstPosition: Int, comment: String): Unit = {
    val now = ctx.clock.currentInstant
    ordered.zipWithIndex.foreach { case (version, index) =>
      val position = firstPosition + index
      if (version.position != position)
        data.stepVersions.upsert(
          version.copy(
            version = UUID.randomUUID(),
            position = position,
            changeComment = comment,
            createdAt = now,
            updatedAt = now
          )
        )
    }
  }

  private def validateDates(
    milestone: PreviewMilestone,
    startDate: LocalDate,
    dueDate: LocalDate,
    endDate: Option[LocalDate]
  ) = {
    val workDays: Set[DayOfWeek] =
      projects.projects.find(milestone.projectId).fold(DayOfWeek.values().toSet)(_.workDays.map(DayOfWeek.of))
    List(
      dateRangeValidation.validate(Some(startDate), Some(dueDate), endDate, workDays),
      dateRangeValidation.validateWithinRange(startDate, dueDate, milestone.startDate, milestone.dueDate)
    ).traverse_(_.toValidated).toEither
  }

  private def validateComment(comment: String) =
    Either.cond(comment.trim.nonEmpty, (), cats.data.NonEmptyChain.one[TgError](Message(ChangeCommentEmpty)))

  private case class Message(message: String) extends TgError

  private def stepWithMilestone(id: UUID): Option[(PreviewMilestoneStep, PreviewMilestoneStepVersion, PreviewMilestone)] =
    for {
      step <- data.steps.find(id)
      version <- data.latestVersion(id)
      milestone <- milestones.milestones.find(step.milestoneId)
    } yield (step, version, milestone)

  private def stepsOf(milestoneId: UUID): List[FindMilestoneStepResp] =
    data.latestVersionsOf(milestoneId).flatMap(version => data.steps.find(version.milestoneStepId).map(toDto(_, version)))

  private def toDto(step: PreviewMilestoneStep, version: PreviewMilestoneStepVersion): FindMilestoneStepResp =
    FindMilestoneStepResp(
      id = step.id,
      milestoneId = step.milestoneId,
      createdAt = step.createdAt,
      latestVersion = version.version,
      name = version.name,
      description = version.description,
      status = version.status,
      position = version.position,
      startDate = version.startDate,
      dueDate = version.dueDate,
      endDate = version.endDate,
      updatedAt = version.updatedAt,
      changeComment = version.changeComment,
      tasksTotal = data.taskCounts(step.id)._1,
      tasksDone = data.taskCounts(step.id)._2
    )

  private def toVersionDto(version: PreviewMilestoneStepVersion): MilestoneStepVersionDto =
    MilestoneStepVersionDto(
      version = version.version,
      milestoneStepId = version.milestoneStepId,
      name = version.name,
      description = version.description,
      status = version.status,
      position = version.position,
      startDate = version.startDate,
      dueDate = version.dueDate,
      endDate = version.endDate,
      createdAt = version.createdAt,
      updatedAt = version.updatedAt,
      changeComment = version.changeComment
    )
}
