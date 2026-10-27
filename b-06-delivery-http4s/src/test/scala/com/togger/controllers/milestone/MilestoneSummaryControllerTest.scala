package com.togger.controllers.milestone

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.togger.account.Account
import com.togger.auth.{AuthAccount, SecuredEndpoint, SecurityLogic}
import com.togger.common._
import com.togger.contribution.{ContributionRepo, ContributionService}
import com.togger.db.MockedTransactor
import com.togger.dto.CreateMilestoneSummaryReq
import com.togger.milestone._
import com.togger.project.{ProjectAccount, ProjectAccountRepo, ProjectRepo}
import doobie.ConnectionIO
import doobie.free.connection.pure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.tapir.integ.cats.effect.CatsMonadError

import java.time.{Instant, LocalDate}
import java.util.UUID

class MilestoneSummaryControllerTest extends AnyWordSpec with Matchers {

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

  // An outsider is turned away before the summary or project services are reached, so those need no behavior here.
  private val controller = {
    val transactor = new MockedTransactor {}.transactorMock
    new MilestoneSummaryController(
      new MilestoneSummaryEndpoints(new SecuredEndpoint(securityLogic)),
      new MilestoneSummaryService(null, null, transactor),
      new MilestoneService(new StubMilestoneRepo, null, transactor),
      new DateRangeValidation,
      null,
      new ContributionService(null: ContributionRepo, null: ProjectRepo, new StubProjectAccountRepo, transactor)
    )
  }

  private val monad = new CatsMonadError[IO]()

  "MilestoneSummaryController" should {

    "answer 403 to an account that is not a member of the milestone's project when saving" in {
      val dto = CreateMilestoneSummaryReq(milestoneId, "summary", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9))
      val result = (for {
        principal <- controller.save.securityLogic(monad)(outsiderId.toString)
        result <- controller.save.logic(monad)(principal.toOption.get)(dto)
      } yield result).unsafeRunSync()
      result shouldBe Left(Forbidden)
    }

    "answer 403 to an account that is not a member of the milestone's project when listing" in {
      val result = (for {
        principal <- controller.findByMilestoneId.securityLogic(monad)(outsiderId.toString)
        result <- controller.findByMilestoneId.logic(monad)(principal.toOption.get)((milestoneId, PageRequest.default))
      } yield result).unsafeRunSync()
      result shouldBe Left(Forbidden)
    }
  }
}
