package com.togger.projectmapping

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

class ProjectMappingServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val projectMappingRepo: ProjectMappingRepo = mock[ProjectMappingRepo]
  val tgClock: TgClock = mock[TgClock]

  val service = new ProjectMappingService(projectMappingRepo, tgClock, transactorMock)

  val projectIdOrigin: UUID = UUID.randomUUID()
  val projectIdLoaded: UUID = UUID.randomUUID()

  def newProjectMapping() = ProjectMapping(
    id = UUID.randomUUID(),
    projectIdOrigin = projectIdOrigin,
    projectIdLoaded = projectIdLoaded,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "create a project mapping" in {
      val mapping = newProjectMapping()

      (projectMappingRepo.insert _).expects(mapping).returns(mapping.pure[ConnectionIO])

      service.create(mapping).map(_ shouldBe mapping).unsafeToFuture()
    }

    "find a project mapping by id" in {
      val mapping = newProjectMapping()

      (projectMappingRepo.findById _).expects(mapping.id).returns(mapping.pure[ConnectionIO])

      service.findById(mapping.id).map(_ shouldBe mapping).unsafeToFuture()
    }

    "find project mappings by loaded project id" in {
      val mapping = newProjectMapping()

      (projectMappingRepo.findByProjectIdLoaded _)
        .expects(projectIdLoaded)
        .returns(List(mapping).pure[ConnectionIO])

      service.findByProjectIdLoaded(projectIdLoaded).map(_ shouldBe List(mapping)).unsafeToFuture()
    }

    "update a project mapping, stamping the current time from the clock" in {
      val mapping = newProjectMapping()
      val now = nowAsInstant
      val updated = mapping.copy(projectIdOrigin = UUID.randomUUID(), updatedAt = now)

      (() => tgClock.currentInstant).expects().returns(now)
      (projectMappingRepo.update _).expects(mapping, now).returns(updated.pure[ConnectionIO])

      service.update(mapping).map(_ shouldBe updated).unsafeToFuture()
    }

    "delete a project mapping by id" in {
      val mapping = newProjectMapping()

      (projectMappingRepo.deleteById _).expects(mapping.id).returns(1.pure[ConnectionIO])

      service.deleteById(mapping.id).map(_ shouldBe 1).unsafeToFuture()
    }

  }

}
