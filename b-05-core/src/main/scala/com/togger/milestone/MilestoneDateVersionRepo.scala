package com.togger.milestone

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait MilestoneDateVersionRepo {

  def insert(milestoneDateVersion: MilestoneDateVersion): ConnectionIO[MilestoneDateVersion]

  def findByVersion(version: UUID): ConnectionIO[MilestoneDateVersion]

  def findPaginatedByMilestoneDateId(
    milestoneDateId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneDateVersion]]

  def findLatestByMilestoneDateId(milestoneDateId: UUID): ConnectionIO[MilestoneDateVersion]

  def updateContent(milestoneDateVersion: MilestoneDateVersion): ConnectionIO[MilestoneDateVersion]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

  def countByMilestoneDateId(milestoneDateId: UUID): ConnectionIO[Int]

}
