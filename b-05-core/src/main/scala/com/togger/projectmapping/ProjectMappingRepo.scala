package com.togger.projectmapping

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait ProjectMappingRepo {

  def insert(projectMapping: ProjectMapping): ConnectionIO[ProjectMapping]

  def findPaginatedByProjectIdDb(
    projectIdDb: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[ProjectMapping]]

  def findPaginatedByProjectIdDump(
    projectIdDump: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[ProjectMapping]]

  def deleteByIds(projectIdDb: UUID, projectIdDump: UUID): ConnectionIO[Int]

}
