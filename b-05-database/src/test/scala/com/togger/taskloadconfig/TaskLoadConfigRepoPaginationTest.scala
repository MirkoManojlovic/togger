package com.togger.taskloadconfig

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PaginationTestSupport, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TaskLoadConfigRepoPaginationTest
    extends BusinessTgDbSpec
    with TgAccount
    with TgProject
    with TimeHelpers
    with PaginationTestSupport {

  private val repo = new TaskLoadConfigRepoImpl

  private def newConfig(projectId: UUID, accountId: UUID, name: String) =
    TaskLoadConfig(UUID.randomUUID(), name, s"/dumps/$name", projectId, accountId, nowAsInstant, nowAsInstant)

  private def readPages(projectId: UUID)(page: DbPageRequest) = repo.findPaginatedByProjectId(projectId, page)

  testNameFromClassName should {

    "split 11 configs over 3 pages of 5 ordered by id, and give an empty page past the end" in { transactor =>
      (for {
        account <- createAccount
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        saved <- (0 to 10).toList.traverse(index => repo.insert(newConfig(projectId, account.id, s"config $index")))
        result <- assertElevenRowsOverThreePages(readPages(projectId), saved.map(_.id).sortBy(_.toString))(_.id)
      } yield result).transact(transactor)
    }

    "only see the configs of the project" in { transactor =>
      (for {
        account <- createAccount
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        otherProject <- projectRepo.insert(project.copy(organizationId = orgAndProject._1.id, name = "Other project"))
        mine <- repo.insert(newConfig(projectId, account.id, "mine"))
        _ <- repo.insert(newConfig(otherProject.id, account.id, "theirs"))
        found <- repo.findPaginatedByProjectId(projectId, pageRequest(1, 100))
        unknown <- repo.findPaginatedByProjectId(UUID.randomUUID(), pageRequest(1, 100))
      } yield {
        found.items.map(_.id) shouldBe List(mine.id)
        found.totalNumberOfItems shouldBe 1L
        unknown.items shouldBe empty
        unknown.totalNumberOfItems shouldBe 0L
      }).transact(transactor)
    }
  }
}
