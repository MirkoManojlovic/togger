package com.togger.dbmigration

import cats.effect.IO

/** Moves the data of the previous version's database into the database of [[version]]; the schema is already in place.
  */
trait DbMigration {
  def version: SemVer

  def migrate(source: DbConnection, target: DbConnection): IO[Unit]
}

/** What differs between the schema of the previous version and this one. */
final case class CopyRules(
  tableRenames: Map[String, String] = Map.empty,
  columnRenames: Map[(String, String), String] = Map.empty,
  derivedColumns: Map[(String, String), Map[String, AnyRef] => AnyRef] = Map.empty,
  skippedTables: Set[String] = Set.empty
)

object CopyRules {
  val noChanges: CopyRules = CopyRules()
}
