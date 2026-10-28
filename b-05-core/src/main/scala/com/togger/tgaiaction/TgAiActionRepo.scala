package com.togger.tgaiaction

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait TgAiActionRepo {

  def insert(action: TgAiAction): ConnectionIO[TgAiAction]
  def insertWithId(action: TgAiAction): ConnectionIO[TgAiAction]

  def findById(id: UUID): ConnectionIO[TgAiAction]

  /** Updates name, prompt, prompt template and model. */
  def update(action: TgAiAction): ConnectionIO[TgAiAction]

  def deleteById(id: UUID): ConnectionIO[Int]

  /** True when the account already has an action with this name, ignoring the action `exceptId`. */
  def existsByName(createdBy: UUID, name: String, exceptId: Option[UUID]): ConnectionIO[Boolean]

  /** One page of the account's actions, newest first. */
  def findPaginatedByCreatedBy(createdBy: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[TgAiAction]]

}

trait TgAiActionEndpointRepo {

  /** Makes the action bound to exactly these endpoints. */
  def replaceBindings(actionId: UUID, endpointIds: List[Int]): ConnectionIO[Unit]

  def findByActionIds(actionIds: List[UUID]): ConnectionIO[List[TgAiActionEndpoint]]

  def isBound(actionId: UUID, endpointId: Int): ConnectionIO[Boolean]

}

trait TgAiActionDefaultRepo {

  def upsert(default: TgAiActionDefault): ConnectionIO[TgAiActionDefault]

  def delete(accountId: UUID, projectId: UUID, endpointId: Int): ConnectionIO[Int]

  def find(accountId: UUID, projectId: UUID, endpointId: Int): ConnectionIO[Option[TgAiActionDefault]]

  def findByProject(accountId: UUID, projectId: UUID): ConnectionIO[List[TgAiActionDefault]]

}
