package com.togger.milestonetemplate

import cats.effect.IO
import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.logging.TgLogging
import com.togger.milestonesteptemplate.MilestoneStepTemplateRepo
import com.togger.milestonetemplate.MilestoneTemplateError._
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.{ConnectionIO, Transactor}

import java.time.Instant
import java.util.UUID

class MilestoneTemplateService(
  templateRepo: MilestoneTemplateRepo,
  templateVersionRepo: MilestoneTemplateVersionRepo,
  stepTemplateRepo: MilestoneStepTemplateRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  type Result[A] = Either[MilestoneTemplateError, A]

  def create(
    template: MilestoneTemplate,
    firstVersion: MilestoneTemplateVersion,
    stepTemplateIds: List[UUID]
  ): IO[Result[MilestoneTemplateFull]] = {
    logger.info(s"Creating ${sp(template)} with ${sp(firstVersion)} and ${sp(stepTemplateIds)}")
    (for {
      stepsCheck <- validateStepTemplates(template.projectId, stepTemplateIds)
      created <- stepsCheck.traverse { _ =>
        for {
          savedTemplate <- templateRepo.insert(template)
          savedVersion <- templateVersionRepo.insert(firstVersion.copy(milestoneTemplateId = savedTemplate.id))
          _ <- templateVersionRepo.insertSteps(savedVersion.version, stepTemplateIds)
          full <- templateRepo.findFullById(savedTemplate.id)
        } yield full
      }
    } yield created).transact(transactor)
  }

  /** Saves a new version only when the name, the description or the steps changed. */
  def update(
    id: UUID,
    name: String,
    description: String,
    stepTemplateIds: List[UUID],
    now: Instant
  ): IO[Result[MilestoneTemplateFull]] = {
    logger.info(s"Updating milestone template ${sp(id)}")
    (for {
      template <- templateRepo.findById(id)
      stepsCheck <- validateStepTemplates(template.projectId, stepTemplateIds)
      updated <- stepsCheck.traverse { _ =>
        for {
          current <- templateRepo.findFullById(id)
          _ <-
            if (current.latest.hasSameContentAs(name, description, stepTemplateIds)) ().pure[ConnectionIO]
            else saveVersion(MilestoneTemplateVersion(UUID.randomUUID(), id, name, description, now), stepTemplateIds)
          full <- templateRepo.findFullById(id)
        } yield full
      }
    } yield updated).transact(transactor).recover(notFound)
  }

  def delete(id: UUID): IO[Result[Int]] = {
    logger.info(s"Deleting milestone template ${sp(id)}")
    templateRepo
      .deleteById(id)
      .transact(transactor)
      .map(deleted => Either.cond(deleted > 0, deleted, MilestoneTemplateNotFound))
  }

  def findById(id: UUID): IO[Result[MilestoneTemplateFull]] =
    templateRepo.findFullById(id).transact(transactor).map(_.asRight[MilestoneTemplateError]).recover(notFound)

  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): IO[PaginatedResponse[MilestoneTemplateFull]] =
    templateRepo.findPaginatedFullByProjectId(projectId, page).transact(transactor)

  def listVersions(
    templateId: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[MilestoneTemplateVersionWithSteps]] =
    (for {
      versionsPage <- templateVersionRepo.findPaginatedByTemplateId(templateId, page)
      steps <- templateVersionRepo.findStepsByVersions(versionsPage.items.map(_.version))
    } yield versionsPage.mapItem(version => MilestoneTemplateVersionWithSteps(version, steps.getOrElse(version.version, Nil))))
      .transact(transactor)

  private def saveVersion(version: MilestoneTemplateVersion, stepTemplateIds: List[UUID]): ConnectionIO[Unit] =
    templateVersionRepo.insert(version).flatMap(saved => templateVersionRepo.insertSteps(saved.version, stepTemplateIds)).void

  /** Every step template has to exist and belong to the project of the template. */
  private def validateStepTemplates(projectId: UUID, stepTemplateIds: List[UUID]): ConnectionIO[Result[Unit]] =
    stepTemplateIds.distinct
      .traverse { stepTemplateId =>
        stepTemplateRepo
          .findById(stepTemplateId)
          .map(stepTemplate => Either.cond(stepTemplate.projectId == projectId, (), StepTemplateNotInProject(stepTemplateId)))
          .recover { case UnexpectedEnd => StepTemplateNotInProject(stepTemplateId).asLeft[Unit] }
      }
      .map(_.sequence_.leftWiden[MilestoneTemplateError])

  private def notFound[A]: PartialFunction[Throwable, Result[A]] = { case UnexpectedEnd =>
    MilestoneTemplateNotFound.asLeft[A]
  }
}
