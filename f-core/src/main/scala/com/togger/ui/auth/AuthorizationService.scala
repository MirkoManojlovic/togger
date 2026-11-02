package com.togger.ui.auth

import com.togger.auth.TgPermission
import com.togger.auth.UserRole

trait AuthorizationService {
  def check(user: UserRole, permission: Set[TgPermission]): Boolean
}
