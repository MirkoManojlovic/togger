package com.togger.account

import cats.effect.IO
import cats.implicits._
import com.togger.account.AccountManagementError._
import com.togger.common.{DbPageRequest, PaginatedResponse, TgClock}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.{ConnectionIO, Transactor}

import java.sql.SQLException
import java.util.UUID

class AccountManagementService(
  accountRepo: AccountRepo,
  accountManagementRepo: AccountManagementRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  type Result[A] = Either[AccountManagementError, A]

  private val UniqueViolation = "23505"
  private val ForeignKeyViolation = "23503"

  def findPaginated(page: DbPageRequest): IO[PaginatedResponse[AccountWithProjects]] =
    accountManagementRepo.findPaginated(page).transact(transactor)

  def findAccountTypes: IO[List[AccountTypeRef]] =
    accountManagementRepo.findAccountTypes.transact(transactor)

  def update(
    id: UUID,
    firstName: String,
    lastName: String,
    email: String,
    accountTypeId: UUID
  ): IO[Result[AccountWithProjects]] = {
    logger.info(s"Updating account ${sp(id)}")
    val update: ConnectionIO[Result[Unit]] =
      for {
        types <- accountManagementRepo.findAccountTypes
        account <- accountRepo.findById(id)
        result <-
          if (!types.exists(_.id == accountTypeId)) (UnknownAccountType: AccountManagementError).asLeft[Unit].pure[ConnectionIO]
          else
            accountRepo
              .update(
                account.copy(firstName = firstName, lastName = lastName, email = email, accountTypeId = accountTypeId),
                tgClock.currentInstant
              )
              .as(().asRight[AccountManagementError])
      } yield result

    update
      .transact(transactor)
      .recover {
        case UnexpectedEnd                                       => (AccountNotFound: AccountManagementError).asLeft[Unit]
        case e: SQLException if e.getSQLState == UniqueViolation => (EmailAlreadyInUse: AccountManagementError).asLeft[Unit]
      }
      .flatMap {
        case Left(error) => error.asLeft[AccountWithProjects].pure[IO]
        case Right(_)    => findOne(id)
      }
  }

  def delete(callerId: UUID, id: UUID): IO[Result[Unit]] = {
    logger.info(s"Deleting account ${sp(id)}")
    if (callerId == id) (CannotDeleteOwnAccount: AccountManagementError).asLeft[Unit].pure[IO]
    else
      accountManagementRepo
        .deleteWithMemberships(id)
        .transact(transactor)
        .map(deleted => if (deleted > 0) ().asRight[AccountManagementError] else AccountNotFound.asLeft[Unit])
        .recover {
          case e: SQLException if e.getSQLState == ForeignKeyViolation => (AccountInUse: AccountManagementError).asLeft[Unit]
        }
  }

  def findById(id: UUID): IO[Result[AccountWithProjects]] = findOne(id)

  private def findOne(id: UUID): IO[Result[AccountWithProjects]] =
    accountManagementRepo
      .findOne(id)
      .transact(transactor)
      .map(_.toRight[AccountManagementError](AccountNotFound))
}
