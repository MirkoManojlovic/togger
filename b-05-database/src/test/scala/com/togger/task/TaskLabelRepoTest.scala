package com.togger.task

import cats.implicits.catsSyntaxApplicativeError
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TaskLabelRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val taskLabelRepo = new TaskLabelRepoImpl

  val label = TaskLabel(
    id = UUID.randomUUID(),
    name = "feature",
    description = "New functionality",
    emoji = "🆕",
    projectId = UUID.randomUUID(),
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        organizationAndProject <- createOrgAndProject
        savedProject = organizationAndProject._2
        savedTaskLabel <- taskLabelRepo.insert(label.copy(projectId = savedProject.id))
        foundLabel1 <- taskLabelRepo.findById(savedTaskLabel.id)
        newUpdatedAt = nowAsInstant
        updatedLabel <- taskLabelRepo.update(savedTaskLabel.copy(name = "bug"), newUpdatedAt)
        foundLabel2 <- taskLabelRepo.findById(savedTaskLabel.id)
        _ <- taskLabelRepo.deleteById(foundLabel2.id)
        foundLabel3 <- taskLabelRepo.findById(savedTaskLabel.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedTaskLabel shouldBe foundLabel1)
        cp(updatedLabel shouldBe foundLabel2)
        cp.reportAll()
        foundLabel3.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

}
