package com.togger.auth

import java.util.UUID

case class AuthAccountFe(
  id: UUID,
  sessionId: String,
//  companyId: Option[Long] = None,
  userRole: UserRole
//  userType: UserType
)
