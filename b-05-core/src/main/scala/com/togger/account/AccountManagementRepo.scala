package com.togger.account

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

case class AccountProjectRef(projectId: UUID, organizationId: UUID, projectName: String)

case class AccountWithProjects(
  id: UUID,
  firstName: String,
  lastName: String,
  email: String,
  accountTypeId: UUID,
  accountTypeName: String,
  projects: List[AccountProjectRef]
)

trait AccountManagementRepo {

  /** One page of every account by last name, first name, each with the projects it is a member of. */
  def findPaginated(page: DbPageRequest): ConnectionIO[PaginatedResponse[AccountWithProjects]]

  def findOne(id: UUID): ConnectionIO[Option[AccountWithProjects]]

  def findAccountTypes: ConnectionIO[List[AccountTypeRef]]

  /** Deletes the account together with its sessions and its organization and project memberships. Fails with a
    * foreign key violation when other data (tasks, log entries, ...) still references the account.
    */
  def deleteWithMemberships(id: UUID): ConnectionIO[Int]
}

case class AccountTypeRef(id: UUID, name: String)
