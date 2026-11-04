package com.togger.milestonesteptemplate

import cats.effect.IO
import cats.implicits._
import com.togger.common.DbPageRequest
import com.togger.common.PaginatedResponse
import com.togger.logging.TgLogging
import com.togger.milestonesteptemplate.MilestoneStepTemplateError._
import com.togger.milestonetemplate.MilestoneTemplateRepo
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.Transactor
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd

import java.time.Instant
import java.util.UUID

class MilestoneStepTemplateService(
  templateRepo: MilestoneStepTemplateRepo,
  templateVersionRepo: MilestoneStepTemplateVersionRepo,
  milestoneTemplateRepo: MilestoneTemplateRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  type Result[A] = Either[MilestoneStepTemplateError, A]

  def create(
    template: MilestoneStepTemplate,
    firstVersion: MilestoneStepTemplateVersion
  ): IO[MilestoneStepTemplateFull] = {
    logger.info(s"Creating ${sp(template)} with ${sp(firstVersion)}")
    (for {
      savedTemplate <- templateRepo.insert(template)
      _ <- templateVersionRepo.insert(firstVersion.copy(milestoneStepTemplateId = savedTemplate.id))
      full <- templateRepo.findFullById(savedTemplate.id)
    } yield full).transact(transactor)
  }

  /** Saves a new version only when the content changed. */
  def update(id: UUID, name: String, description: String, now: Instant): IO[Result[MilestoneStepTemplateFull]] = {
    logger.info(s"Updating milestone step template ${sp(id)}")
    (for {
      _ <- templateRepo.findById(id)
      currentVersion <- templateVersionRepo.findLatestByTemplateId(id)
      newVersion = MilestoneStepTemplateVersion(UUID.randomUUID(), id, name, description, now)
      _ <-
        if (currentVersion.hasSameContentAs(newVersion)) ().pure[doobie.ConnectionIO]
        else templateVersionRepo.insert(newVersion).void
      full <- templateRepo.findFullById(id)
    } yield full).transact(transactor).map(_.asRight[MilestoneStepTemplateError]).recover(notFound)
  }

  def delete(id: UUID): IO[Result[Int]] = {
    logger.info(s"Deleting milestone step template ${sp(id)}")
    (for {
      usedBy <- milestoneTemplateRepo.findNamesUsingStepTemplate(id)
      result <-
        if (usedBy.nonEmpty) (StepTemplateInUse(usedBy): MilestoneStepTemplateError).asLeft[Int].pure[ConnectionIO]
        else
          templateRepo
            .deleteById(id)
            .map(deleted =>
              Either.cond(deleted > 0, deleted, MilestoneStepTemplateNotFound: MilestoneStepTemplateError)
            )
    } yield result).transact(transactor)
  }

  def findById(id: UUID): IO[Result[MilestoneStepTemplateFull]] =
    templateRepo.findFullById(id).transact(transactor).map(_.asRight[MilestoneStepTemplateError]).recover(notFound)

  def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[MilestoneStepTemplateFull]] =
    templateRepo.findPaginatedFullByProjectId(projectId, page).transact(transactor)

  def listVersions(
    templateId: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[MilestoneStepTemplateVersion]] =
    templateVersionRepo.findPaginatedByTemplateId(templateId, page).transact(transactor)

  private def notFound[A]: PartialFunction[Throwable, Result[A]] = { case UnexpectedEnd =>
    MilestoneStepTemplateNotFound.asLeft[A]
  }
}
