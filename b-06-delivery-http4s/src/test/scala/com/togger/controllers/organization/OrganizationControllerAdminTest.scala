package com.togger.controllers.organization

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.togger.account.AdminCheck
import com.togger.auth.{AuthAccount, SecuredEndpoint, SecurityLogic}
import com.togger.common._
import com.togger.db.MockedTransactor
import com.togger.organization._
import doobie.ConnectionIO
import doobie.free.connection.pure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.tapir.integ.cats.effect.CatsMonadError

import java.time.Instant
import java.util.UUID

class OrganizationControllerAdminTest extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-10-12T10:00:00Z")
  private val adminId = UUID.randomUUID()
  private val developerId = UUID.randomUUID()
  private val organization = Organization(UUID.randomUUID(), "Maths", "Numbers", "🏢", now, now)

  private def authAccount(accountId: UUID) =
    TgJsonSuccess.justBody(AuthAccount(accountId, "John", "Doe", "john@email.com", now, now, "device", now, now))

  private val securityLogic = new SecurityLogic {
    override def securityLogic(token: String): IO[Either[TgJsonFailure, TgJsonSuccess[AuthAccount]]] =
      IO.pure(Right(authAccount(UUID.fromString(token))))
  }

  // Only findPaginated is reachable from the endpoint under test; the repo records that it was asked.
  private class StubOrganizationRepo extends OrganizationRepo {
    var pagesRequested = 0
    override def findPaginated(page: DbPageRequest): ConnectionIO[PaginatedResponse[Organization]] = {
      pagesRequested += 1
      pure(PaginatedResponse.of(page.page.pageValue, page.limit, List(organization), 1L))
    }
    override def findById(id: UUID): ConnectionIO[Organization] = ???
    override def insert(organization: Organization): ConnectionIO[Organization] = ???
    override def deleteById(id: UUID): ConnectionIO[Int] = ???
    override def update(organization: Organization, updatedAt: Instant): ConnectionIO[Organization] = ???
    override def findPaginatedByAccountId(
      accountId: UUID,
      page: DbPageRequest
    ): ConnectionIO[PaginatedResponse[Organization]] = ???
  }

  private val adminCheck = new AdminCheck {
    override def isAdmin(accountId: UUID): IO[Boolean] = IO.pure(accountId == adminId)
  }

  private def callFindAll(accountId: UUID, repo: StubOrganizationRepo, pageRequest: PageRequest) = {
    val service = new OrganizationService(repo, null, null, new TgClockImpl, new MockedTransactor {}.transactorMock)
    val controller =
      new OrganizationController(
        new OrganizationEndpoints(new SecuredEndpoint(securityLogic)),
        service,
        adminCheck,
        new TgClockImpl
      )
    val monad = new CatsMonadError[IO]()
    (for {
      principal <- controller.findAll.securityLogic(monad)(accountId.toString)
      result <- controller.findAll.logic(monad)(principal.toOption.get)(pageRequest)
    } yield result).unsafeRunSync()
  }

  "OrganizationController.findAll" should {

    "answer 403 to an account that is not an admin, without reading any organization" in {
      val repo = new StubOrganizationRepo
      callFindAll(developerId, repo, PageRequest.default) shouldBe Left(Forbidden)
      repo.pagesRequested shouldBe 0
    }

    "answer 403 to an unknown account" in {
      val repo = new StubOrganizationRepo
      callFindAll(UUID.randomUUID(), repo, PageRequest.default) shouldBe Left(Forbidden)
      repo.pagesRequested shouldBe 0
    }

    "answer the requested page to an admin" in {
      val repo = new StubOrganizationRepo
      val result = callFindAll(adminId, repo, PageRequest(1, 10))
      val page = result.toOption.flatMap(_.body)
      page.map(_.items.map(_.id)) shouldBe Some(List(organization.id))
      page.map(_.totalNumberOfItems) shouldBe Some(1L)
      repo.pagesRequested shouldBe 1
    }

    "answer 400 to an admin asking for page 0" in {
      val repo = new StubOrganizationRepo
      callFindAll(adminId, repo, PageRequest(0, 10)) match {
        case Left(BadRequest(errors)) => errors.map(_.`type`) shouldBe Set("DbPageError")
        case other                    => fail(s"Expected a bad request but got $other")
      }
      repo.pagesRequested shouldBe 0
    }
  }
}
