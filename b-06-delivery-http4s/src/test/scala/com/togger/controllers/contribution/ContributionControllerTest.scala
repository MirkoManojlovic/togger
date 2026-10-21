package com.togger.controllers.contribution

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.togger.auth.{AuthAccount, SecuredEndpoint, SecurityLogic}
import com.togger.common._
import com.togger.contribution._
import com.togger.db.MockedTransactor
import com.togger.dto.ContributionsFilter
import com.togger.account.Account
import com.togger.project.{Project, ProjectAccount, ProjectAccountRepo, ProjectRepo}
import doobie.ConnectionIO
import doobie.free.connection.pure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.tapir.integ.cats.effect.CatsMonadError

import java.time.Instant
import java.util.UUID

class ContributionControllerTest extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-10-12T10:00:00Z")
  private val projectId = UUID.randomUUID()
  private val memberId = UUID.randomUUID()
  private val outsiderId = UUID.randomUUID()

  private def authAccount(accountId: UUID) =
    TgJsonSuccess.justBody(AuthAccount(accountId, "John", "Doe", "john@email.com", now, now, "device", now, now))

  private val securityLogic = new SecurityLogic {
    override def securityLogic(token: String): IO[Either[TgJsonFailure, TgJsonSuccess[AuthAccount]]] =
      IO.pure(Right(authAccount(UUID.fromString(token))))
  }

  // Only the membership check is reachable for an outsider; the repo records how many aggregates were read.
  private class StubContributionRepo extends ContributionRepo {
    var reads = 0
    private def read[A](rows: List[A]): ConnectionIO[List[A]] = { reads += 1; pure(rows) }
    override def entryActivity(projectId: UUID, filter: ContributionsFilter) = read(List.empty[EntryActivityRow])
    override def tasksAssigned(projectId: UUID, filter: ContributionsFilter) = read(List.empty[TaskAssignedRow])
    override def tasksClosed(projectId: UUID, filter: ContributionsFilter) = read(List.empty[TaskClosedRow])
    override def tasksByStatus(projectId: UUID, filter: ContributionsFilter) = read(List.empty[TaskStatusCountRow])
    override def tasksByPriority(projectId: UUID, filter: ContributionsFilter) =
      read(List.empty[TaskPriorityCountRow])
    override def tasksByLabel(projectId: UUID, filter: ContributionsFilter) = read(List.empty[TaskLabelCountRow])
    override def milestoneHeaders(projectId: UUID) = read(List.empty[MilestoneHeaderRow])
    override def milestoneTaskTotals(projectId: UUID) = read(List.empty[MilestoneTaskTotalsRow])
  }

  private class StubProjectAccountRepo extends ProjectAccountRepo {
    override def isMember(projectId: UUID, accountId: UUID): ConnectionIO[Boolean] = pure(accountId == memberId)
    override def findAllMembersByProjectIds(projectIds: Seq[UUID]) = pure(Map.empty[UUID, Seq[Account]])
    override def findById(projectId: UUID, accountId: UUID) = ???
    override def insert(organizationAccount: ProjectAccount) = ???
    override def deleteById(projectId: UUID, accountId: UUID) = ???
    override def findPaginatedMembers(projectId: UUID, page: DbPageRequest) = ???
  }

  private val project =
    Project(projectId, UUID.randomUUID(), "Maths", "Numbers", "🧮", createdAt = now, updatedAt = now)

  private class StubProjectRepo extends ProjectRepo {
    override def findById(id: UUID): ConnectionIO[Project] = pure(project)
    override def insert(project: Project) = ???
    override def deleteById(id: UUID) = ???
    override def update(project: Project, updatedAt: Instant) = ???
    override def findPaginatedByOrganizationIdAndAccountId(
      organizationId: UUID,
      accountId: UUID,
      page: DbPageRequest
    ) = ???
    override def findPaginatedByAccountId(accountId: UUID, labelIds: Seq[UUID], page: DbPageRequest) = ???
  }

  private def callFindByProjectId(accountId: UUID, repo: StubContributionRepo) = {
    val service = new ContributionService(
      repo,
      new StubProjectRepo,
      new StubProjectAccountRepo,
      new MockedTransactor {}.transactorMock
    )
    val controller = new ContributionController(new ContributionEndpoints(new SecuredEndpoint(securityLogic)), service)
    val monad = new CatsMonadError[IO]()
    (for {
      principal <- controller.findByProjectId.securityLogic(monad)(accountId.toString)
      result <- controller.findByProjectId.logic(monad)(principal.toOption.get)((projectId, ContributionsFilter.empty))
    } yield result).unsafeRunSync()
  }

  "ContributionController.findByProjectId" should {

    "answer 403 to an account that is not a member of the project, without reading any aggregate" in {
      val repo = new StubContributionRepo
      callFindByProjectId(outsiderId, repo) shouldBe Left(Forbidden)
      repo.reads shouldBe 0
    }

    "answer the contributions of the project to a member" in {
      val repo = new StubContributionRepo
      val result = callFindByProjectId(memberId, repo)
      result.toOption.flatMap(_.body).map(_.projectId) shouldBe Some(projectId)
      repo.reads shouldBe 8
    }
  }
}
