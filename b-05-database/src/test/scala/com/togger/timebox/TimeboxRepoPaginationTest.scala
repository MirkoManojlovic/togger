package com.togger.timebox

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PageRequest, PaginatedResponse, TimeHelpers}
import com.togger.organization.TgProject
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

class TimeboxRepoPaginationTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  private val timeboxRepo = new TimeboxRepoImpl
  private val timeboxVersionRepo = new TimeboxVersionRepoImpl

  private val firstStart = LocalDate.of(2026, 1, 5)

  /** Eleven timeboxes of the project, timebox i starting i weeks after `firstStart`, inserted in reverse so the order
    * is by start date and not by insertion. Timebox 0 has an older version starting later than any other, which no
    * ordering may use. Timebox 1 and 2 are replaced by a newer version with the other's start date, swapping them.
    * One more timebox belongs to another project.
    */
  private case class Seeded(projectId: UUID, timeboxes: Vector[Timebox], otherProjectTimebox: Timebox) {
    def idsOf(indexes: Seq[Int]): List[UUID] = indexes.map(timeboxes(_).id).toList
  }

  private def insertVersion(timeboxId: UUID, start: LocalDate) =
    timeboxVersionRepo.insert(
      TimeboxVersion(UUID.randomUUID(), timeboxId, start, start.plusDays(6), None, nowAsInstant)
    )

  private def seed(): ConnectionIO[Seeded] =
    for {
      _ <- createAccount
      orgAndProject <- createOrgAndProject
      projectId = orgAndProject._2.id
      otherProject <- projectRepo.insert(project.copy(organizationId = orgAndProject._1.id, name = "Other project"))
      created <- (0 to 10).toList.reverse.traverse { index =>
        for {
          timebox <- timeboxRepo.insert(Timebox(UUID.randomUUID(), projectId, nowAsInstant))
          _ <- Option.when(index == 0)(insertVersion(timebox.id, firstStart.plusWeeks(100))).sequence
          _ <- insertVersion(timebox.id, firstStart.plusWeeks(index.toLong))
        } yield index -> timebox
      }
      byIndex = created.toMap
      _ <- insertVersion(byIndex(1).id, firstStart.plusWeeks(2))
      _ <- insertVersion(byIndex(2).id, firstStart.plusWeeks(1))
      otherProjectTimebox <- timeboxRepo.insert(Timebox(UUID.randomUUID(), otherProject.id, nowAsInstant))
      _ <- insertVersion(otherProjectTimebox.id, firstStart.plusWeeks(50))
    } yield Seeded(projectId, (0 to 10).map(byIndex).toVector, otherProjectTimebox)

  // Newest start first; 1 and 2 swapped by their newer versions.
  private val expectedOrder = Seq(10, 9, 8, 7, 6, 5, 4, 3, 1, 2, 0)

  private def page(seeded: Seeded, pageNumber: Int, size: Int): ConnectionIO[PaginatedResponse[Timebox]] =
    timeboxRepo.findPaginatedByProjectId(
      seeded.projectId,
      DbPageRequest.fromPageRequest(PageRequest(pageNumber, size)).toOption.get
    )

  testNameFromClassName should {

    "split 11 timeboxes over 3 pages of 5, latest start first, without a duplicate" in { transactor =>
      (for {
        seeded <- seed()
        pages <- (1 to 3).toList.traverse(page(seeded, _, 5))
      } yield {
        val cp = new Checkpoint
        cp(pages.map(_.items.size) shouldBe List(5, 5, 1))
        cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(11L))
        cp(pages.map(_.totalNumberOfPages).distinct shouldBe List(3L))
        cp(pages.map(_.page) shouldBe List(1, 2, 3))
        cp(pages.flatMap(_.items.map(_.id)) shouldBe seeded.idsOf(expectedOrder))
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "return an empty page with the totals past the last page" in { transactor =>
      (for {
        seeded <- seed()
        past <- page(seeded, 4, 5)
      } yield {
        past.items shouldBe empty
        past.totalNumberOfItems shouldBe 11L
        past.totalNumberOfPages shouldBe 3L
      }).transact(transactor)
    }

    "only see the timeboxes of the project" in { transactor =>
      (for {
        seeded <- seed()
        found <- page(seeded, 1, 100)
        unknown <- page(seeded.copy(projectId = UUID.randomUUID()), 1, 100)
      } yield {
        found.items.map(_.id) should not contain seeded.otherProjectTimebox.id
        unknown.items shouldBe empty
        unknown.totalNumberOfItems shouldBe 0L
      }).transact(transactor)
    }

    "keep a timebox without a version, last" in { transactor =>
      (for {
        seeded <- seed()
        versionless <- timeboxRepo.insert(Timebox(UUID.randomUUID(), seeded.projectId, nowAsInstant))
        found <- page(seeded, 1, 100)
      } yield {
        found.totalNumberOfItems shouldBe 12L
        found.items.last.id shouldBe versionless.id
      }).transact(transactor)
    }
  }
}
