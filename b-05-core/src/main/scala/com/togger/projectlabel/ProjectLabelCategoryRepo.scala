package com.togger.projectlabel

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait ProjectLabelCategoryRepo {
  def findById(id: UUID): ConnectionIO[ProjectLabelCategory]
  def insert(projectLabelCategory: ProjectLabelCategory): ConnectionIO[ProjectLabelCategory]
  def deleteById(id: UUID): ConnectionIO[Int]
  def update(projectLabelCategory: ProjectLabelCategory, updatedAt: Instant): ConnectionIO[ProjectLabelCategory]
  /** One page of all categories, by name. */
  def findPaginated(page: DbPageRequest): ConnectionIO[PaginatedResponse[ProjectLabelCategory]]
}
