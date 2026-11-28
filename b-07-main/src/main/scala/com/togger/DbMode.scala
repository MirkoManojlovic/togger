package com.togger

import com.togger.dbmigration.DbNames
import enumeratum.EnumEntry
import enumeratum._

import scala.annotation.unused

sealed abstract class DbMode(val dbName: String) extends EnumEntry {

  /** The database to use for an app version; only prod has one database per version. */
  def dbNameFor(@unused appVersion: String): String = dbName
}
object DbMode extends Enum[DbMode] {
  override def values: IndexedSeq[DbMode] = findValues

  case object Development extends DbMode("togger-local-development")
  case object Prod extends DbMode(DbNames.LegacyProdName) {
    override def dbNameFor(appVersion: String): String = DbNames.prodName(appVersion)
  }
}
