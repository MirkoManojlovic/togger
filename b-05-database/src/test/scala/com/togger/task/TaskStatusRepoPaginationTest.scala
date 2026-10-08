package com.togger.task

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PageRequest, PaginatedResponse, TimeHelpers}
import com.togger.organization.TgProject
import com.togger.taskStatus.TaskStatusRepoImpl
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TaskStatusRepoPaginationTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  private val repo = new TaskStatusRepoImpl

  /** Eleven task statuses "Status 00".."Status 10" of the project, inserted in reverse so the order is by name and not
    * by insertion. One more belongs to another project.
    */
  private case class Seeded(projectId: UUID, items: Vector[TaskStatus], otherProjectItem: TaskStatus)

  private def newItem(projectId: UUID, name: String): TaskStatus =
    TaskStatus(
      id = UUID.randomUUID(),
      projectId = projectId,
      name = name,
      description = s"$name description",
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )

  private def seed(): ConnectionIO[Seeded] =
    for {
      _ <- createAccount
      orgAndProject <- createOrgAndProject
      projectId = orgAndProject._2.id
      otherProject <- projectRepo.insert(project.copy(organizationId = orgAndProject._1.id, name = "Other project"))
      created <- (0 to 10).toList.reverse.traverse(index =>
        repo.insert(newItem(projectId, f"Status $index%02d")).map(index -> _)
      )
      other <- repo.insert(newItem(otherProject.id, "Status 00"))
    } yield Seeded(projectId, created.sortBy(_._1).map(_._2).toVector, other)

  private def page(projectId: UUID, pageNumber: Int, size: Int): ConnectionIO[PaginatedResponse[TaskStatus]] =
    repo.findPaginatedByProjectId(
      projectId,
      DbPageRequest.fromPageRequest(PageRequest(pageNumber, size)).toOption.get
    )

  testNameFromClassName should {

    "split 11 task statuses over 3 pages of 5, by name, without a duplicate" in { transactor =>
      (for {
        seeded <- seed()
        pages <- (1 to 3).toList.traverse(page(seeded.projectId, _, 5))
      } yield {
        val cp = new Checkpoint
        cp(pages.map(_.items.size) shouldBe List(5, 5, 1))
        cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(11L))
        cp(pages.map(_.totalNumberOfPages).distinct shouldBe List(3L))
        cp(pages.map(_.page) shouldBe List(1, 2, 3))
        cp(pages.flatMap(_.items.map(_.id)) shouldBe seeded.items.map(_.id).toList)
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "return an empty page with the totals past the last page" in { transactor =>
      (for {
        seeded <- seed()
        past <- page(seeded.projectId, 4, 5)
      } yield {
        past.items shouldBe empty
        past.totalNumberOfItems shouldBe 11L
        past.totalNumberOfPages shouldBe 3L
      }).transact(transactor)
    }

    "only see the task statuses of the project" in { transactor =>
      (for {
        seeded <- seed()
        found <- page(seeded.projectId, 1, 100)
        unknown <- page(UUID.randomUUID(), 1, 100)
      } yield {
        found.items.map(_.id) should not contain seeded.otherProjectItem.id
        unknown.items shouldBe empty
        unknown.totalNumberOfItems shouldBe 0L
      }).transact(transactor)
    }

    "break a tie on the name by id, so pages never overlap" in { transactor =>
      (for {
        seeded <- seed()
        twins <- (1 to 3).toList.traverse(_ => repo.insert(newItem(seeded.projectId, "Status 05")))
        pages <- (1 to 7).toList.traverse(page(seeded.projectId, _, 2))
      } yield {
        val ids = pages.flatMap(_.items.map(_.id))
        val sameName = pages.flatMap(_.items).filter(_.name == "Status 05").map(_.id.toString)
        ids.distinct.size shouldBe 14
        sameName shouldBe sameName.sorted
        sameName.toSet shouldBe (twins.map(_.id) :+ seeded.items(5).id).map(_.toString).toSet
      }).transact(transactor)
    }
  }
}
