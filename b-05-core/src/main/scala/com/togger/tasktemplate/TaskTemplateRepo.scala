package com.togger.tasktemplate

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait TaskTemplateRepo {

  def insert(taskTemplate: TaskTemplate): ConnectionIO[TaskTemplate]

  def findById(id: UUID): ConnectionIO[TaskTemplate]

  /** The template linked to the label, if any. */
  def findByLabelId(taskLabelId: UUID): ConnectionIO[Option[TaskTemplate]]

  /** Moves the link to another label, or removes it. The link is not versioned. */
  def updateLabel(id: UUID, taskLabelId: Option[UUID]): ConnectionIO[TaskTemplate]

  /** Deletes the template together with all its versions. */
  def deleteById(id: UUID): ConnectionIO[Int]

  def findFullById(id: UUID): ConnectionIO[TaskTemplateFull]

  def findFullByLabelId(taskLabelId: UUID): ConnectionIO[Option[TaskTemplateFull]]

  /** One page of the project's templates, by name. */
  def findPaginatedFullByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TaskTemplateFull]]

}

trait TaskTemplateVersionRepo {

  def insert(version: TaskTemplateVersion): ConnectionIO[TaskTemplateVersion]

  def findByVersion(version: UUID): ConnectionIO[TaskTemplateVersion]

  def findLatestByTemplateId(taskTemplateId: UUID): ConnectionIO[TaskTemplateVersion]

  /** One page of the history of the template, newest version first. */
  def findPaginatedByTemplateId(
    taskTemplateId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TaskTemplateVersion]]

  def countByTemplateId(taskTemplateId: UUID): ConnectionIO[Int]

}
