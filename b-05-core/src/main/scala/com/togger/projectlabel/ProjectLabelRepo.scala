package com.togger.projectlabel

import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait ProjectLabelRepo {
  def findById(id: UUID): ConnectionIO[ProjectLabel]
  def insert(projectLabel: ProjectLabel): ConnectionIO[ProjectLabel]
  def deleteById(id: UUID): ConnectionIO[Int]
  def update(projectLabel: ProjectLabel, updatedAt: Instant): ConnectionIO[ProjectLabel]
  def findAll: ConnectionIO[List[ProjectLabel]]
}
