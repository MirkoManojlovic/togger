package com.togger.deletedentity

import com.togger.db.Util._
import com.togger.deletedentity.DeletedEntityRepoImpl.DELETED_ENTITY_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update

import java.util.UUID

class DeletedEntityRepoImpl extends DeletedEntityRepo {

  override def insertAll(deletedEntities: List[DeletedEntity]): ConnectionIO[Int] =
    logAroundInfoQuery(
      Update[DeletedEntity](
        s"""insert into ${DeletedEntityRepoImpl.DELETED_ENTITY_TABLE} (project_id, entity_type, entity_id, deleted_at)
           |values (?, ?, ?, ?)
           |on conflict (project_id, entity_type, entity_id)
           |do update set deleted_at = greatest(${DeletedEntityRepoImpl.DELETED_ENTITY_TABLE}.deleted_at, excluded.deleted_at)""".stripMargin
      ).updateMany(deletedEntities)
    )(
      s"Inserting ${sp(deletedEntities.size)} deleted entities",
      count => s"Deleted entities inserted ${sp(count)}"
    )

  override def findAllByProjectId(projectId: UUID): ConnectionIO[List[DeletedEntity]] =
    logAroundInfoQuery(
      (selectAllStatement(DELETED_ENTITY_TABLE_FRAG) ++ fr"where project_id = $projectId" ++
        fr"order by entity_type, entity_id").query[DeletedEntity].to[List]
    )(
      s"Finding the deleted entities of ${sp(projectId)}",
      deleted => s"Deleted entities found ${sp(deleted.size)} for ${sp(projectId)}"
    )

  override def deleteByEntity(projectId: UUID, entityType: String, entityId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(DELETED_ENTITY_TABLE_FRAG) ++
        fr"where project_id = $projectId and entity_type = $entityType and entity_id = $entityId").update.run
    )(
      s"Deleting the tombstone of $entityType ${sp(entityId)} of ${sp(projectId)}",
      _ => s"Tombstone of $entityType ${sp(entityId)} of ${sp(projectId)} deleted"
    )

}

object DeletedEntityRepoImpl {
  val DELETED_ENTITY_TABLE: String = "deleted_entity"
  val DELETED_ENTITY_TABLE_FRAG: Fragment = Fragment.const(DELETED_ENTITY_TABLE)
}
