package com.togger.projectlabel

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait ProjectLabelRepo {
  def findById(id: UUID): ConnectionIO[ProjectLabel]
  def insert(projectLabel: ProjectLabel): ConnectionIO[ProjectLabel]
  def deleteById(id: UUID): ConnectionIO[Int]
  def update(projectLabel: ProjectLabel, updatedAt: Instant): ConnectionIO[ProjectLabel]
  /** One page of all labels, by name. */
  def findPaginated(page: DbPageRequest): ConnectionIO[PaginatedResponse[ProjectLabel]]
}
