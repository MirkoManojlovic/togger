package com.togger.dbmigration

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.togger.desktop.InitialSetup
import doobie.Transactor
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DbMigrationRunnerTest extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  // far above every real release, so the real databases of the machine are never picked as the source
  private val oldVersion = SemVer(97, 0, 0)
  private val newVersion = SemVer(98, 0, 0)
  private val oldDb = DbNames.prodName(oldVersion)
  private val newDb = DbNames.prodName(newVersion)

  private val admin = Transactor.fromDriverManager[IO](
    driver = "org.postgresql.Driver",
    url = "jdbc:postgresql://localhost:5432/postgres",
    user = "togger_account",
    password = "togger_password",
    logHandler = None
  )

  private val migration = new DbMigration0_6_0 { override val version: SemVer = newVersion }
  private def runner = new DbMigrationRunner(
    new InitialSetup(admin),
    name => IO(TestDatabases.create(name, "liquibase/changelog.yaml")),
    List(migration)
  )

  override def beforeAll(): Unit = {
    TestDatabases.create(oldDb, "liquibase-v0.5.0/changelog.yaml")
    TestDatabases.execute(
      oldDb,
      """SET session_replication_role = replica;
        |INSERT INTO log_entry (id, task_id, created_by, created_at) VALUES
        |  ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000c1', '2026-11-01 23:30:00+00'),
        |  ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000c1', '2026-11-02 00:30:00+02');
        |INSERT INTO log_entry_version (version, log_entry_id, text, status, created_by, created_at, updated_at) VALUES
        |  ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-0000000000a1', 'worked on it', 1, '00000000-0000-0000-0000-0000000000c1', '2026-11-01 23:30:00+00', '2026-11-01 23:30:00+00');
        |""".stripMargin
    )
  }

  override def afterAll(): Unit = {
    TestDatabases.drop(oldDb)
    TestDatabases.drop(newDb)
  }

  "DbMigrationRunner" should {
    "copy the data of the previous version, deriving the log date from the UTC day of created_at" in {
      runner.run(newDb, newVersion).unsafeRunSync()

      TestDatabases.query(newDb, "SELECT id, log_date FROM log_entry ORDER BY id") shouldBe List(
        List("00000000-0000-0000-0000-0000000000a1", "2026-11-01"),
        List("00000000-0000-0000-0000-0000000000a2", "2026-11-01")
      )
      TestDatabases.query(newDb, "SELECT text FROM log_entry_version") shouldBe List(List("worked on it"))
      TestDatabases.query(newDb, "SELECT version FROM db_migration_log") shouldBe List(List("98.0.0"))
    }

    "leave the previous database untouched" in {
      TestDatabases.query(oldDb, "SELECT count(*) FROM log_entry") shouldBe List(List("2"))
    }

    "skip a database that is already migrated" in {
      TestDatabases.execute(newDb, "DELETE FROM log_entry WHERE id = '00000000-0000-0000-0000-0000000000a2'")
      runner.run(newDb, newVersion).unsafeRunSync()
      TestDatabases.query(newDb, "SELECT count(*) FROM log_entry") shouldBe List(List("1"))
    }

    "rebuild a database without the migration marker" in {
      TestDatabases.execute(newDb, "DELETE FROM db_migration_log")
      runner.run(newDb, newVersion).unsafeRunSync()
      TestDatabases.query(newDb, "SELECT count(*) FROM log_entry") shouldBe List(List("2"))
      TestDatabases.query(newDb, "SELECT count(*) FROM db_migration_log") shouldBe List(List("1"))
    }

    "drop the new database when the migration fails" in {
      val failing = new DbMigration {
        override val version: SemVer = newVersion
        override def migrate(source: DbConnection, target: DbConnection): IO[Unit] =
          IO.raiseError(new RuntimeException("boom"))
      }
      TestDatabases.drop(newDb)
      val failingRunner = new DbMigrationRunner(
        new InitialSetup(admin),
        name => IO(TestDatabases.create(name, "liquibase/changelog.yaml")),
        List(failing)
      )
      failingRunner.run(newDb, newVersion).attempt.unsafeRunSync().isLeft shouldBe true
      TestDatabases.exists(newDb) shouldBe false
    }
  }
}
