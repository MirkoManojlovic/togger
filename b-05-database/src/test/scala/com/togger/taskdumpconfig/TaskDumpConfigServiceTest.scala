package com.togger.taskdumpconfig

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.{TgClock, TimeHelpers}
import com.togger.db.MockedTransactor
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.implicits._
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class TaskDumpConfigServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val taskDumpConfigRepo: TaskDumpConfigRepo = mock[TaskDumpConfigRepo]
  val tgClock: TgClock = mock[TgClock]

  val service = new TaskDumpConfigService(taskDumpConfigRepo, tgClock, transactorMock)

  val projectId: UUID = UUID.randomUUID()
  val accountId: UUID = UUID.randomUUID()

  def newTaskDumpConfig() = TaskDumpConfig(
    id = UUID.randomUUID(),
    projectId = projectId,
    accountId = accountId,
    dumpPath = "/tmp/dumps",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "create a task dump config" in {
      val config = newTaskDumpConfig()

      (taskDumpConfigRepo.insert _).expects(config).returns(config.pure[ConnectionIO])

      service.create(config).map(_ shouldBe config).unsafeToFuture()
    }

    "find a task dump config by id" in {
      val config = newTaskDumpConfig()

      (taskDumpConfigRepo.findById _).expects(config.id).returns(config.pure[ConnectionIO])

      service.findById(config.id).map(_ shouldBe config).unsafeToFuture()
    }

    "find task dump configs by project id" in {
      val config = newTaskDumpConfig()

      (taskDumpConfigRepo.findByProjectId _).expects(projectId).returns(List(config).pure[ConnectionIO])

      service.findByProjectId(projectId).map(_ shouldBe List(config)).unsafeToFuture()
    }

    "find task dump configs by account id" in {
      val config = newTaskDumpConfig()

      (taskDumpConfigRepo.findByAccountId _).expects(accountId).returns(List(config).pure[ConnectionIO])

      service.findByAccountId(accountId).map(_ shouldBe List(config)).unsafeToFuture()
    }

    "update a task dump config, stamping the current time from the clock" in {
      val config = newTaskDumpConfig()
      val now = nowAsInstant
      val updated = config.copy(dumpPath = "/tmp/dumps-new", updatedAt = now)

      (() => tgClock.currentInstant).expects().returns(now)
      (taskDumpConfigRepo.update _).expects(config, now).returns(updated.pure[ConnectionIO])

      service.update(config).map(_ shouldBe updated).unsafeToFuture()
    }

    "delete a task dump config by id" in {
      val config = newTaskDumpConfig()

      (taskDumpConfigRepo.deleteById _).expects(config.id).returns(1.pure[ConnectionIO])

      service.deleteById(config.id).map(_ shouldBe 1).unsafeToFuture()
    }

  }

}
