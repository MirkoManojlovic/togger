package com.togger.controllers.milestone

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.togger.account.Account
import com.togger.auth.{AuthAccount, SecuredEndpoint, SecurityLogic}
import com.togger.ai.TgAiService
import com.togger.common._
import com.togger.contribution.{ContributionRepo, ContributionService}
import com.togger.db.MockedTransactor
import com.togger.dto.SummariseMilestoneWithAiReq
import com.togger.milestone._
import com.togger.project.{ProjectAccount, ProjectAccountRepo, ProjectRepo}
import doobie.ConnectionIO
import doobie.free.connection.pure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.tapir.integ.cats.effect.CatsMonadError

import java.time.{Instant, LocalDate}
import java.util.UUID

class MilestoneAiSummaryControllerTest extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-10-12T10:00:00Z")
  private val projectId = UUID.randomUUID()
  private val milestoneId = UUID.randomUUID()
  private val memberId = UUID.randomUUID()
  private val outsiderId = UUID.randomUUID()

  private val securityLogic = new SecurityLogic {
    override def securityLogic(token: String): IO[Either[TgJsonFailure, TgJsonSuccess[AuthAccount]]] =
      IO.pure(
        Right(
          TgJsonSuccess.justBody(
            AuthAccount(UUID.fromString(token), "John", "Doe", "john@email.com", now, now, "device", now, now)
          )
        )
      )
  }

  private class StubProjectAccountRepo extends ProjectAccountRepo {
    override def isMember(projectId: UUID, accountId: UUID): ConnectionIO[Boolean] = pure(accountId == memberId)
    override def findAllMembersByProjectIds(projectIds: Seq[UUID]) = pure(Map.empty[UUID, Seq[Account]])
    override def findById(projectId: UUID, accountId: UUID) = ???
    override def insert(organizationAccount: ProjectAccount) = ???
    override def deleteById(projectId: UUID, accountId: UUID) = ???
    override def findPaginatedMembers(projectId: UUID, page: DbPageRequest) = ???
  }

  private class StubMilestoneRepo extends MilestoneRepo {
    override def findById(id: UUID): ConnectionIO[Milestone] = pure(Milestone(id, projectId, now))
    override def insert(milestone: Milestone) = ???
    override def insertWithId(milestone: Milestone) = ???
    override def deleteById(id: UUID) = ???
    override def findPaginatedByProjectId(
      projectId: UUID,
      filter: com.togger.dto.MilestoneFilter,
      page: DbPageRequest
    ) = ???
  }

  // Outsiders and invalid dates are turned away before the ai service is reached.
  private val controller = {
    val transactor = new MockedTransactor {}.transactorMock
    new MilestoneAiSummaryController(
      new MilestoneAiSummaryStreamEndpoints(new SecuredEndpoint(securityLogic)),
      new MilestoneAiSummaryService(null, null, new TgAiService(null, null), transactor),
      new MilestoneService(new StubMilestoneRepo, null, transactor),
      new DateRangeValidation,
      new ContributionService(null: ContributionRepo, null: ProjectRepo, new StubProjectAccountRepo, transactor)
    )
  }

  private val monad = new CatsMonadError[IO]()

  private def summarise(accountId: UUID, dto: SummariseMilestoneWithAiReq) =
    (for {
      principal <- controller.summarise.securityLogic(monad)(accountId.toString)
      result <- controller.summarise.logic(monad)(principal.toOption.get)(dto)
    } yield result).unsafeRunSync()

  private def request(start: LocalDate, end: LocalDate) =
    SummariseMilestoneWithAiReq(milestoneId, None, "Summarise", start, end, "gpt-4o-mini")

  "MilestoneAiSummaryController" should {

    "answer 403 to an account that is not a member of the milestone's project" in {
      summarise(outsiderId, request(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9))) shouldBe Left(Forbidden)
    }

    "answer a bad request to a member when the end date is before the start date" in {
      val result = summarise(memberId, request(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 5)))
      (result.isLeft, result == Left(Forbidden)) shouldBe (true, false)
    }
  }
}
