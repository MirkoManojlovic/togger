package com.togger

import enumeratum.{EnumEntry, _}

sealed abstract class DbMode(val dbName: String) extends EnumEntry
object DbMode extends Enum[DbMode] {
  override def values: IndexedSeq[DbMode] = findValues

  case object Development extends DbMode("togger-local-development")
  case object Prod extends DbMode("togger-local")
}
