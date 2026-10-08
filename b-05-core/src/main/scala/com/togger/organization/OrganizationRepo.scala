package com.togger.organization

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait OrganizationRepo {
  def findById(id: UUID): ConnectionIO[Organization]

  def insert(organization: Organization): ConnectionIO[Organization]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(organization: Organization, updatedAt: Instant): ConnectionIO[Organization]

  /** One page of every organization, by name. Cross-tenant: callers must be admin-only. */
  def findPaginated(page: DbPageRequest): ConnectionIO[PaginatedResponse[Organization]]

  /** One page of the organizations the account is a member of, by name. */
  def findPaginatedByAccountId(accountId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[Organization]]

}
