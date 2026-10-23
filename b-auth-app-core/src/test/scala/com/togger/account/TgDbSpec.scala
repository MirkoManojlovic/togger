package com.togger.account

import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import cats.effect.{IO, Resource}
import com.togger.TestLoggingJvm
import com.togger.db.{TgTestDbDrops, TgTestTransactor}
import com.togger.suites.TestNameHelper
import com.zaxxer.hikari.HikariConfig
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

  override val resource: Resource[IO, HikariTransactor[IO]] =
    Resource.pure(SharedTgTestTransactor.transactor)

}

/** One Hikari pool per JVM, shared by all specs, so tests don't exhaust postgres' connection limit. */
object SharedTgTestTransactor extends TgTestTransactor {
  import cats.effect.unsafe.implicits.global

  lazy val transactor: HikariTransactor[IO] = {
    val (transactor, release) = HikariTransactor
      .fromHikariConfig[IO](
        new HikariConfig().tap { hikariConfig =>
          hikariConfig.setDataSource(pgConfig)
          hikariConfig.setMinimumIdle(0)
          hikariConfig.setMaximumPoolSize(4)
          hikariConfig.setInitializationFailTimeout(-1)
        },
        None
      )
      .allocated
      .unsafeRunSync()
    sys.addShutdownHook(release.unsafeRunSync())
    transactor
  }
}
