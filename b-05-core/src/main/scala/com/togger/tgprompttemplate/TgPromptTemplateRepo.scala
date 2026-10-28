package com.togger.tgprompttemplate

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait TgPromptTemplateRepo {

  def insert(tgPromptTemplate: TgPromptTemplate): ConnectionIO[TgPromptTemplate]
  def insertWithId(tgPromptTemplate: TgPromptTemplate): ConnectionIO[TgPromptTemplate]

  def findById(id: UUID): ConnectionIO[TgPromptTemplate]

  /** Deletes the prompt together with all its versions. */
  def deleteById(id: UUID): ConnectionIO[Int]

  def findFullById(id: UUID): ConnectionIO[TgPromptTemplateFull]

  /** One page of the account's prompts, newest first. */
  def findPaginatedFullByCreatedBy(
    createdBy: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TgPromptTemplateFull]]

}

trait TgPromptTemplateVersionRepo {

  def insert(version: TgPromptTemplateVersion): ConnectionIO[TgPromptTemplateVersion]
  def insertWithId(version: TgPromptTemplateVersion): ConnectionIO[TgPromptTemplateVersion]

  def findByVersion(version: UUID): ConnectionIO[TgPromptTemplateVersion]

  def findLatestByTgPromptTemplateId(tgPromptTemplateId: UUID): ConnectionIO[TgPromptTemplateVersion]

  /** One page of the history of the prompt, newest version first. */
  def findPaginatedByTgPromptTemplateId(
    tgPromptTemplateId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TgPromptTemplateVersion]]

  def countByTgPromptTemplateId(tgPromptTemplateId: UUID): ConnectionIO[Int]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

}
