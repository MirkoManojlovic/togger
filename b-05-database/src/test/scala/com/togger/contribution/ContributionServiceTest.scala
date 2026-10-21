package com.togger.contribution

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.account.{Account, HashedPasswordImpl}
import com.togger.common.{Password, TgIds, TimeHelpers}
import com.togger.db.MockedTransactor
import com.togger.dto.{ContributionsFilter, DailyCountDto, StatusCountDto}
import com.togger.logentry.LogEntryStatus
import com.togger.milestone.MilestoneStatus
import com.togger.project.{Project, ProjectAccountRepo, ProjectRepo}
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.LocalDate
import java.util.UUID

class ContributionServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with TestLoggingJvm {

  private val contributionRepo: ContributionRepo = mock[ContributionRepo]
  private val projectRepo: ProjectRepo = mock[ProjectRepo]
  private val projectAccountRepo: ProjectAccountRepo = mock[ProjectAccountRepo]

  private val service = new ContributionService(contributionRepo, projectRepo, projectAccountRepo, transactorMock)

  private val projectId = UUID.randomUUID()
  private val project =
    Project(projectId, UUID.randomUUID(), "Maths", "Numbers", "🧮", createdAt = nowAsInstant, updatedAt = nowAsInstant)

  private def account(firstName: String, lastName: String) = Account(
    id = UUID.randomUUID(),
    firstName = firstName,
    lastName = lastName,
    email = s"$firstName@email.com",
    password = HashedPasswordImpl.fromPassword(Password.fromString("A!good_1_password_is_good").toOption.get),
    accountTypeId = TgIds.UNASSIGNED_UUID,
    verified = true,
    enabled = true,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  private val ann = account("Ann", "Adams")
  private val bob = account("Bob", "Brown")
  private val cy = account("Cy", "Clark")
  private val formerMember = UUID.randomUUID()
  private val alpha = UUID.randomUUID()
  private val beta = UUID.randomUUID()

  private def day(dayOfMonth: Int): LocalDate = LocalDate.of(2026, 10, dayOfMonth) // the 13th is a Tuesday

  private def expectReads(filter: ContributionsFilter): Unit = {
    (projectRepo.findById _).expects(projectId).returns(project.pure[ConnectionIO])
    (projectAccountRepo.findAllMembersByProjectIds _)
      .expects(Seq(projectId))
      .returns(Map[UUID, Seq[Account]](projectId -> Seq(cy, bob, ann)).pure[ConnectionIO])
    (contributionRepo.milestoneHeaders _)
      .expects(projectId)
      .returns(
        List(
          MilestoneHeaderRow(alpha, "Alpha", MilestoneStatus.InProgress, day(1), day(20)),
          MilestoneHeaderRow(beta, "Beta", MilestoneStatus.InProgress, day(1), day(30))
        ).pure[ConnectionIO]
      )
    (contributionRepo.milestoneTaskTotals _)
      .expects(projectId)
      .returns(
        List(MilestoneTaskTotalsRow(Some(alpha), 2L, 1L), MilestoneTaskTotalsRow(None, 1L, 0L)).pure[ConnectionIO]
      )
    (contributionRepo.entryActivity _)
      .expects(projectId, filter)
      .returns(
        List(
          EntryActivityRow(ann.id, Some(alpha), day(13), LogEntryStatus.Completed, 2L),
          EntryActivityRow(ann.id, Some(alpha), day(14), LogEntryStatus.InProgress, 1L),
          EntryActivityRow(ann.id, None, day(15), LogEntryStatus.Vacation, 1L),
          EntryActivityRow(bob.id, Some(alpha), day(13), LogEntryStatus.InProgress, 1L),
          EntryActivityRow(formerMember, Some(alpha), day(13), LogEntryStatus.InProgress, 5L)
        ).pure[ConnectionIO]
      )
    (contributionRepo.tasksAssigned _)
      .expects(projectId, filter)
      .returns(
        List(TaskAssignedRow(ann.id, Some(alpha), 1L), TaskAssignedRow(bob.id, None, 2L)).pure[ConnectionIO]
      )
    (contributionRepo.tasksClosed _)
      .expects(projectId, filter)
      .returns(
        List(
          TaskClosedRow(ann.id, Some(alpha), 1L),
          TaskClosedRow(bob.id, Some(alpha), 1L)
        ).pure[ConnectionIO]
      )
  }

  testNameFromClassName should {

    "list every member in the general part, sorted by name, with the numbers summed over the milestones" in {
      expectReads(ContributionsFilter.empty)

      service.findByProjectId(projectId, ContributionsFilter.empty).unsafeToFuture().map { result =>
        result.projectId shouldBe projectId
        result.general.map(_.memberName) shouldBe List("Ann Adams", "Bob Brown", "Cy Clark")
        val List(annStats, bobStats, cyStats) = result.general
        annStats.entriesByStatus shouldBe List(
          StatusCountDto(LogEntryStatus.InProgress, 1),
          StatusCountDto(LogEntryStatus.Completed, 2),
          StatusCountDto(LogEntryStatus.Vacation, 1)
        )
        annStats.tasksAssigned shouldBe 1
        annStats.tasksClosed shouldBe 1
        bobStats.tasksAssigned shouldBe 2
        bobStats.tasksClosed shouldBe 1
        cyStats.entriesByStatus shouldBe empty
        cyStats.activeDays shouldBe 0
      }
    }

    "leave days off out of the active days and the daily entries, and count the work days of the busy range" in {
      expectReads(ContributionsFilter.empty)

      service.findByProjectId(projectId, ContributionsFilter.empty).unsafeToFuture().map { result =>
        val annStats = result.general.head
        annStats.activeDays shouldBe 2
        annStats.dailyEntries shouldBe List(DailyCountDto(day(13), 2), DailyCountDto(day(14), 1))
        // the 13th to the 15th, Tuesday to Thursday
        result.general.map(_.workDaysInRange).distinct shouldBe List(3)
      }
    }

    "count the work days of the requested range, not of the busy days" in {
      val filter = ContributionsFilter(Some(day(12)), Some(day(18)))
      expectReads(filter)

      service.findByProjectId(projectId, filter).unsafeToFuture().map { result =>
        // Monday the 12th to Sunday the 18th
        result.general.map(_.workDaysInRange).distinct shouldBe List(5)
      }
    }

    "give each milestone a card with its tasks and only the members that did something in it" in {
      expectReads(ContributionsFilter.empty)

      service.findByProjectId(projectId, ContributionsFilter.empty).unsafeToFuture().map { result =>
        val List(alphaCard, betaCard, noMilestoneCard) = result.milestones
        alphaCard.milestoneId shouldBe Some(alpha)
        alphaCard.name shouldBe "Alpha"
        alphaCard.status shouldBe Some(MilestoneStatus.InProgress)
        alphaCard.totalTasks shouldBe 2
        alphaCard.closedTasks shouldBe 1
        alphaCard.members.map(_.memberName) shouldBe List("Ann Adams", "Bob Brown")
        alphaCard.members.map(_.activeDays) shouldBe List(2, 1)

        betaCard.milestoneId shouldBe Some(beta)
        betaCard.totalTasks shouldBe 0
        betaCard.members shouldBe empty

        noMilestoneCard.milestoneId shouldBe None
        noMilestoneCard.name shouldBe ContributionAssembler.NoMilestoneName
        noMilestoneCard.totalTasks shouldBe 1
        noMilestoneCard.members.map(_.memberName) shouldBe List("Ann Adams", "Bob Brown")
        // Ann only took a vacation day there: listed, but not worked
        noMilestoneCard.members.head.activeDays shouldBe 0
        noMilestoneCard.members.last.tasksAssigned shouldBe 2
      }
    }

    "drop the rows of accounts that are not members of the project" in {
      expectReads(ContributionsFilter.empty)

      service.findByProjectId(projectId, ContributionsFilter.empty).unsafeToFuture().map { result =>
        (result.general ++ result.milestones.flatMap(_.members)).map(_.memberId) should not contain formerMember
        result.general.flatMap(_.entriesByStatus).map(_.count).sum shouldBe 5
      }
    }

    "ask the repo if an account is a member" in {
      val accountId = UUID.randomUUID()
      (projectAccountRepo.isMember _).expects(projectId, accountId).returns(true.pure[ConnectionIO])

      service.isMember(projectId, accountId).unsafeToFuture().map(_ shouldBe true)
    }
  }
}
