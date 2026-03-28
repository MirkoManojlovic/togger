package com.togger.auth

import enumeratum.{Enum, EnumEntry}

sealed trait TgPermission extends EnumEntry

object TgPermission extends Enum[TgPermission] {

  override def values: IndexedSeq[TgPermission] = findValues

  case object ManagerUserPermission extends TgPermission
  case object DeveloperUserPermission extends TgPermission
}
