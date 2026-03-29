package com.togger.task

import cats.implicits.catsSyntaxApplicativeError
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import com.togger.project.{Project, ProjectRepoImpl}
import com.togger.taskStatus.TaskStatusRepoImpl
import doobie.implicits._
import doobie.postgres.implicits._

class TaskStatusRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val taskStatusRepo = new TaskStatusRepoImpl

  val task = TaskStatus(
    id = -1L,
    name = "In progress",
    description = "Doing things",
    projectId = -1L,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        organizationAndProject <- createOrgAndProject
        savedProject = organizationAndProject._2
        savedTaskStatus <- taskStatusRepo.insert(task.copy(projectId = savedProject.id))
        foundTask1 <- taskStatusRepo.findById(savedTaskStatus.id)
        newUpdatedAt = nowAsInstant
        updatedTask <- taskStatusRepo.update(savedTaskStatus.copy(name = "In progress new"), newUpdatedAt)
        foundTask2 <- taskStatusRepo.findById(savedTaskStatus.id)
        _ <- taskStatusRepo.deleteById(foundTask2.id)
        foundTask3 <- taskStatusRepo.findById(savedTaskStatus.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedTaskStatus shouldBe foundTask1)
        cp(updatedTask shouldBe foundTask2)
        cp.reportAll()
        foundTask3.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

}
