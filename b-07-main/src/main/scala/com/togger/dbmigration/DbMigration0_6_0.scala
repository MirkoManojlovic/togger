package com.togger.dbmigration

import cats.effect.IO
import cats.syntax.apply._

import java.sql.Date
import java.sql.Timestamp
import java.time.ZoneOffset

/** From 0.5.0 (or the unversioned `togger-local`): log entries got their own `log_date`, the UTC day of `created_at`
  * for entries written before. The text of a log entry version is `text` (it was `text_external`).
  */
class DbMigration0_6_0 extends DbMigration {
  override val version: SemVer = SemVer(0, 6, 0)

  private val rules = CopyRules(
    columnRenames = Map(("log_entry_version", "text") -> "text_external"),
    derivedColumns = Map(("log_entry", "log_date") -> logDateOfCreatedAt)
  )

  override def migrate(source: DbConnection, target: DbConnection): IO[Unit] =
    (source.open, target.open).tupled.use { case (from, to) => new TableCopier(rules).copy(from, to) }

  private def logDateOfCreatedAt(row: Map[String, AnyRef]): AnyRef =
    Date.valueOf(row("created_at").asInstanceOf[Timestamp].toInstant.atOffset(ZoneOffset.UTC).toLocalDate)
}
