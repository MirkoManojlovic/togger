package com.togger.task

import doobie.ConnectionIO

import java.time._

trait TaskRepo {

  def findById(id: Long): ConnectionIO[Task]

  def findByIdWithStatus(id: Long): ConnectionIO[(Task, TaskStatus)]

  def findByIdWithStatusAndAccount(id: Long): ConnectionIO[TaskFull]

  def insert(task: Task): ConnectionIO[Task]

  def deleteById(id: Long): ConnectionIO[Int]

  def update(task: Task, updatedAt: Instant): ConnectionIO[Task]

  def findByProjectId(projectId: Long): ConnectionIO[Seq[TaskFull]]

  def findAllTaskIdsByAssigneeId(accountId: Long): ConnectionIO[List[Long]]

}

trait TaskStatusRepo {

  def findById(id: Long): ConnectionIO[TaskStatus]

  def insert(task: TaskStatus): ConnectionIO[TaskStatus]

  def deleteById(id: Long): ConnectionIO[Int]

  def update(task: TaskStatus, updatedAt: Instant): ConnectionIO[TaskStatus]

  def findByProjectId(projectId: Long): ConnectionIO[List[TaskStatus]]

}
