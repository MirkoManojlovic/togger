package com.togger.task

import cats.implicits.catsSyntaxApplicativeError
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TgIds, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TaskRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val taskRepo = new TaskRepoImpl

  val task = Task(
    id = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    createdBy = TgIds.UNASSIGNED_UUID,
    createdAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        savedTask <- taskRepo.insert(task.copy(projectId = savedProject.id, createdBy = savedAccount.id))
        foundTask1 <- taskRepo.findById(savedTask.id)
        _ <- taskRepo.deleteById(foundTask1.id)
        foundTask2 <- taskRepo.findById(savedTask.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedTask shouldBe foundTask1)
        cp.reportAll()
        foundTask2.isLeft shouldBe true
      }

      res.transact(transactor)
    }

  }

}
