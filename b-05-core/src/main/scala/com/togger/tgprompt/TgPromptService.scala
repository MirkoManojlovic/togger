package com.togger.tgprompt

import cats.effect.IO
import cats.implicits._
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.logging.TgLogging
import com.togger.tgprompt.TgPromptError._
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.{ConnectionIO, Transactor}

import java.time.Instant
import java.util.UUID

/** Every method that touches an existing prompt takes the id of the calling account and reports a prompt of another
  * account as not found.
  */
class TgPromptService(
  tgPromptRepo: TgPromptRepo,
  tgPromptVersionRepo: TgPromptVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  type Result[A] = Either[TgPromptError, A]

  def create(tgPrompt: TgPrompt, firstVersion: TgPromptVersion): IO[TgPromptFull] = {
    logger.info(s"Creating ${sp(tgPrompt)} with ${sp(firstVersion)}")
    (for {
      saved <- tgPromptRepo.insert(tgPrompt)
      _ <- tgPromptVersionRepo.insert(firstVersion.copy(tgPromptId = saved.id))
      full <- tgPromptRepo.findFullById(saved.id)
    } yield full).transact(transactor)
  }

  def saveNewVersion(
    accountId: UUID,
    id: UUID,
    title: String,
    prompt: String,
    now: Instant
  ): IO[Result[TgPromptFull]] = {
    logger.info(s"Saving new version of prompt ${sp(id)}")
    withOwnedPrompt(accountId, id) {
      for {
        currentVersion <- tgPromptVersionRepo.findLatestByTgPromptId(id)
        newVersion = TgPromptVersion(UUID.randomUUID(), id, title, prompt, now)
        _ <-
          if (currentVersion.hasSameContentAs(newVersion)) currentVersion.pure[ConnectionIO]
          else tgPromptVersionRepo.insert(newVersion)
        full <- tgPromptRepo.findFullById(id)
      } yield full.asRight[TgPromptError]
    }
  }

  def findById(accountId: UUID, id: UUID): IO[Result[TgPromptFull]] =
    withOwnedPrompt(accountId, id)(tgPromptRepo.findFullById(id).map(_.asRight[TgPromptError]))

  def findPaginated(accountId: UUID, page: DbPageRequest): IO[PaginatedResponse[TgPromptFull]] =
    tgPromptRepo.findPaginatedFullByCreatedBy(accountId, page).transact(transactor)

  def listVersions(
    accountId: UUID,
    id: UUID,
    page: DbPageRequest
  ): IO[Result[PaginatedResponse[TgPromptVersion]]] =
    withOwnedPrompt(accountId, id)(
      tgPromptVersionRepo.findPaginatedByTgPromptId(id, page).map(_.asRight[TgPromptError])
    )

  def deleteVersion(accountId: UUID, version: UUID): IO[Result[Int]] = {
    logger.info(s"Deleting prompt version ${sp(version)}")
    (for {
      existingVersion <- tgPromptVersionRepo.findByVersion(version)
      ownedCheck <- ownedPrompt(accountId, existingVersion.tgPromptId)
      result <- ownedCheck.flatTraverse { _ =>
        tgPromptVersionRepo
          .countByTgPromptId(existingVersion.tgPromptId)
          .flatMap(count =>
            if (count <= 1) (CannotDeleteLastVersion: TgPromptError).asLeft[Int].pure[ConnectionIO]
            else tgPromptVersionRepo.deleteByVersion(version).map(_.asRight[TgPromptError])
          )
      }
    } yield result).transact(transactor).recover { case UnexpectedEnd => VersionNotFound.asLeft[Int] }
  }

  def delete(accountId: UUID, id: UUID): IO[Result[Int]] = {
    logger.info(s"Deleting prompt ${sp(id)}")
    withOwnedPrompt(accountId, id)(tgPromptRepo.deleteById(id).map(_.asRight[TgPromptError]))
  }

  private def ownedPrompt(accountId: UUID, id: UUID): ConnectionIO[Result[TgPrompt]] =
    tgPromptRepo
      .findById(id)
      .map(prompt => Either.cond(prompt.createdBy == accountId, prompt, TgPromptNotFound: TgPromptError))

  private def withOwnedPrompt[A](accountId: UUID, id: UUID)(action: => ConnectionIO[Result[A]]): IO[Result[A]] =
    ownedPrompt(accountId, id)
      .flatMap(_.flatTraverse(_ => action))
      .transact(transactor)
      .recover { case UnexpectedEnd => TgPromptNotFound.asLeft[A] }
}
