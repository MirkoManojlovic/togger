package com.togger.timebox

import com.togger.task.TaskVersion
import doobie.ConnectionIO

import java.util.UUID

/** Timebox versions are append-only: there is intentionally no update or delete by version. */
trait TimeboxVersionRepo {

  def insert(timeboxVersion: TimeboxVersion): ConnectionIO[TimeboxVersion]

  def insertPlannedTasks(timeboxVersion: UUID, taskVersions: Seq[UUID]): ConnectionIO[Int]

  def findByVersion(version: UUID): ConnectionIO[TimeboxVersion]

  def findByTimeboxId(timeboxId: UUID): ConnectionIO[List[TimeboxVersion]]

  def findLatestByTimeboxId(timeboxId: UUID): ConnectionIO[TimeboxVersion]

  def findPlannedTasks(timeboxVersion: UUID): ConnectionIO[List[TaskVersion]]

}
