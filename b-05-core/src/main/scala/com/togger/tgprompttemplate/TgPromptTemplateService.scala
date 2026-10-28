package com.togger.tgprompttemplate

import cats.effect.IO
import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.logging.TgLogging
import com.togger.tgprompttemplate.TgPromptTemplateError._
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.{ConnectionIO, Transactor}

import java.time.Instant
import java.util.UUID

/** Every method that touches an existing prompt takes the id of the calling account and reports a prompt of another
  * account as not found.
  */
class TgPromptTemplateService(
  tgPromptTemplateRepo: TgPromptTemplateRepo,
  tgPromptTemplateVersionRepo: TgPromptTemplateVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  type Result[A] = Either[TgPromptTemplateError, A]

  def create(tgPromptTemplate: TgPromptTemplate, firstVersion: TgPromptTemplateVersion): IO[TgPromptTemplateFull] = {
    logger.info(s"Creating ${sp(tgPromptTemplate)} with ${sp(firstVersion)}")
    (for {
      saved <- tgPromptTemplateRepo.insert(tgPromptTemplate)
      _ <- tgPromptTemplateVersionRepo.insert(firstVersion.copy(tgPromptTemplateId = saved.id))
      full <- tgPromptTemplateRepo.findFullById(saved.id)
    } yield full).transact(transactor)
  }

  def saveNewVersion(
    accountId: UUID,
    id: UUID,
    title: String,
    prompt: String,
    now: Instant
  ): IO[Result[TgPromptTemplateFull]] = {
    logger.info(s"Saving new version of prompt ${sp(id)}")
    withOwnedPrompt(accountId, id) {
      for {
        currentVersion <- tgPromptTemplateVersionRepo.findLatestByTgPromptTemplateId(id)
        newVersion = TgPromptTemplateVersion(UUID.randomUUID(), id, title, prompt, now)
        _ <-
          if (currentVersion.hasSameContentAs(newVersion)) currentVersion.pure[ConnectionIO]
          else tgPromptTemplateVersionRepo.insert(newVersion)
        full <- tgPromptTemplateRepo.findFullById(id)
      } yield full.asRight[TgPromptTemplateError]
    }
  }

  def findById(accountId: UUID, id: UUID): IO[Result[TgPromptTemplateFull]] =
    withOwnedPrompt(accountId, id)(tgPromptTemplateRepo.findFullById(id).map(_.asRight[TgPromptTemplateError]))

  def findPaginated(accountId: UUID, page: DbPageRequest): IO[PaginatedResponse[TgPromptTemplateFull]] =
    tgPromptTemplateRepo.findPaginatedFullByCreatedBy(accountId, page).transact(transactor)

  def listVersions(
    accountId: UUID,
    id: UUID,
    page: DbPageRequest
  ): IO[Result[PaginatedResponse[TgPromptTemplateVersion]]] =
    withOwnedPrompt(accountId, id)(
      tgPromptTemplateVersionRepo.findPaginatedByTgPromptTemplateId(id, page).map(_.asRight[TgPromptTemplateError])
    )

  def deleteVersion(accountId: UUID, version: UUID): IO[Result[Int]] = {
    logger.info(s"Deleting prompt version ${sp(version)}")
    (for {
      existingVersion <- tgPromptTemplateVersionRepo.findByVersion(version)
      ownedCheck <- ownedPrompt(accountId, existingVersion.tgPromptTemplateId)
      result <- ownedCheck.flatTraverse { _ =>
        tgPromptTemplateVersionRepo
          .countByTgPromptTemplateId(existingVersion.tgPromptTemplateId)
          .flatMap(count =>
            if (count <= 1) (CannotDeleteLastVersion: TgPromptTemplateError).asLeft[Int].pure[ConnectionIO]
            else tgPromptTemplateVersionRepo.deleteByVersion(version).map(_.asRight[TgPromptTemplateError])
          )
      }
    } yield result).transact(transactor).recover { case UnexpectedEnd => VersionNotFound.asLeft[Int] }
  }

  def delete(accountId: UUID, id: UUID): IO[Result[Int]] = {
    logger.info(s"Deleting prompt ${sp(id)}")
    withOwnedPrompt(accountId, id)(tgPromptTemplateRepo.deleteById(id).map(_.asRight[TgPromptTemplateError]))
  }

  private def ownedPrompt(accountId: UUID, id: UUID): ConnectionIO[Result[TgPromptTemplate]] =
    tgPromptTemplateRepo
      .findById(id)
      .map(prompt => Either.cond(prompt.createdBy == accountId, prompt, TgPromptTemplateNotFound: TgPromptTemplateError))

  private def withOwnedPrompt[A](accountId: UUID, id: UUID)(action: => ConnectionIO[Result[A]]): IO[Result[A]] =
    ownedPrompt(accountId, id)
      .flatMap(_.flatTraverse(_ => action))
      .transact(transactor)
      .recover { case UnexpectedEnd => TgPromptTemplateNotFound.asLeft[A] }
}
