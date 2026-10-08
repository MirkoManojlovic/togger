package com.togger.projectmapping

import cats.implicits._
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PaginationTestSupport, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class ProjectMappingRepoPaginationTest
    extends BusinessTgDbSpec
    with TgProject
    with TimeHelpers
    with PaginationTestSupport {

  private val repo = new ProjectMappingRepoImpl

  // Small increasing ids compare the same way in Postgres (bytes) and in Scala.
  private def dumpId(index: Int): UUID = new UUID(0L, index.toLong)

  private def newMapping(projectIdDb: UUID, projectIdDump: UUID) =
    ProjectMapping(projectIdDb, projectIdDump, nowAsInstant, nowAsInstant)

  private def readByDb(projectIdDb: UUID)(page: DbPageRequest) = repo.findPaginatedByProjectIdDb(projectIdDb, page)

  private def readByDump(projectIdDump: UUID)(page: DbPageRequest) =
    repo.findPaginatedByProjectIdDump(projectIdDump, page)

  testNameFromClassName should {

    "split 11 mappings of a db project over 3 pages of 5 ordered by dump id" in { transactor =>
      (for {
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        _ <- (0 to 10).toList.reverse.traverse(index => repo.insert(newMapping(projectId, dumpId(index))))
        result <- assertElevenRowsOverThreePages(readByDb(projectId), (0 to 10).map(dumpId))(_.projectIdDump)
      } yield result).transact(transactor)
    }

    "split 11 mappings of a dump project over 3 pages of 5 ordered by db id" in { transactor =>
      (for {
        orgAndProject <- createOrgAndProject
        others <- (1 to 10).toList.traverse(index =>
          projectRepo.insert(project.copy(organizationId = orgAndProject._1.id, name = s"Project $index"))
        )
        projects = orgAndProject._2 :: others
        _ <- projects.traverse(dbProject => repo.insert(newMapping(dbProject.id, dumpId(1))))
        result <- assertElevenRowsOverThreePages(readByDump(dumpId(1)), projects.map(_.id).sortBy(_.toString))(
          _.projectIdDb
        )
      } yield result).transact(transactor)
    }

    "only see the mappings of the asked project" in { transactor =>
      (for {
        orgAndProject <- createOrgAndProject
        other <- projectRepo.insert(project.copy(organizationId = orgAndProject._1.id, name = "Other project"))
        mine <- repo.insert(newMapping(orgAndProject._2.id, dumpId(1)))
        _ <- repo.insert(newMapping(other.id, dumpId(2)))
        found <- repo.findPaginatedByProjectIdDb(orgAndProject._2.id, pageRequest(1, 100))
        unknown <- repo.findPaginatedByProjectIdDump(dumpId(99), pageRequest(1, 100))
      } yield {
        found.items shouldBe List(mine)
        unknown.items shouldBe empty
        unknown.totalNumberOfItems shouldBe 0L
      }).transact(transactor)
    }
  }
}
