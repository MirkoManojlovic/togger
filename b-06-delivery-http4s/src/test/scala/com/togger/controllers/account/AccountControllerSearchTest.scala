package com.togger.controllers.account

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.togger.account._
import com.togger.auth.{AuthAccount, SecuredEndpoint, SecurityLogic}
import com.togger.common._
import com.togger.db.MockedTransactor
import com.togger.dto.SearchAccountDto
import doobie.ConnectionIO
import doobie.free.connection.pure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.tapir.integ.cats.effect.CatsMonadError

import java.time.Instant
import java.util.UUID

class AccountControllerSearchTest extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-10-12T10:00:00Z")

  private val account = Account(
    UUID.randomUUID(),
    "Jack",
    "Curry",
    "bird@email.com",
    null,
    UUID.randomUUID(),
    verified = true,
    enabled = true,
    now,
    now
  )

  private val securityLogic = new SecurityLogic {
    override def securityLogic(token: String): IO[Either[TgJsonFailure, TgJsonSuccess[AuthAccount]]] =
      IO.pure(
        Right(
          TgJsonSuccess.justBody(AuthAccount(UUID.randomUUID(), "John", "Doe", "j@email.com", now, now, "d", now, now))
        )
      )
  }

  // Only searchPaginated is reachable from the endpoint under test; the repo records the terms it was asked for.
  private class StubAccountRepo extends AccountRepo {
    val terms = scala.collection.mutable.Buffer.empty[String]
    override def searchPaginated(term: String, page: DbPageRequest): ConnectionIO[PaginatedResponse[Account]] = {
      terms += term
      pure(PaginatedResponse.of(page.page.pageValue, page.limit, List(account), 1L))
    }
    override def insert(account: Account): ConnectionIO[Account] = ???
    override def findById(id: UUID): ConnectionIO[Account] = ???
    override def findByEmail(email: String): ConnectionIO[Account] = ???
    override def findByIds(ids: Set[UUID]): ConnectionIO[List[Account]] = ???
    override def deleteById(id: UUID): ConnectionIO[Int] = ???
    override def update(account: Account, updateAt: Instant): ConnectionIO[Account] = ???
  }

  private def search(term: String, pageRequest: PageRequest, repo: StubAccountRepo) = {
    val controller = new AccountController(
      new AccountEndpoints(new SecuredEndpoint(securityLogic)),
      new AccountServiceCore(repo, new MockedTransactor {}.transactorMock)
    )
    val monad = new CatsMonadError[IO]()
    (for {
      principal <- controller.searchByFirstNameLastNameEmail.securityLogic(monad)("token")
      result <- controller.searchByFirstNameLastNameEmail.logic(monad)(principal.toOption.get)((term, pageRequest))
    } yield result).unsafeRunSync()
  }

  "AccountController.searchByFirstNameLastNameEmail" should {

    "answer the requested page of the matching accounts, searched with the trimmed term" in {
      val repo = new StubAccountRepo
      val page = search("  bird ", PageRequest(1, 10), repo).toOption.flatMap(_.body)
      page.map(_.items) shouldBe
        Some(List(SearchAccountDto(account.id, "Jack", "Curry", "bird@email.com", account.accountTypeId)))
      page.map(_.totalNumberOfItems) shouldBe Some(1L)
      repo.terms.toList shouldBe List("bird")
    }

    "answer 400 for a term that is too short, without searching" in {
      val repo = new StubAccountRepo
      search("a", PageRequest.default, repo) match {
        case Left(BadRequest(errors)) => errors.map(_.`type`) shouldBe Set("SearchTermTooShort")
        case other                    => fail(s"Expected a bad request but got $other")
      }
      repo.terms shouldBe empty
    }

    "answer 400 for page 0, without searching" in {
      val repo = new StubAccountRepo
      search("bird", PageRequest(0, 10), repo) match {
        case Left(BadRequest(errors)) => errors.map(_.`type`) shouldBe Set("DbPageError")
        case other                    => fail(s"Expected a bad request but got $other")
      }
      repo.terms shouldBe empty
    }
  }
}
