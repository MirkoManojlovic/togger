package com.togger.timebox

import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

/** Date ranges are inclusive, and only the latest version of each timebox counts. */
class TimeboxVersionRepoOverlapTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  private val timeboxRepo = new TimeboxRepoImpl
  private val timeboxVersionRepo = new TimeboxVersionRepoImpl

  private val start = LocalDate.of(2026, 10, 12)
  private val end = start.plusDays(6)

  private def addVersion(timeboxId: UUID, from: LocalDate, to: LocalDate) =
    timeboxVersionRepo.insert(TimeboxVersion(UUID.randomUUID(), timeboxId, from, to, None, nowAsInstant, ""))

  private def addTimebox(projectId: UUID, from: LocalDate, to: LocalDate) =
    for {
      timebox <- timeboxRepo.insert(Timebox(UUID.randomUUID(), projectId, nowAsInstant))
      _ <- addVersion(timebox.id, from, to)
    } yield timebox

  testNameFromClassName should {

    "find ranges that share at least one day and ignore the ones that do not" in { transactor =>
      (for {
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        _ <- addTimebox(projectId, start, end)
        overlaps = (from: LocalDate, to: LocalDate) =>
          timeboxVersionRepo.existsOverlappingLatest(projectId, from, to, None)
        same <- overlaps(start, end)
        inside <- overlaps(start.plusDays(2), start.plusDays(3))
        around <- overlaps(start.minusDays(3), end.plusDays(3))
        sharesFirstDay <- overlaps(start.minusDays(4), start)
        sharesLastDay <- overlaps(end, end.plusDays(4))
        dayBefore <- overlaps(start.minusDays(4), start.minusDays(1))
        dayAfter <- overlaps(end.plusDays(1), end.plusDays(4))
      } yield {
        List(same, inside, around, sharesFirstDay, sharesLastDay) shouldBe List.fill(5)(true)
        List(dayBefore, dayAfter) shouldBe List(false, false)
      }).transact(transactor)
    }

    "not count the excluded timebox itself" in { transactor =>
      (for {
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        timebox <- addTimebox(projectId, start, end)
        other <- addTimebox(projectId, end.plusDays(10), end.plusDays(12))
        ownOnly <- timeboxVersionRepo.existsOverlappingLatest(projectId, start, end, Some(timebox.id))
        withOther <- timeboxVersionRepo.existsOverlappingLatest(projectId, start, end, Some(other.id))
      } yield {
        ownOnly shouldBe false
        withOther shouldBe true
      }).transact(transactor)
    }

    "only look at the latest version of a timebox" in { transactor =>
      (for {
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        timebox <- addTimebox(projectId, start, end)
        _ <- addVersion(timebox.id, end.plusDays(20), end.plusDays(25))
        oldDates <- timeboxVersionRepo.existsOverlappingLatest(projectId, start, end, None)
        newDates <- timeboxVersionRepo.existsOverlappingLatest(projectId, end.plusDays(20), end.plusDays(21), None)
      } yield {
        oldDates shouldBe false
        newDates shouldBe true
      }).transact(transactor)
    }

    "give no overlap when the project has no timeboxes" in { transactor =>
      (for {
        orgAndProject <- createOrgAndProject
        found <- timeboxVersionRepo.existsOverlappingLatest(orgAndProject._2.id, start, end, None)
        unknownProject <- timeboxVersionRepo.existsOverlappingLatest(UUID.randomUUID(), start, end, None)
      } yield {
        found shouldBe false
        unknownProject shouldBe false
      }).transact(transactor)
    }
  }
}
