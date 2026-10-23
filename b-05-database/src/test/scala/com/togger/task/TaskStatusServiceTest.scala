package com.togger.task

import cats.syntax.applicativeError._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TgClockImpl, TimeHelpers}
import com.togger.organization.TgProject
import com.togger.taskStatus.TaskStatusRepoImpl
import doobie.implicits._

import java.util.UUID

class TaskStatusServiceTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val taskStatusRepo = new TaskStatusRepoImpl

  private def status(name: String, projectId: UUID) = TaskStatus(
    id = UUID.randomUUID(),
    name = name,
    description = "desc",
    emoji = "✅",
    projectId = projectId,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "refuse to delete the Done status" in { transactor =>
      val service = new TaskStatusService(taskStatusRepo, new TgClockImpl, transactor)
      for {
        organizationAndProject <- createOrgAndProject.transact(transactor)
        done <- taskStatusRepo
          .insert(status(DefaultTaskStatus.Done.statusName, organizationAndProject._2.id))
          .transact(transactor)
        result <- service.deleteById(done.id).attempt
        stillThere <- taskStatusRepo.findById(done.id).transact(transactor)
      } yield {
        result.isLeft shouldBe true
        stillThere shouldBe done
      }
    }

    "delete a non-Done status" in { transactor =>
      val service = new TaskStatusService(taskStatusRepo, new TgClockImpl, transactor)
      for {
        organizationAndProject <- createOrgAndProject.transact(transactor)
        other <- taskStatusRepo
          .insert(status(DefaultTaskStatus.Todo.statusName, organizationAndProject._2.id))
          .transact(transactor)
        deleted <- service.deleteById(other.id)
      } yield deleted shouldBe 1
    }
  }

}
