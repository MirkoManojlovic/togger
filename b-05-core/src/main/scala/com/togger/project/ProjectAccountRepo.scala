package com.togger.project

import com.togger.account.Account
import doobie.ConnectionIO

trait ProjectAccountRepo {
  def findById(projectId: Long, accountId: Long): ConnectionIO[ProjectAccount]

  def insert(organizationAccount: ProjectAccount): ConnectionIO[ProjectAccount]

  def deleteById(projectId: Long, accountId: Long): ConnectionIO[Int]

//  def update(organizationAccount: ProjectAccount, updatedAt: Instant): ConnectionIO[ProjectAccount]

  def findAllMembers(projectId: Long): ConnectionIO[Seq[Account]]

}
