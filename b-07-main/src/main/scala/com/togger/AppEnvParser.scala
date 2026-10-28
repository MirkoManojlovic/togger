package com.togger

import enumeratum.{EnumEntry, _}

sealed trait AppEnv extends EnumEntry
object AppEnv extends Enum[AppEnv] {
  override def values: IndexedSeq[AppEnv] = findValues

  case object Web extends AppEnv
  case object Desktop extends AppEnv
}

class AppEnvParser {
  def parse(inputArgs: List[String]): (AppEnv, String, DbMode) = {
    val appEnv = inputArgs.head match {
      case "web"     => AppEnv.Web
      case "desktop" => AppEnv.Desktop
      case _         => AppEnv.Web
    }

    val changelogPath = inputArgs.tail.head

    val dbMode = inputArgs.drop(2).headOption match {
      case Some("prod") => DbMode.Prod
      case _            => DbMode.Development
    }

    (appEnv, changelogPath, dbMode)
  }

}
