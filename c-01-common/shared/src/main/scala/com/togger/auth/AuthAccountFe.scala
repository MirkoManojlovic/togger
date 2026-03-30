package com.togger.auth

case class AuthAccountFe(
  id: Long,
  sessionId: String,
//  companyId: Option[Long] = None,
  userRole: UserRole
//  userType: UserType
)
