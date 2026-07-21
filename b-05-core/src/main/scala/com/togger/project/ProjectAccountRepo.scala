package com.togger.project

import com.togger.account.Account
import doobie.ConnectionIO

import java.util.UUID

trait ProjectAccountRepo {
  def findById(projectId: UUID, accountId: UUID): ConnectionIO[ProjectAccount]

  def insert(organizationAccount: ProjectAccount): ConnectionIO[ProjectAccount]

  def deleteById(projectId: UUID, accountId: UUID): ConnectionIO[Int]

//  def update(organizationAccount: ProjectAccount, updatedAt: Instant): ConnectionIO[ProjectAccount]

  def findAllMembers(projectId: UUID): ConnectionIO[Seq[Account]]

}
