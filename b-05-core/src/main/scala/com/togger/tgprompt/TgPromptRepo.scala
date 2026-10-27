package com.togger.tgprompt

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait TgPromptRepo {

  def insert(tgPrompt: TgPrompt): ConnectionIO[TgPrompt]
  def insertWithId(tgPrompt: TgPrompt): ConnectionIO[TgPrompt]

  def findById(id: UUID): ConnectionIO[TgPrompt]

  /** Deletes the prompt together with all its versions. */
  def deleteById(id: UUID): ConnectionIO[Int]

  def findFullById(id: UUID): ConnectionIO[TgPromptFull]

  /** One page of the account's prompts, newest first. */
  def findPaginatedFullByCreatedBy(
    createdBy: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TgPromptFull]]

}

trait TgPromptVersionRepo {

  def insert(version: TgPromptVersion): ConnectionIO[TgPromptVersion]
  def insertWithId(version: TgPromptVersion): ConnectionIO[TgPromptVersion]

  def findByVersion(version: UUID): ConnectionIO[TgPromptVersion]

  def findLatestByTgPromptId(tgPromptId: UUID): ConnectionIO[TgPromptVersion]

  /** One page of the history of the prompt, newest version first. */
  def findPaginatedByTgPromptId(
    tgPromptId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TgPromptVersion]]

  def countByTgPromptId(tgPromptId: UUID): ConnectionIO[Int]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

}
