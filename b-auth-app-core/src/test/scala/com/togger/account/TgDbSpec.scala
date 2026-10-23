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
    new HikariConfig()
      .tap { hikariConfig =>
        hikariConfig.setDataSource(pgConfig)
        hikariConfig.setMinimumIdle(0)
        hikariConfig.setInitializationFailTimeout(-1)
      }
      .pipe(hikariConfig => HikariTransactor.fromHikariConfig(hikariConfig, None))

}
