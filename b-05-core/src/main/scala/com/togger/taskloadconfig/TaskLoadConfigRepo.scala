package com.togger.taskloadconfig

import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait TaskLoadConfigRepo {

  def findById(id: UUID): ConnectionIO[TaskLoadConfig]

  def insert(taskLoadConfig: TaskLoadConfig): ConnectionIO[TaskLoadConfig]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(taskLoadConfig: TaskLoadConfig, updatedAt: Instant): ConnectionIO[TaskLoadConfig]

  def findByProjectId(projectId: UUID): ConnectionIO[List[TaskLoadConfig]]

  def findByAccountId(accountId: UUID): ConnectionIO[List[TaskLoadConfig]]

}
