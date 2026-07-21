package com.togger.project

import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait ProjectRepo {
  def findById(id: UUID): ConnectionIO[Project]

  def insert(project: Project): ConnectionIO[Project]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(project: Project, updatedAt: Instant): ConnectionIO[Project]

  def findByOrganizationIdAndAccountId(
    organizationId: UUID,
    accountId: UUID
  ): ConnectionIO[Seq[(Project, ProjectAccount)]]

  def findAllProjectsByAccountId(accountId: UUID): ConnectionIO[Seq[(Project, ProjectAccount)]]

//  def addProjectMember(projectId: Long, accountId: Long): Unit
//  def removeProjectMember(projectId: Long, accountId: Long): Unit

}
