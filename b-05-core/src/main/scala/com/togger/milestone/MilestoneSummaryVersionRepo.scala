package com.togger.milestone

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait MilestoneSummaryVersionRepo {

  def insert(milestoneSummaryVersion: MilestoneSummaryVersion): ConnectionIO[MilestoneSummaryVersion]
  def insertWithId(milestoneSummaryVersion: MilestoneSummaryVersion): ConnectionIO[MilestoneSummaryVersion]

  def findByVersion(version: UUID): ConnectionIO[MilestoneSummaryVersion]

  def findPaginatedByMilestoneSummaryId(
    milestoneSummaryId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneSummaryVersion]]

  def findLatestByMilestoneSummaryId(milestoneSummaryId: UUID): ConnectionIO[MilestoneSummaryVersion]

  def updateContent(milestoneSummaryVersion: MilestoneSummaryVersion): ConnectionIO[MilestoneSummaryVersion]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

  def countByMilestoneSummaryId(milestoneSummaryId: UUID): ConnectionIO[Int]

}
