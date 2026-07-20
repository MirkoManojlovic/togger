package com.togger.organization

import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait OrganizationRepo {
  def findById(id: UUID): ConnectionIO[Organization]

  def insert(organization: Organization): ConnectionIO[Organization]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(organization: Organization, updatedAt: Instant): ConnectionIO[Organization]

  def findAll(): ConnectionIO[Seq[Organization]]

  def findAllOrgByAccountId(accountId: UUID): ConnectionIO[Seq[(Organization, OrganizationAccount)]]

}
