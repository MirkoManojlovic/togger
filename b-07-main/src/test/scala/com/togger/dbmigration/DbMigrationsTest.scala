package com.togger.dbmigration

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.io.Source
import scala.util.Using

class DbMigrationsTest extends AnyWordSpec with Matchers {

  "DbMigrations" should {
    "list each version once, oldest first" in {
      val versions = DbMigrations.all.map(_.version)
      versions shouldBe versions.distinct.sorted
    }

    "have a migration for the version of the desktop app (a release without data changes adds a no-op one)" in {
      val appVersion = tauriVersion
      val needsMigration = DbMigrations.all.headOption.exists(first => appVersion >= first.version)
      if (needsMigration) DbMigrations.forVersion(appVersion) should not be empty
      else succeed
    }
  }

  private def tauriVersion: SemVer = {
    val config = Iterator
      .iterate(new File(".").getAbsoluteFile)(_.getParentFile)
      .takeWhile(_ != null)
      .map(dir => new File(dir, "src-tauri/tauri.conf.json"))
      .find(_.exists())
      .get
    val content = Using.resource(Source.fromFile(config))(_.mkString)
    val version = """"version"\s*:\s*"([^"]+)"""".r.findFirstMatchIn(content).map(_.group(1)).get
    SemVer.parse(version).get
  }
}
