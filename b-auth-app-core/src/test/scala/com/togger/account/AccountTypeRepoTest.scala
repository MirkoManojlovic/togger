package com.togger.accountType

import cats.implicits._
import com.togger.account.{AccountTgDbSpec, AccountType, AccountTypeRepo}
import com.togger.common.{TgIds, TimeHelpers}
import doobie.implicits._
import doobie.postgres.implicits._

trait TgAccount { self: TimeHelpers =>
  val password = "A!good_1_password_is_good"

  val accountType = AccountType(
    id = TgIds.UNASSIGNED_ACCOUNT_ID,
    name = "Developer",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )
}

class AccountRepoTest extends AccountTgDbSpec with TimeHelpers with TgAccount {

  val accountRepo = new AccountTypeRepo
  val drops = dropAll(Seq(AccountTypeRepo.ACCOUNT_TYPE_TABLE)).sequence

  "AccountRepo" should {

    "do basic crud operations" in { transactor =>
      val res = for {
        _ <- drops
        savedAccount <- accountRepo.insert(accountType)
        foundAccount1 <- accountRepo.findById(savedAccount.id)
        newUpdatedAt = nowAsInstant
        updatedAccount <- accountRepo.update(savedAccount.copy(name = "Manager"), newUpdatedAt)
        foundAccount2 <- accountRepo.findById(savedAccount.id)
        _ <- accountRepo.deleteById(foundAccount2.id)
        foundAccount3 <- accountRepo.findById(savedAccount.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedAccount shouldBe foundAccount1)
        cp(updatedAccount shouldBe foundAccount2)
        cp.reportAll()
        foundAccount3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

  }

}
