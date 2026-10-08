package com.togger.taskloadconfig

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait TaskLoadConfigRepo {

  def findById(id: UUID): ConnectionIO[TaskLoadConfig]

  def insert(taskLoadConfig: TaskLoadConfig): ConnectionIO[TaskLoadConfig]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(taskLoadConfig: TaskLoadConfig, updatedAt: Instant): ConnectionIO[TaskLoadConfig]

  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[TaskLoadConfig]]

}
