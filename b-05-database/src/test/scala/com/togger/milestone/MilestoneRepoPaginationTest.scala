package com.togger.milestone

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PageRequest, PaginatedResponse, TimeHelpers}
import com.togger.dto.MilestoneFilter
import com.togger.organization.TgProject
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

class MilestoneRepoPaginationTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  private val milestoneRepo = new MilestoneRepoImpl
  private val milestoneVersionRepo = new MilestoneVersionRepoImpl

  private val firstDue = LocalDate.of(2026, 3, 1)

  /** Eleven milestones of the project, milestone i due i days after `firstDue`, inserted in reverse so the order is by
    * due date and not by insertion. Milestone 0 has an older version due later than any other, which no ordering may
    * use. Milestone 1 and 2 are replaced by a newer version with the other's due date, swapping them. Milestones
    * 0-3 are done, the others in progress; "Release" is in the name of 4 and in the description of 5. One more
    * milestone belongs to another project.
    */
  private case class Seeded(projectId: UUID, milestones: Vector[Milestone], otherProjectMilestone: Milestone) {
    def idsOf(indexes: Seq[Int]): List[UUID] = indexes.map(milestones(_).id).toList
  }

  private def insertVersion(
    milestoneId: UUID,
    dueDate: LocalDate,
    name: String = "Milestone",
    description: String = "Plain",
    status: MilestoneStatus = MilestoneStatus.InProgress
  ) =
    milestoneVersionRepo.insert(
      MilestoneVersion(
        version = UUID.randomUUID(),
        milestoneId = milestoneId,
        name = name,
        description = description,
        status = status,
        startDate = dueDate.minusDays(10),
        dueDate = dueDate,
        endDate = None,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

  private def insertMilestone(projectId: UUID) =
    milestoneRepo.insert(Milestone(UUID.randomUUID(), projectId, nowAsInstant))

  private def seed(): ConnectionIO[Seeded] =
    for {
      _ <- createAccount
      orgAndProject <- createOrgAndProject
      projectId = orgAndProject._2.id
      otherProject <- projectRepo.insert(project.copy(organizationId = orgAndProject._1.id, name = "Other project"))
      created <- (0 to 10).toList.reverse.traverse { index =>
        for {
          milestone <- insertMilestone(projectId)
          _ <- Option.when(index == 0)(insertVersion(milestone.id, firstDue.plusDays(100))).sequence
          _ <- insertVersion(
            milestone.id,
            firstDue.plusDays(index.toLong),
            name = if (index == 4) "Release candidate" else s"Milestone $index",
            description = if (index == 5) "Ship the release" else "Plain",
            status = if (index <= 3) MilestoneStatus.Done else MilestoneStatus.InProgress
          )
        } yield index -> milestone
      }
      byIndex = created.toMap
      _ <- insertVersion(byIndex(1).id, firstDue.plusDays(2), status = MilestoneStatus.Done)
      _ <- insertVersion(byIndex(2).id, firstDue.plusDays(1), status = MilestoneStatus.Done)
      otherProjectMilestone <- insertMilestone(otherProject.id)
      _ <- insertVersion(otherProjectMilestone.id, firstDue.minusDays(50))
    } yield Seeded(projectId, (0 to 10).map(byIndex).toVector, otherProjectMilestone)

  // Due first first; 1 and 2 swapped by their newer versions.
  private val expectedOrder = Seq(0, 2, 1, 3, 4, 5, 6, 7, 8, 9, 10)

  private def page(
    seeded: Seeded,
    pageNumber: Int,
    size: Int,
    filter: MilestoneFilter = MilestoneFilter.empty
  ): ConnectionIO[PaginatedResponse[Milestone]] =
    milestoneRepo.findPaginatedByProjectId(
      seeded.projectId,
      filter,
      DbPageRequest.fromPageRequest(PageRequest(pageNumber, size)).toOption.get
    )

  testNameFromClassName should {

    "split 11 milestones over 3 pages of 5, due first first, without a duplicate" in { transactor =>
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

    "only see the milestones of the project" in { transactor =>
      (for {
        seeded <- seed()
        found <- page(seeded, 1, 100)
        unknown <- page(seeded.copy(projectId = UUID.randomUUID()), 1, 100)
      } yield {
        found.items.map(_.id) should not contain seeded.otherProjectMilestone.id
        unknown.items shouldBe empty
        unknown.totalNumberOfItems shouldBe 0L
      }).transact(transactor)
    }

    "break a tie on the due date by id, so pages never overlap" in { transactor =>
      (for {
        seeded <- seed()
        twins <- (1 to 3).toList.traverse(_ =>
          insertMilestone(seeded.projectId).flatTap(milestone => insertVersion(milestone.id, firstDue.plusDays(7)))
        )
        pages <- (1 to 7).toList.traverse(page(seeded, _, 2))
      } yield {
        val items = pages.flatMap(_.items)
        val sameDue = items.filter(milestone => (twins :+ seeded.milestones(7)).exists(_.id == milestone.id))
        items.map(_.id).distinct.size shouldBe 14
        sameDue.map(_.id.toString) shouldBe sameDue.map(_.id.toString).sorted
        sameDue.size shouldBe 4
      }).transact(transactor)
    }

    "filter by the status of the latest version, counting only the matches" in { transactor =>
      (for {
        seeded <- seed()
        done <- page(seeded, 1, 100, MilestoneFilter(statuses = List(MilestoneStatus.Done)))
        either <- page(seeded, 1, 100, MilestoneFilter(statuses = List(MilestoneStatus.Done, MilestoneStatus.Paused)))
        paged <- page(seeded, 2, 2, MilestoneFilter(statuses = List(MilestoneStatus.InProgress)))
      } yield {
        done.items.map(_.id) shouldBe seeded.idsOf(Seq(0, 2, 1, 3))
        done.totalNumberOfItems shouldBe 4L
        either.totalNumberOfItems shouldBe 4L
        paged.totalNumberOfItems shouldBe 7L
        paged.totalNumberOfPages shouldBe 4L
        paged.items.map(_.id) shouldBe seeded.idsOf(Seq(6, 7))
      }).transact(transactor)
    }

    "search the name and the description of the latest version, ignoring case and LIKE wildcards" in { transactor =>
      (for {
        seeded <- seed()
        release <- page(seeded, 1, 100, MilestoneFilter(search = Some("RELEASE")))
        wildcard <- page(seeded, 1, 100, MilestoneFilter(search = Some("%")))
        none <- page(seeded, 1, 100, MilestoneFilter(search = Some("nothing like this")))
        blank <- page(seeded, 1, 100, MilestoneFilter(search = Some("  ")))
      } yield {
        release.items.map(_.id) shouldBe seeded.idsOf(Seq(4, 5))
        release.totalNumberOfItems shouldBe 2L
        wildcard.totalNumberOfItems shouldBe 0L
        none.items shouldBe empty
        blank.totalNumberOfItems shouldBe 11L
      }).transact(transactor)
    }
  }
}
