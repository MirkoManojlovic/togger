package com.togger.projectmapping

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

  val service = new ProjectMappingService(projectMappingRepo, transactorMock)

  val projectIdDump: UUID = UUID.randomUUID()
  val projectIdDb: UUID = UUID.randomUUID()

  def newProjectMapping() = ProjectMapping(
    projectIdDump = projectIdDump,
    projectIdDb = projectIdDb,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "create a project mapping" in {
      val mapping = newProjectMapping()

      (projectMappingRepo.insert _).expects(mapping).returns(mapping.pure[ConnectionIO])

      service.create(mapping).map(_ shouldBe mapping).unsafeToFuture()
    }

    "find project mappings by db project id" in {
      val mapping = newProjectMapping()

      (projectMappingRepo.findByProjectIdDb _)
        .expects(projectIdDb)
        .returns(List(mapping).pure[ConnectionIO])

      service.findByProjectIdDb(projectIdDb).map(_ shouldBe List(mapping)).unsafeToFuture()
    }

    "find project mappings by dump project id" in {
      val mapping = newProjectMapping()

      (projectMappingRepo.findByProjectIdDump _)
        .expects(projectIdDump)
        .returns(List(mapping).pure[ConnectionIO])

      service.findByProjectIdDump(projectIdDump).map(_ shouldBe List(mapping)).unsafeToFuture()
    }

    "delete a project mapping by its composite key" in {
      (projectMappingRepo.deleteByIds _).expects(projectIdDb, projectIdDump).returns(1.pure[ConnectionIO])

      service.deleteByIds(projectIdDb, projectIdDump).map(_ shouldBe 1).unsafeToFuture()
    }

  }

}
