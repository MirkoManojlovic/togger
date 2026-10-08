package com.togger.taskdumpconfig

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait TaskDumpConfigRepo {

  def findById(id: UUID): ConnectionIO[TaskDumpConfig]

  def insert(taskDumpConfig: TaskDumpConfig): ConnectionIO[TaskDumpConfig]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(taskDumpConfig: TaskDumpConfig, updatedAt: Instant): ConnectionIO[TaskDumpConfig]

  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[TaskDumpConfig]]

}
