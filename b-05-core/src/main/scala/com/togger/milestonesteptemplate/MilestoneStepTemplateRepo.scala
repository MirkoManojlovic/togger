package com.togger.milestonesteptemplate

import com.togger.common.DbPageRequest
import com.togger.common.PaginatedResponse
import doobie.ConnectionIO

import java.util.UUID

trait MilestoneStepTemplateRepo {

  def insert(template: MilestoneStepTemplate): ConnectionIO[MilestoneStepTemplate]
  def insertWithId(template: MilestoneStepTemplate): ConnectionIO[MilestoneStepTemplate]

  def findById(id: UUID): ConnectionIO[MilestoneStepTemplate]

  /** Deletes the template together with all its versions. */
  def deleteById(id: UUID): ConnectionIO[Int]

  def findFullById(id: UUID): ConnectionIO[MilestoneStepTemplateFull]

  /** One page of the project's templates, by name. */
  def findPaginatedFullByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneStepTemplateFull]]

}

trait MilestoneStepTemplateVersionRepo {

  def insert(version: MilestoneStepTemplateVersion): ConnectionIO[MilestoneStepTemplateVersion]
  def insertWithId(version: MilestoneStepTemplateVersion): ConnectionIO[MilestoneStepTemplateVersion]

  def findByVersion(version: UUID): ConnectionIO[MilestoneStepTemplateVersion]

  def findLatestByTemplateId(templateId: UUID): ConnectionIO[MilestoneStepTemplateVersion]

  /** One page of the history of the template, newest version first. */
  def findPaginatedByTemplateId(
    templateId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneStepTemplateVersion]]

}
