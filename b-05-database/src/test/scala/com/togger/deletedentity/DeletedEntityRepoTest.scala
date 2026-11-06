package com.togger.deletedentity

import com.togger.common.BusinessTgDbSpec
import com.togger.common.TimeHelpers
import com.togger.organization.TgProject
import doobie.implicits._

import java.util.UUID

class DeletedEntityRepoTest extends BusinessTgDbSpec with TgProject with TimeHelpers {

  private val deletedEntityRepo = new DeletedEntityRepoImpl

  testNameFromClassName should {

    "insert, find and delete tombstones of a project" in { transactor =>
      val taskId = UUID.randomUUID()
      val logEntryId = UUID.randomUUID()
      val res = for {
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        inserted <- deletedEntityRepo.insertAll(
          List(
            DeletedEntity(projectId, DeletedEntityType.Task, taskId, nowAsInstant),
            DeletedEntity(projectId, DeletedEntityType.LogEntry, logEntryId, nowAsInstant)
          )
        )
        found <- deletedEntityRepo.findAllByProjectId(projectId)
        removed <- deletedEntityRepo.deleteByEntity(projectId, DeletedEntityType.Task, taskId)
        remaining <- deletedEntityRepo.findAllByProjectId(projectId)
      } yield {
        inserted shouldBe 2
        found.map(e => (e.entityType, e.entityId)).toSet shouldBe
          Set((DeletedEntityType.Task, taskId), (DeletedEntityType.LogEntry, logEntryId))
        removed shouldBe 1
        remaining.map(_.entityId) shouldBe List(logEntryId)
      }

      res.transact(transactor)
    }

    "keep the newest deletion time when an entity is deleted again" in { transactor =>
      val taskId = UUID.randomUUID()
      val res = for {
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        _ <- deletedEntityRepo.insertAll(
          List(DeletedEntity(projectId, DeletedEntityType.Task, taskId, nowAsInstant.plusSeconds(60)))
        )
        _ <- deletedEntityRepo.insertAll(List(DeletedEntity(projectId, DeletedEntityType.Task, taskId, nowAsInstant)))
        found <- deletedEntityRepo.findAllByProjectId(projectId)
      } yield found.map(_.deletedAt.getEpochSecond) shouldBe List(nowAsInstant.plusSeconds(60).getEpochSecond)

      res.transact(transactor)
    }

  }

}
