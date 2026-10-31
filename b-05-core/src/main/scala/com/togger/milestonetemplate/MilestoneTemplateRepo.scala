package com.togger.milestonetemplate

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait MilestoneTemplateRepo {

  def insert(template: MilestoneTemplate): ConnectionIO[MilestoneTemplate]
  def insertWithId(template: MilestoneTemplate): ConnectionIO[MilestoneTemplate]

  def findById(id: UUID): ConnectionIO[MilestoneTemplate]

  /** Deletes the template together with all its versions. */
  def deleteById(id: UUID): ConnectionIO[Int]

  def findFullById(id: UUID): ConnectionIO[MilestoneTemplateFull]

  /** One page of the project's templates, by name. */
  def findPaginatedFullByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneTemplateFull]]

  /** The names of the templates whose latest version contains the step template, by name. */
  def findNamesUsingStepTemplate(stepTemplateId: UUID): ConnectionIO[List[String]]

}

trait MilestoneTemplateVersionRepo {

  def insert(version: MilestoneTemplateVersion): ConnectionIO[MilestoneTemplateVersion]
  def insertWithId(version: MilestoneTemplateVersion): ConnectionIO[MilestoneTemplateVersion]

  /** Stores the ordered steps of a version; the position is the index in the list. */
  def insertSteps(version: UUID, stepTemplateIds: List[UUID]): ConnectionIO[Int]

  def findByVersion(version: UUID): ConnectionIO[MilestoneTemplateVersion]

  def findLatestByTemplateId(templateId: UUID): ConnectionIO[MilestoneTemplateVersion]

  /** One page of the history of the template, newest version first. */
  def findPaginatedByTemplateId(
    templateId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneTemplateVersion]]

  /** The ordered steps of each of the versions; a version without steps is absent from the result. */
  def findStepsByVersions(versions: List[UUID]): ConnectionIO[Map[UUID, List[MilestoneTemplateStep]]]

}
