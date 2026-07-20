package com.togger.organization

import com.togger.account.Account
import doobie.ConnectionIO

import java.util.UUID

trait OrganizationAccountRepo {
  def findById(organizationId: UUID, accountId: UUID): ConnectionIO[OrganizationAccount]

  def insert(organizationAccount: OrganizationAccount): ConnectionIO[OrganizationAccount]

  def deleteById(organizationId: UUID, accountId: UUID): ConnectionIO[Int]

//  def update(organizationAccount: OrganizationAccount, updatedAt: Instant): ConnectionIO[OrganizationAccount]

  def findAllMembers(organizationId: UUID): ConnectionIO[Seq[Account]]

}
