package com.togger.project

import com.togger.account.Account
import doobie.ConnectionIO

import java.util.UUID

trait ProjectAccountRepo {
  def findById(projectId: Long, accountId: UUID): ConnectionIO[ProjectAccount]

  def insert(organizationAccount: ProjectAccount): ConnectionIO[ProjectAccount]

  def deleteById(projectId: Long, accountId: UUID): ConnectionIO[Int]

//  def update(organizationAccount: ProjectAccount, updatedAt: Instant): ConnectionIO[ProjectAccount]

  def findAllMembers(projectId: Long): ConnectionIO[Seq[Account]]

}
