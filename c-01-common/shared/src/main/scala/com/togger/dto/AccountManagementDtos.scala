package com.togger.dto

import java.util.UUID

case class AccountProjectDto(id: UUID, organizationId: UUID, name: String)

case class AccountManagementDto(
  id: UUID,
  firstName: String,
  lastName: String,
  email: String,
  accountTypeId: UUID,
  accountTypeName: String,
  projects: List[AccountProjectDto]
)

case class AccountTypeDto(id: UUID, name: String)

case class UpdateAccountReq(id: UUID, firstName: String, lastName: String, email: String, accountTypeId: UUID)
