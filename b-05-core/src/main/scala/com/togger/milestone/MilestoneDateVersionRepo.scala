package com.togger.milestone

import doobie.ConnectionIO

import java.util.UUID

trait MilestoneDateVersionRepo {

  def insert(milestoneDateVersion: MilestoneDateVersion): ConnectionIO[MilestoneDateVersion]

  def findByVersion(version: UUID): ConnectionIO[MilestoneDateVersion]

  def findByMilestoneDateId(milestoneDateId: UUID): ConnectionIO[List[MilestoneDateVersion]]

  def findLatestByMilestoneDateId(milestoneDateId: UUID): ConnectionIO[MilestoneDateVersion]

  def updateContent(milestoneDateVersion: MilestoneDateVersion): ConnectionIO[MilestoneDateVersion]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

  def countByMilestoneDateId(milestoneDateId: UUID): ConnectionIO[Int]

}
