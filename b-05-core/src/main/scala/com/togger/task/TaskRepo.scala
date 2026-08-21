package com.togger.task

import doobie.ConnectionIO

import java.time._
import java.util.UUID

trait TaskRepo {

  def findById(id: UUID): ConnectionIO[Task]

  def insert(task: Task): ConnectionIO[Task]

  def insertWithId(task: Task): ConnectionIO[Task]

  def deleteById(id: UUID): ConnectionIO[Int]

}

trait TaskVersionRepo {

  def insert(taskVersion: TaskVersion): ConnectionIO[TaskVersion]

  def insertWithId(taskVersion: TaskVersion): ConnectionIO[TaskVersion]

  def findByVersion(version: UUID): ConnectionIO[TaskVersion]

  def findByTaskId(taskId: UUID): ConnectionIO[List[TaskVersion]]

  def findLatestByTaskId(taskId: UUID): ConnectionIO[TaskVersion]

  def updateContent(taskVersion: TaskVersion): ConnectionIO[TaskVersion]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

  def countByTaskId(taskId: UUID): ConnectionIO[Int]

  def findByIdWithLatest(taskId: UUID): ConnectionIO[TaskFull]

  def findByProjectIdWithLatest(projectId: UUID): ConnectionIO[Seq[TaskFull]]

  def findAllTaskIdsByAssigneeId(accountId: UUID): ConnectionIO[List[UUID]]

}

trait TaskStatusRepo {

  def findById(id: UUID): ConnectionIO[TaskStatus]

  def insert(task: TaskStatus): ConnectionIO[TaskStatus]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(task: TaskStatus, updatedAt: Instant): ConnectionIO[TaskStatus]

  def findByProjectId(projectId: UUID): ConnectionIO[List[TaskStatus]]

}
