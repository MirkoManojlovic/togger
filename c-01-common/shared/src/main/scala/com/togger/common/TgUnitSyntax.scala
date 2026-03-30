package com.togger.common

object TgUnitSyntax {
  implicit class TgUnitOps(anything: Any) {
    def toUnit(): Unit = ()
  }
}
