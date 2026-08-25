package com.togger.accountmapping

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import doobie.implicits._

import java.util.UUID

class AccountMappingRepoTest extends BusinessTgDbSpec with TgAccount with TimeHelpers {

  val accountMappingRepo = new AccountMappingRepoImpl

  def newAccountMapping(accountIdDb: UUID, accountIdDump: UUID) = AccountMapping(
    accountIdDb = accountIdDb,
    accountIdDump = accountIdDump,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        mapping = newAccountMapping(savedAccount.id, UUID.randomUUID())
        savedMapping <- accountMappingRepo.insert(mapping)
        foundByDb <- accountMappingRepo.findByAccountIdDb(savedAccount.id)
        foundByDump <- accountMappingRepo.findByAccountIdDump(mapping.accountIdDump)
        _ <- accountMappingRepo.deleteByIds(savedAccount.id, mapping.accountIdDump)
        foundAfterDelete <- accountMappingRepo.findByAccountIdDb(savedAccount.id)
      } yield {
        val cp = new Checkpoint
        cp(savedMapping.accountIdDb shouldBe savedAccount.id)
        cp(savedMapping.accountIdDump shouldBe mapping.accountIdDump)
        cp(foundByDb shouldBe List(savedMapping))
        cp(foundByDump shouldBe List(savedMapping))
        cp.reportAll()
        foundAfterDelete shouldBe empty
      }

      res.transact(transactor)
    }

    "find several mappings for the same db account from different dump sources" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        mapping1 <- accountMappingRepo.insert(newAccountMapping(savedAccount.id, UUID.randomUUID()))
        mapping2 <- accountMappingRepo.insert(newAccountMapping(savedAccount.id, UUID.randomUUID()))
        found <- accountMappingRepo.findByAccountIdDb(savedAccount.id)
      } yield {
        found.toSet shouldBe Set(mapping1, mapping2)
      }

      res.transact(transactor)
    }

    "fail to insert a duplicate composite-key mapping" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        dumpId = UUID.randomUUID()
        _ <- accountMappingRepo.insert(newAccountMapping(savedAccount.id, dumpId))
        secondInsert <- accountMappingRepo.insert(newAccountMapping(savedAccount.id, dumpId)).attempt
      } yield {
        secondInsert.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

}
