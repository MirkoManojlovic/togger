package com.togger.auth

import com.togger.auth.TgPermission.{DeveloperUserPermission, ManagerUserPermission}
import enumeratum.{Enum, EnumEntry}

sealed trait UserRole extends EnumEntry {
  val permissions: Set[TgPermission]
}

object UserRole extends Enum[UserRole] {
  override def values: IndexedSeq[UserRole] = findValues

  case object ManagerUserRole extends UserRole {
    override val permissions: Set[TgPermission] = Set(ManagerUserPermission)
  }

  case object DeveloperUserRole extends UserRole {
    override val permissions: Set[TgPermission] = Set(DeveloperUserPermission)
  }

  object NoPermissionsUserRole extends UserRole {
    override val permissions: Set[TgPermission] = Set.empty
  }
}
