package com.togger.taskdumpconfig

import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait TaskDumpConfigRepo {

  def findById(id: UUID): ConnectionIO[TaskDumpConfig]

  def insert(taskDumpConfig: TaskDumpConfig): ConnectionIO[TaskDumpConfig]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(taskDumpConfig: TaskDumpConfig, updatedAt: Instant): ConnectionIO[TaskDumpConfig]

  def findByProjectId(projectId: UUID): ConnectionIO[List[TaskDumpConfig]]

  def findByAccountId(accountId: UUID): ConnectionIO[List[TaskDumpConfig]]

}
