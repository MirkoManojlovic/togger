package com.togger.project

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait ProjectRepo {
  def findById(id: UUID): ConnectionIO[Project]

  def insert(project: Project): ConnectionIO[Project]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(project: Project, updatedAt: Instant): ConnectionIO[Project]

  /** One page of the organization's projects the account is a member of, by name. */
  def findPaginatedByOrganizationIdAndAccountId(
    organizationId: UUID,
    accountId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[Project]]

  /** One page of the projects the account is a member of, by name. With label ids, only the projects that have ALL of
    * the labels assigned; without, every project of the account.
    */
  def findPaginatedByAccountId(
    accountId: UUID,
    labelIds: Seq[UUID],
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[Project]]

//  def addProjectMember(projectId: Long, accountId: Long): Unit
//  def removeProjectMember(projectId: Long, accountId: Long): Unit

}
