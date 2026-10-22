package com.togger.dataexportconfig

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait DataExportConfigRepo {

  def findById(id: UUID): ConnectionIO[DataExportConfig]

  def insert(dataExportConfig: DataExportConfig): ConnectionIO[DataExportConfig]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(dataExportConfig: DataExportConfig, updatedAt: Instant): ConnectionIO[DataExportConfig]

  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[DataExportConfig]]

}
