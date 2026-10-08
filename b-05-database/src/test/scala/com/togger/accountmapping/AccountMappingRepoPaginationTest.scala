package com.togger.accountmapping

import cats.implicits._
import com.togger.account.{Account, AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PaginationTestSupport, TgIds, TimeHelpers}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class AccountMappingRepoPaginationTest
    extends BusinessTgDbSpec
    with TgAccount
    with TimeHelpers
    with PaginationTestSupport {

  private val repo = new AccountMappingRepoImpl
  private val accountRepo = new AccountRepoImpl

  // Small increasing ids compare the same way in Postgres (bytes) and in Scala.
  private def dumpId(index: Int): UUID = new UUID(0L, index.toLong)

  private def newMapping(accountIdDb: UUID, accountIdDump: UUID) =
    AccountMapping(accountIdDb, accountIdDump, nowAsInstant, nowAsInstant)

  private def newAccount(accountTypeId: UUID, index: Int) =
    Account(
      id = TgIds.UNASSIGNED_UUID,
      firstName = "Jane",
      lastName = s"Doe $index",
      email = s"jane$index@email.com",
      password = passwordHashed,
      verified = true,
      enabled = true,
      accountTypeId = accountTypeId,
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )

  private def readByDb(accountIdDb: UUID)(page: DbPageRequest) = repo.findPaginatedByAccountIdDb(accountIdDb, page)

  private def readByDump(accountIdDump: UUID)(page: DbPageRequest) =
    repo.findPaginatedByAccountIdDump(accountIdDump, page)

  testNameFromClassName should {

    "split 11 mappings of a db account over 3 pages of 5 ordered by dump id" in { transactor =>
      (for {
        account <- createAccount
        _ <- (0 to 10).toList.reverse.traverse(index => repo.insert(newMapping(account.id, dumpId(index))))
        result <- assertElevenRowsOverThreePages(readByDb(account.id), (0 to 10).map(dumpId))(_.accountIdDump)
      } yield result).transact(transactor)
    }

    "split 11 mappings of a dump account over 3 pages of 5 ordered by db id" in { transactor =>
      (for {
        first <- createAccount
        others <- (1 to 10).toList.traverse(index => accountRepo.insert(newAccount(first.accountTypeId, index)))
        accounts = first :: others
        _ <- accounts.traverse(account => repo.insert(newMapping(account.id, dumpId(1))))
        result <- assertElevenRowsOverThreePages(readByDump(dumpId(1)), accounts.map(_.id).sortBy(_.toString))(
          _.accountIdDb
        )
      } yield result).transact(transactor)
    }

    "only see the mappings of the asked account" in { transactor =>
      (for {
        account <- createAccount
        other <- accountRepo.insert(newAccount(account.accountTypeId, 1))
        mine <- repo.insert(newMapping(account.id, dumpId(1)))
        _ <- repo.insert(newMapping(other.id, dumpId(2)))
        found <- repo.findPaginatedByAccountIdDb(account.id, pageRequest(1, 100))
        unknown <- repo.findPaginatedByAccountIdDump(dumpId(99), pageRequest(1, 100))
      } yield {
        found.items shouldBe List(mine)
        unknown.items shouldBe empty
        unknown.totalNumberOfItems shouldBe 0L
      }).transact(transactor)
    }
  }
}
