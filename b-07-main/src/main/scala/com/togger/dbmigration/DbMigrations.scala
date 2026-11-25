package com.togger.dbmigration

/** One entry per release, oldest first. A release without data changes still has its class, as a no-op. */
object DbMigrations {
  val all: List[DbMigration] = List(new DbMigration0_6_0)

  def forVersion(version: SemVer): Option[DbMigration] = all.find(_.version == version)
}
