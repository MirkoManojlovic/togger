package com.togger.tgaiaction

import cats.effect.IO
import cats.implicits._
import com.togger.ai.TgAiEndpoints
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.logging.TgLogging
import com.togger.project.ProjectAccountRepo
import com.togger.tgaiaction.TgAiActionError._
import com.togger.tgprompttemplate.TgPromptTemplateRepo
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.{ConnectionIO, Transactor}

import java.time.Instant
import java.util.UUID

/** Every method that touches an existing action takes the id of the calling account and reports an action of another
  * account as not found.
  */
class TgAiActionService(
  tgAiActionRepo: TgAiActionRepo,
  tgAiActionEndpointRepo: TgAiActionEndpointRepo,
  tgAiActionDefaultRepo: TgAiActionDefaultRepo,
  tgPromptTemplateRepo: TgPromptTemplateRepo,
  projectAccountRepo: ProjectAccountRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  type Result[A] = Either[TgAiActionError, A]

  def create(action: TgAiAction, endpointIds: List[Int]): IO[Result[TgAiActionWithBindings]] = {
    logger.info(s"Creating ${sp(action)} bound to ${sp(endpointIds)}")
    validate(action, endpointIds, exceptId = None).flatMap {
      case Some(error) => error.asLeft[TgAiActionWithBindings].pure[ConnectionIO]
      case None =>
        for {
          saved <- tgAiActionRepo.insert(action)
          _ <- tgAiActionEndpointRepo.replaceBindings(saved.id, endpointIds.distinct)
          full <- withBindings(saved)
        } yield full.asRight[TgAiActionError]
    }.transact(transactor)
  }

  def update(action: TgAiAction, endpointIds: List[Int]): IO[Result[TgAiActionWithBindings]] = {
    logger.info(s"Updating ${sp(action)} bound to ${sp(endpointIds)}")
    withOwnedAction(action.createdBy, action.id) { _ =>
      validate(action, endpointIds, exceptId = Some(action.id)).flatMap {
        case Some(error) => error.asLeft[TgAiActionWithBindings].pure[ConnectionIO]
        case None =>
          for {
            saved <- tgAiActionRepo.update(action)
            _ <- tgAiActionEndpointRepo.replaceBindings(saved.id, endpointIds.distinct)
            full <- withBindings(saved)
          } yield full.asRight[TgAiActionError]
      }
    }
  }

  def findById(accountId: UUID, id: UUID): IO[Result[TgAiActionWithBindings]] =
    withOwnedAction(accountId, id)(action => withBindings(action).map(_.asRight[TgAiActionError]))

  def findPaginated(accountId: UUID, page: DbPageRequest): IO[PaginatedResponse[TgAiActionWithBindings]] =
    (for {
      actions <- tgAiActionRepo.findPaginatedByCreatedBy(accountId, page)
      bindings <- tgAiActionEndpointRepo.findByActionIds(actions.items.map(_.id).toList)
      endpointIdsByAction = bindings.groupMap(_.tgAiActionId)(_.aiEndpointId)
    } yield actions.mapItem(action => TgAiActionWithBindings(action, endpointIdsByAction.getOrElse(action.id, Nil))))
      .transact(transactor)

  def delete(accountId: UUID, id: UUID): IO[Result[Int]] = {
    logger.info(s"Deleting AI action ${sp(id)}")
    withOwnedAction(accountId, id)(_ => tgAiActionRepo.deleteById(id).map(_.asRight[TgAiActionError]))
  }

  /** Makes the action the default of the account for the endpoint within the project. */
  def setDefault(default: TgAiActionDefault): IO[Result[TgAiActionDefault]] = {
    logger.info(s"Setting ${sp(default)}")
    (for {
      member <- projectAccountRepo.isMember(default.projectId, default.accountId)
      result <-
        if (!member) (NotAProjectMember: TgAiActionError).asLeft[TgAiActionDefault].pure[ConnectionIO]
        else
          ownedAction(default.accountId, default.tgAiActionId).flatMap(_.flatTraverse(_ => saveDefault(default)))
    } yield result).transact(transactor).recover { case UnexpectedEnd => TgAiActionNotFound.asLeft[TgAiActionDefault] }
  }

  def clearDefault(accountId: UUID, projectId: UUID, endpointId: Int): IO[Int] = {
    logger.info(s"Clearing AI action default of ${sp(accountId)} for ${sp(projectId)} and endpoint $endpointId")
    tgAiActionDefaultRepo.delete(accountId, projectId, endpointId).transact(transactor)
  }

  def findDefaults(accountId: UUID, projectId: UUID): IO[List[TgAiActionDefault]] =
    tgAiActionDefaultRepo.findByProject(accountId, projectId).transact(transactor)

  private def saveDefault(default: TgAiActionDefault): ConnectionIO[Result[TgAiActionDefault]] =
    tgAiActionEndpointRepo.isBound(default.tgAiActionId, default.aiEndpointId).flatMap {
      case true  => tgAiActionDefaultRepo.upsert(default).map(_.asRight[TgAiActionError])
      case false => (ActionNotBoundToEndpoint: TgAiActionError).asLeft[TgAiActionDefault].pure[ConnectionIO]
    }

  private def validate(action: TgAiAction, endpointIds: List[Int], exceptId: Option[UUID]): ConnectionIO[Option[TgAiActionError]] =
    if (endpointIds.exists(id => TgAiEndpoints.withValueOpt(id).isEmpty))
      Option[TgAiActionError](UnknownEndpoint).pure[ConnectionIO]
    else
      for {
        duplicate <- tgAiActionRepo.existsByName(action.createdBy, action.name, exceptId)
        templateOk <- action.tgPromptTemplateId.fold(true.pure[ConnectionIO])(ownsPromptTemplate(action.createdBy, _))
      } yield
        if (duplicate) Some(DuplicateName)
        else if (!templateOk) Some(UnknownPromptTemplate)
        else None

  private def ownsPromptTemplate(accountId: UUID, templateId: UUID): ConnectionIO[Boolean] =
    tgPromptTemplateRepo
      .findById(templateId)
      .map(_.createdBy == accountId)
      .recover { case UnexpectedEnd => false }

  private def withBindings(action: TgAiAction): ConnectionIO[TgAiActionWithBindings] =
    tgAiActionEndpointRepo
      .findByActionIds(List(action.id))
      .map(bindings => TgAiActionWithBindings(action, bindings.map(_.aiEndpointId)))

  private def ownedAction(accountId: UUID, id: UUID): ConnectionIO[Result[TgAiAction]] =
    tgAiActionRepo
      .findById(id)
      .map(action => Either.cond(action.createdBy == accountId, action, TgAiActionNotFound: TgAiActionError))

  private def withOwnedAction[A](accountId: UUID, id: UUID)(action: TgAiAction => ConnectionIO[Result[A]]): IO[Result[A]] =
    ownedAction(accountId, id)
      .flatMap(_.flatTraverse(action))
      .transact(transactor)
      .recover { case UnexpectedEnd => TgAiActionNotFound.asLeft[A] }
}
