package com.togger.dataimportconfig

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait DataImportConfigRepo {

  def findById(id: UUID): ConnectionIO[DataImportConfig]

  def insert(dataImportConfig: DataImportConfig): ConnectionIO[DataImportConfig]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(dataImportConfig: DataImportConfig, updatedAt: Instant): ConnectionIO[DataImportConfig]

  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[DataImportConfig]]

}
