package com.togger.organization

import doobie.ConnectionIO

import java.time.Instant

trait OrganizationRepo {
  def findById(id: Long): ConnectionIO[Organization]

  def insert(organization: Organization): ConnectionIO[Organization]

  def deleteById(id: Long): ConnectionIO[Int]

  def update(organization: Organization, updatedAt: Instant): ConnectionIO[Organization]

  def findAll(): ConnectionIO[Seq[Organization]]

  def findAllOrgByAccountId(accountId: Long): ConnectionIO[Seq[(Organization, OrganizationAccount)]]

}
