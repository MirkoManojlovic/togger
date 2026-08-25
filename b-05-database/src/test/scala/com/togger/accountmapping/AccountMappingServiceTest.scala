package com.togger.accountmapping

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.TimeHelpers
import com.togger.db.MockedTransactor
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.implicits._
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class AccountMappingServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val accountMappingRepo: AccountMappingRepo = mock[AccountMappingRepo]

  val service = new AccountMappingService(accountMappingRepo, transactorMock)

  val accountIdDb: UUID = UUID.randomUUID()
  val accountIdDump: UUID = UUID.randomUUID()

  def newAccountMapping() = AccountMapping(
    accountIdDb = accountIdDb,
    accountIdDump = accountIdDump,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "create an account mapping" in {
      val mapping = newAccountMapping()

      (accountMappingRepo.insert _).expects(mapping).returns(mapping.pure[ConnectionIO])

      service.create(mapping).map(_ shouldBe mapping).unsafeToFuture()
    }

    "find account mappings by db account id" in {
      val mapping = newAccountMapping()

      (accountMappingRepo.findByAccountIdDb _)
        .expects(accountIdDb)
        .returns(List(mapping).pure[ConnectionIO])

      service.findByAccountIdDb(accountIdDb).map(_ shouldBe List(mapping)).unsafeToFuture()
    }

    "find account mappings by dump account id" in {
      val mapping = newAccountMapping()

      (accountMappingRepo.findByAccountIdDump _)
        .expects(accountIdDump)
        .returns(List(mapping).pure[ConnectionIO])

      service.findByAccountIdDump(accountIdDump).map(_ shouldBe List(mapping)).unsafeToFuture()
    }

    "delete an account mapping by its composite key" in {
      (accountMappingRepo.deleteByIds _).expects(accountIdDb, accountIdDump).returns(1.pure[ConnectionIO])

      service.deleteByIds(accountIdDb, accountIdDump).map(_ shouldBe 1).unsafeToFuture()
    }

  }

}
