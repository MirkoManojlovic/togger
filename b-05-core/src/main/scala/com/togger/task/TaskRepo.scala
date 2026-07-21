package com.togger.task

import doobie.ConnectionIO

import java.time._
import java.util.UUID

trait TaskRepo {

  def findById(id: UUID): ConnectionIO[Task]

  def findByIdWithStatus(id: UUID): ConnectionIO[(Task, TaskStatus)]

  def findByIdWithStatusAndAccount(id: UUID): ConnectionIO[TaskFull]

  def insert(task: Task): ConnectionIO[Task]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(task: Task, updatedAt: Instant): ConnectionIO[Task]

  def findByProjectId(projectId: UUID): ConnectionIO[Seq[TaskFull]]

  def findAllTaskIdsByAssigneeId(accountId: UUID): ConnectionIO[List[UUID]]

}

trait TaskStatusRepo {

  def findById(id: UUID): ConnectionIO[TaskStatus]

  def insert(task: TaskStatus): ConnectionIO[TaskStatus]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(task: TaskStatus, updatedAt: Instant): ConnectionIO[TaskStatus]

  def findByProjectId(projectId: UUID): ConnectionIO[List[TaskStatus]]

}
