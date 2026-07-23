package com.togger.logentry

import doobie.ConnectionIO

import java.util.UUID

trait LogEntryVersionRepo {

  def insert(logEntryVersion: LogEntryVersion): ConnectionIO[LogEntryVersion]

  def findByVersion(version: UUID): ConnectionIO[LogEntryVersion]

  def findByLogEntryId(logEntryId: UUID): ConnectionIO[List[LogEntryVersion]]

  def findLatestByLogEntryId(logEntryId: UUID): ConnectionIO[LogEntryVersion]

  def updateContent(logEntryVersion: LogEntryVersion): ConnectionIO[LogEntryVersion]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

  def countByLogEntryId(logEntryId: UUID): ConnectionIO[Int]

}
