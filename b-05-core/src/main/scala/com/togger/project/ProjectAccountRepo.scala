package com.togger.project

import com.togger.account.Account
import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait ProjectAccountRepo {
  def findById(projectId: UUID, accountId: UUID): ConnectionIO[ProjectAccount]

  def insert(organizationAccount: ProjectAccount): ConnectionIO[ProjectAccount]

  def deleteById(projectId: UUID, accountId: UUID): ConnectionIO[Int]

  def isMember(projectId: UUID, accountId: UUID): ConnectionIO[Boolean]

//  def update(organizationAccount: ProjectAccount, updatedAt: Instant): ConnectionIO[ProjectAccount]

  /** One page of the project's members by last name, first name. */
  def findPaginatedMembers(projectId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[Account]]

  /** Members of every given project; bounded by caller (the ids come from a page of projects). */
  def findAllMembersByProjectIds(projectIds: Seq[UUID]): ConnectionIO[Map[UUID, Seq[Account]]]

}
