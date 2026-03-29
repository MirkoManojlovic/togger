package com.togger.organization

import com.togger.account.Account
import doobie.ConnectionIO

trait OrganizationAccountRepo {
  def findById(organizationId: Long, accountId: Long): ConnectionIO[OrganizationAccount]

  def insert(organizationAccount: OrganizationAccount): ConnectionIO[OrganizationAccount]

  def deleteById(organizationId: Long, accountId: Long): ConnectionIO[Int]

//  def update(organizationAccount: OrganizationAccount, updatedAt: Instant): ConnectionIO[OrganizationAccount]

  def findAllMembers(organizationId: Long): ConnectionIO[Seq[Account]]

}
