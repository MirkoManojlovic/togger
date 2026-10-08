package com.togger.organization

import com.togger.account.Account
import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait OrganizationAccountRepo {
  def findById(organizationId: UUID, accountId: UUID): ConnectionIO[OrganizationAccount]

  def insert(organizationAccount: OrganizationAccount): ConnectionIO[OrganizationAccount]

  def deleteById(organizationId: UUID, accountId: UUID): ConnectionIO[Int]

//  def update(organizationAccount: OrganizationAccount, updatedAt: Instant): ConnectionIO[OrganizationAccount]

  /** One page of the organization's members by last name, first name. */
  def findPaginatedMembers(organizationId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[Account]]

}
