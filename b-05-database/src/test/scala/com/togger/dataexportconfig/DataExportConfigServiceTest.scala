package com.togger.dataexportconfig

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

class DataExportConfigServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val dataExportConfigRepo: DataExportConfigRepo = mock[DataExportConfigRepo]
  val tgClock: TgClock = mock[TgClock]

  val service = new DataExportConfigService(dataExportConfigRepo, tgClock, transactorMock)

  val projectId: UUID = UUID.randomUUID()
  val accountId: UUID = UUID.randomUUID()

  def newDataExportConfig() = DataExportConfig(
    id = UUID.randomUUID(),
    projectId = projectId,
    accountId = accountId,
    name = "My dump config",
    exportPath = "/tmp/dumps",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "create a task dump config" in {
      val config = newDataExportConfig()

      (dataExportConfigRepo.insert _).expects(config).returns(config.pure[ConnectionIO])

      service.create(config).map(_ shouldBe config).unsafeToFuture()
    }

    "find a task dump config by id" in {
      val config = newDataExportConfig()

      (dataExportConfigRepo.findById _).expects(config.id).returns(config.pure[ConnectionIO])

      service.findById(config.id).map(_ shouldBe config).unsafeToFuture()
    }

    "find task dump configs by project id" in {
      val config = newDataExportConfig()

      (dataExportConfigRepo.findPaginatedByProjectId _)
        .expects(projectId, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(config), 1L).pure[ConnectionIO])

      service
        .findPaginatedByProjectId(projectId, DbPageRequest.default)
        .map(_.items shouldBe List(config))
        .unsafeToFuture()
    }

    "update a task dump config, stamping the current time from the clock" in {
      val config = newDataExportConfig()
      val now = nowAsInstant
      val updated = config.copy(exportPath = "/tmp/dumps-new", updatedAt = now)

      (() => tgClock.currentInstant).expects().returns(now)
      (dataExportConfigRepo.update _).expects(config, now).returns(updated.pure[ConnectionIO])

      service.update(config).map(_ shouldBe updated).unsafeToFuture()
    }

    "delete a task dump config by id" in {
      val config = newDataExportConfig()

      (dataExportConfigRepo.deleteById _).expects(config.id).returns(1.pure[ConnectionIO])

      service.deleteById(config.id).map(_ shouldBe 1).unsafeToFuture()
    }

  }

}
