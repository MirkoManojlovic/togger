package com.togger.ui.auth

import com.togger.auth.{TgPermission, UserRole}

trait AuthorizationService {
  def check(user: UserRole, permission: Set[TgPermission]): Boolean
}
