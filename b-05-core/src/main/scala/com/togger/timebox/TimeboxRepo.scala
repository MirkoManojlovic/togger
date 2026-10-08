package com.togger.timebox

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait TimeboxRepo {

  def insert(timebox: Timebox): ConnectionIO[Timebox]

  def findById(id: UUID): ConnectionIO[Timebox]

  def deleteById(id: UUID): ConnectionIO[Int]

  /** One page of the project's timeboxes, the one whose latest version starts last first. */
  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[Timebox]]

}
