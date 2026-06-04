package com.togger

import enumeratum.{EnumEntry, _}

sealed trait AppEnv extends EnumEntry
object AppEnv extends Enum[AppEnv] {
  override def values: IndexedSeq[AppEnv] = findValues

  case object Web extends AppEnv
  case object Desktop extends AppEnv
}

class AppEnvParser {
  def parse(inputArgs: List[String]): AppEnv =
    inputArgs match {
      case List("web")     => AppEnv.Web
      case List("desktop") => AppEnv.Desktop
      case _               => AppEnv.Web
    }

}
