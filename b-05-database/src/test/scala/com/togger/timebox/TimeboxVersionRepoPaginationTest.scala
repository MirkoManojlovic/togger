package com.togger.timebox

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PaginationTestSupport, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

/** The history of a timebox is listed newest version first (the versions are uuid7, so by time). */
class TimeboxVersionRepoPaginationTest
    extends BusinessTgDbSpec
    with TgAccount
    with TgProject
    with TimeHelpers
    with PaginationTestSupport {

  private val timeboxRepo = new TimeboxRepoImpl
  private val timeboxVersionRepo = new TimeboxVersionRepoImpl

  private val firstStart = LocalDate.of(2026, 1, 5)

  private def newVersion(timeboxId: UUID, index: Int) =
    TimeboxVersion(
      UUID.randomUUID(),
      timeboxId,
      firstStart.plusWeeks(index.toLong),
      firstStart.plusWeeks(index.toLong).plusDays(6),
      None,
      nowAsInstant
    )

  private def readPages(timeboxId: UUID)(page: DbPageRequest) =
    timeboxVersionRepo.findPaginatedByTimeboxId(timeboxId, page)

  testNameFromClassName should {

    "split 11 versions over 3 pages of 5, newest version first, and give an empty page past the end" in {
      transactor =>
        (for {
          orgAndProject <- createOrgAndProject
          timebox <- timeboxRepo.insert(Timebox(UUID.randomUUID(), orgAndProject._2.id, nowAsInstant))
          otherTimebox <- timeboxRepo.insert(Timebox(UUID.randomUUID(), orgAndProject._2.id, nowAsInstant))
          versions <- (0 to 10).toList.traverse(index => timeboxVersionRepo.insert(newVersion(timebox.id, index)))
          _ <- timeboxVersionRepo.insert(newVersion(otherTimebox.id, 99))
          result <- assertElevenRowsOverThreePages(
            readPages(timebox.id),
            versions.map(_.version).sortBy(_.toString).reverse
          )(_.version)
        } yield result).transact(transactor)
    }

    "give no versions for an unknown timebox" in { transactor =>
      timeboxVersionRepo
        .findPaginatedByTimeboxId(UUID.randomUUID(), pageRequest(1, 100))
        .map { found =>
          found.items shouldBe empty
          found.totalNumberOfItems shouldBe 0L
        }
        .transact(transactor)
    }
  }
}
