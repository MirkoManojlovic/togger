package com.togger.organization

import com.togger.account.Account
import doobie.ConnectionIO

import java.util.UUID

trait OrganizationAccountRepo {
  def findById(organizationId: Long, accountId: UUID): ConnectionIO[OrganizationAccount]

  def insert(organizationAccount: OrganizationAccount): ConnectionIO[OrganizationAccount]

  def deleteById(organizationId: Long, accountId: UUID): ConnectionIO[Int]

//  def update(organizationAccount: OrganizationAccount, updatedAt: Instant): ConnectionIO[OrganizationAccount]

  def findAllMembers(organizationId: Long): ConnectionIO[Seq[Account]]

}
