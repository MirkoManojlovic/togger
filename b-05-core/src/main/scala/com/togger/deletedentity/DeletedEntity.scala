package com.togger.deletedentity

import java.time.Instant
import java.util.UUID

/** The tombstone of a deleted entity of a project. `entityType` is the export folder of the entity, see
  * [[DeletedEntityType]].
  */
case class DeletedEntity(projectId: UUID, entityType: String, entityId: UUID, deletedAt: Instant)

object DeletedEntityType {
  val Task: String = "tasks"
  val LogEntry: String = "logentries"
}
