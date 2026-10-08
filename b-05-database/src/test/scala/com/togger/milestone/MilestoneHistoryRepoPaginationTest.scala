package com.togger.milestone

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PaginationTestSupport, TimeHelpers}
import com.togger.organization.TgProject
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

/** Milestone dates are listed by id (uuid7, so oldest first), the versions of a milestone or date newest first. */
class MilestoneHistoryRepoPaginationTest
    extends BusinessTgDbSpec
    with TgAccount
    with TgProject
    with TimeHelpers
    with PaginationTestSupport {

  private val milestoneRepo = new MilestoneRepoImpl
  private val milestoneVersionRepo = new MilestoneVersionRepoImpl
  private val milestoneDateRepo = new MilestoneDateRepoImpl
  private val milestoneDateVersionRepo = new MilestoneDateVersionRepoImpl

  private def newMilestone(projectId: UUID) = Milestone(UUID.randomUUID(), projectId, nowAsInstant)

  private def newMilestoneDate(milestoneId: UUID) = MilestoneDate(UUID.randomUUID(), milestoneId, nowAsInstant)

  private def newMilestoneVersion(milestoneId: UUID, index: Int) =
    MilestoneVersion(
      version = UUID.randomUUID(),
      milestoneId = milestoneId,
      name = s"Milestone $index",
      description = s"Description $index",
      startDate = LocalDate.of(2026, 1, 5),
      dueDate = LocalDate.of(2026, 2, 5),
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )

  private def newMilestoneDateVersion(milestoneDateId: UUID, index: Int) =
    MilestoneDateVersion(
      version = UUID.randomUUID(),
      milestoneDateId = milestoneDateId,
      name = s"Date $index",
      description = s"Description $index",
      date = nowAsInstant,
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )

  private case class Seeded(projectId: UUID, otherProjectId: UUID, milestone: Milestone, otherMilestone: Milestone)

  private def seed(): ConnectionIO[Seeded] =
    for {
      orgAndProject <- createOrgAndProject
      projectId = orgAndProject._2.id
      otherProject <- projectRepo.insert(project.copy(organizationId = orgAndProject._1.id, name = "Other project"))
      milestone <- milestoneRepo.insert(newMilestone(projectId))
      otherMilestone <- milestoneRepo.insert(newMilestone(otherProject.id))
    } yield Seeded(projectId, otherProject.id, milestone, otherMilestone)

  private def readDatesOfMilestone(milestoneId: UUID)(page: DbPageRequest) =
    milestoneDateRepo.findPaginatedByMilestoneId(milestoneId, page)

  private def readDatesOfProject(projectId: UUID)(page: DbPageRequest) =
    milestoneDateRepo.findPaginatedByProjectId(projectId, page)

  testNameFromClassName should {

    "split 11 versions of a milestone over 3 pages of 5, newest version first" in { transactor =>
      (for {
        seeded <- seed()
        versions <- (0 to 10).toList.traverse(index =>
          milestoneVersionRepo.insert(newMilestoneVersion(seeded.milestone.id, index))
        )
        _ <- milestoneVersionRepo.insert(newMilestoneVersion(seeded.otherMilestone.id, 99))
        result <- assertElevenRowsOverThreePages(
          milestoneVersionRepo.findPaginatedByMilestoneId(seeded.milestone.id, _),
          versions.map(_.version).sortBy(_.toString).reverse
        )(_.version)
      } yield result).transact(transactor)
    }

    "split 11 versions of a milestone date over 3 pages of 5, newest version first" in { transactor =>
      (for {
        seeded <- seed()
        date <- milestoneDateRepo.insert(newMilestoneDate(seeded.milestone.id))
        otherDate <- milestoneDateRepo.insert(newMilestoneDate(seeded.otherMilestone.id))
        versions <- (0 to 10).toList.traverse(index =>
          milestoneDateVersionRepo.insert(newMilestoneDateVersion(date.id, index))
        )
        _ <- milestoneDateVersionRepo.insert(newMilestoneDateVersion(otherDate.id, 99))
        result <- assertElevenRowsOverThreePages(
          milestoneDateVersionRepo.findPaginatedByMilestoneDateId(date.id, _),
          versions.map(_.version).sortBy(_.toString).reverse
        )(_.version)
      } yield result).transact(transactor)
    }

    "split 11 dates of a milestone over 3 pages of 5 ordered by id" in { transactor =>
      (for {
        seeded <- seed()
        dates <- (0 to 10).toList.traverse(_ => milestoneDateRepo.insert(newMilestoneDate(seeded.milestone.id)))
        _ <- milestoneDateRepo.insert(newMilestoneDate(seeded.otherMilestone.id))
        result <- assertElevenRowsOverThreePages(
          readDatesOfMilestone(seeded.milestone.id),
          dates.map(_.id).sortBy(_.toString)
        )(_.id)
      } yield result).transact(transactor)
    }

    "split 11 dates of a project over 3 pages of 5 ordered by id, across its milestones" in { transactor =>
      (for {
        seeded <- seed()
        secondMilestone <- milestoneRepo.insert(newMilestone(seeded.projectId))
        firstDates <- (0 to 5).toList.traverse(_ => milestoneDateRepo.insert(newMilestoneDate(seeded.milestone.id)))
        secondDates <- (0 to 4).toList.traverse(_ => milestoneDateRepo.insert(newMilestoneDate(secondMilestone.id)))
        _ <- milestoneDateRepo.insert(newMilestoneDate(seeded.otherMilestone.id))
        result <- assertElevenRowsOverThreePages(
          readDatesOfProject(seeded.projectId),
          (firstDates ++ secondDates).map(_.id).sortBy(_.toString)
        )(_.id)
      } yield result).transact(transactor)
    }
  }
}
