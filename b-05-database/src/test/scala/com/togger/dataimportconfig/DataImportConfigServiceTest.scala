package com.togger.dataimportconfig

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.{DbPageRequest, PaginatedResponse, TgClock, TimeHelpers}
import com.togger.db.MockedTransactor
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.implicits._
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class DataImportConfigServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val dataImportConfigRepo: DataImportConfigRepo = mock[DataImportConfigRepo]
  val tgClock: TgClock = mock[TgClock]

  val service = new DataImportConfigService(dataImportConfigRepo, tgClock, transactorMock)

  val projectId: UUID = UUID.randomUUID()
  val accountId: UUID = UUID.randomUUID()

  def newDataImportConfig() = DataImportConfig(
    id = UUID.randomUUID(),
    name = "My load config",
    importPath = "/tmp/dumps",
    projectId = projectId,
    accountId = accountId,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "create a Import data config" in {
      val config = newDataImportConfig()

      (dataImportConfigRepo.insert _).expects(config).returns(config.pure[ConnectionIO])

      service.create(config).map(_ shouldBe config).unsafeToFuture()
    }

    "find a Import data config by id" in {
      val config = newDataImportConfig()

      (dataImportConfigRepo.findById _).expects(config.id).returns(config.pure[ConnectionIO])

      service.findById(config.id).map(_ shouldBe config).unsafeToFuture()
    }

    "find Import data configs by project id" in {
      val config = newDataImportConfig()

      (dataImportConfigRepo.findPaginatedByProjectId _)
        .expects(projectId, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(config), 1L).pure[ConnectionIO])

      service
        .findPaginatedByProjectId(projectId, DbPageRequest.default)
        .map(_.items shouldBe List(config))
        .unsafeToFuture()
    }

    "update a Import data config, stamping the current time from the clock" in {
      val config = newDataImportConfig()
      val now = nowAsInstant
      val updated = config.copy(name = "Renamed config", importPath = "/tmp/dumps-new", updatedAt = now)

      (() => tgClock.currentInstant).expects().returns(now)
      (dataImportConfigRepo.update _).expects(config, now).returns(updated.pure[ConnectionIO])

      service.update(config).map(_ shouldBe updated).unsafeToFuture()
    }

    "delete a Import data config by id" in {
      val config = newDataImportConfig()

      (dataImportConfigRepo.deleteById _).expects(config.id).returns(1.pure[ConnectionIO])

      service.deleteById(config.id).map(_ shouldBe 1).unsafeToFuture()
    }

  }

}
