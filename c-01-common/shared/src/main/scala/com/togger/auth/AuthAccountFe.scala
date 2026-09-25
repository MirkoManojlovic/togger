package com.togger.auth

import java.util.UUID

/** `firstName`/`lastName`/`email` are optional because this is persisted as JSON in local storage and read back on
  * startup: entries stored before these fields existed decode to `None` instead of failing the whole decode and logging
  * the user out.
  */
case class AuthAccountFe(
  id: UUID,
  sessionId: String,
//  companyId: Option[Long] = None,
  userRole: UserRole,
//  userType: UserType
  firstName: Option[String] = None,
  lastName: Option[String] = None,
  email: Option[String] = None
)
