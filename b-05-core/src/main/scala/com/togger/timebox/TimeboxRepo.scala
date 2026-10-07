package com.togger.timebox

import doobie.ConnectionIO

import java.util.UUID

trait TimeboxRepo {

  def insert(timebox: Timebox): ConnectionIO[Timebox]

  def findById(id: UUID): ConnectionIO[Timebox]

  def deleteById(id: UUID): ConnectionIO[Int]

  def findByProjectId(projectId: UUID): ConnectionIO[List[Timebox]]

}
