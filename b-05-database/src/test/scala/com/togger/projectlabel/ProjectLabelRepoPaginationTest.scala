package com.togger.projectlabel

import cats.implicits._
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PageRequest, PaginatedResponse, TimeHelpers}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class ProjectLabelRepoPaginationTest extends BusinessTgDbSpec with TimeHelpers {

  private val repo = new ProjectLabelRepoImpl
  private val categoryRepo = new ProjectLabelCategoryRepoImpl

  private def newLabel(name: String, categoryId: UUID): ProjectLabel =
    ProjectLabel(UUID.randomUUID(), name, s"$name description", categoryId, nowAsInstant, nowAsInstant)

  /** Eleven labels "Label 00".."Label 10", inserted in reverse so the order is by name and not by insertion. */
  private def seed(): ConnectionIO[(UUID, Vector[ProjectLabel])] =
    for {
      category <- categoryRepo.insert(ProjectLabelCategory(UUID.randomUUID(), "Language", nowAsInstant, nowAsInstant))
      created <- (0 to 10).toList.reverse.traverse(index =>
        repo.insert(newLabel(f"Label $index%02d", category.id)).map(index -> _)
      )
    } yield category.id -> created.sortBy(_._1).map(_._2).toVector

  private def page(pageNumber: Int, size: Int): ConnectionIO[PaginatedResponse[ProjectLabel]] =
    repo.findPaginated(DbPageRequest.fromPageRequest(PageRequest(pageNumber, size)).toOption.get)

  testNameFromClassName should {

    "split 11 project labels over 3 pages of 5, by name, without a duplicate" in { transactor =>
      (for {
        seeded <- seed()
        pages <- (1 to 3).toList.traverse(page(_, 5))
      } yield {
        val cp = new Checkpoint
        cp(pages.map(_.items.size) shouldBe List(5, 5, 1))
        cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(11L))
        cp(pages.map(_.totalNumberOfPages).distinct shouldBe List(3L))
        cp(pages.flatMap(_.items.map(_.id)) shouldBe seeded._2.map(_.id).toList)
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "return an empty page with the totals past the last page" in { transactor =>
      (for {
        _ <- seed()
        past <- page(4, 5)
      } yield {
        past.items shouldBe empty
        past.totalNumberOfItems shouldBe 11L
        past.totalNumberOfPages shouldBe 3L
      }).transact(transactor)
    }

    "break a tie on the name by id, so pages never overlap" in { transactor =>
      (for {
        seeded <- seed()
        twins <- (1 to 3).toList.traverse(_ => repo.insert(newLabel("Label 05", seeded._1)))
        pages <- (1 to 7).toList.traverse(page(_, 2))
      } yield {
        val items = pages.flatMap(_.items)
        val sameName = items.filter(_.name == "Label 05").map(_.id.toString)
        items.map(_.id).distinct.size shouldBe 14
        sameName shouldBe sameName.sorted
        sameName.size shouldBe twins.size + 1
      }).transact(transactor)
    }
  }
}
