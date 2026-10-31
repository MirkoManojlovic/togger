package com.togger.account

import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import cats.effect.{IO, Resource}
import com.togger.TestLoggingJvm
import com.togger.db.{TgTestDbDrops, TgTestTransactor}
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.postgres.implicits._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.FixtureAsyncWordSpec
import org.scalatest.{Checkpoints, FutureOutcome}

import scala.util.chaining.scalaUtilChainingOps

trait TgDbSpec
    extends FixtureAsyncWordSpec
    with AsyncIOSpec
    with Matchers
    with Checkpoints
    with TestLoggingJvm
    with TgTestTransactor
    with TestNameHelper
    with TgTestDbDrops
    with CatsResourceIO[HikariTransactor[IO]] {

  override def withFixture(test: NoArgAsyncTest): FutureOutcome =
    new FutureOutcome(
      resource
        .use(transactor =>
          dropTablesQuery
            .transact(transactor)
            .flatMap(_ => IO.fromFuture(IO(super.withFixture(test).toFuture)))
            .flatMap(futureOutcome => dropTablesQuery.transact(transactor).map(_ => futureOutcome))
        )
        .unsafeToFuture()
    )

  val dropTablesQuery: ConnectionIO[Seq[Int]]

  @volatile private var createdDatabase: Option[TgTestDatabase] = None

  /** The database of this spec, cloned from the template on first use and dropped after the last test. */
  private def testDatabase: TgTestDatabase = synchronized {
    createdDatabase.getOrElse(TgTestDatabase.create().tap(database => createdDatabase = Some(database)))
  }

  override val resource: Resource[IO, HikariTransactor[IO]] = Resource.eval(IO(testDatabase.transactor))

  override def afterAll(): Unit =
    try super.afterAll()
    finally createdDatabase.foreach(_.drop())

}
