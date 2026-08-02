package com.togger.task

import cats.implicits.{catsSyntaxApplicativeError, toTraverseOps}
import com.togger.account._
import com.togger.common.{BusinessTgDbSpec, TgIds, TimeHelpers}
import com.togger.organization.{TgProject, TgTaskStatus}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class LinkRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val taskRepo = new TaskRepoImpl
  val linkRepo = new LinkRepoImpl

  val task = Task(
    id = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    name = "Task name",
    description = "Task description",
    assignedTo = None,
    taskStatusId = UUID.randomUUID(),
    createdBy = TgIds.UNASSIGNED_UUID,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  val link = Link(
    id = UUID.randomUUID(),
    taskId = UUID.randomUUID(),
    createdAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        taskStatusSaved <- createTaskStatus(savedProject.id)
        savedTask <- taskRepo.insert(task =
          task.copy(
            projectId = savedProject.id,
            assignedTo = Some(savedAccount.id),
            createdBy = savedAccount.id,
            taskStatusId = taskStatusSaved.id
          )
        )
        savedLink <- linkRepo.insert(link.copy(taskId = savedTask.id))
        foundLink1 <- linkRepo.findById(savedLink.id)
        _ <- linkRepo.deleteById(foundLink1.id)
        foundLink2 <- linkRepo.findById(savedLink.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedLink shouldBe foundLink1)
        cp.reportAll()
        foundLink2.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "insert many links" in { transactor =>
      def createLinks(taskId: UUID) = (0 to 10).map(index =>
        Link(
          id = UUID.randomUUID(),
          taskId = taskId,
          createdAt = nowAsInstant
        )
      )

      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        taskStatusSaved <- createTaskStatus(savedProject.id)
        savedTask <- taskRepo.insert(task =
          task.copy(
            projectId = savedProject.id,
            assignedTo = Some(savedAccount.id),
            createdBy = savedAccount.id,
            taskStatusId = taskStatusSaved.id
          )
        )
        linksToInsert = createLinks(savedTask.id)
        savedLinks <- linkRepo.insertMany(linksToInsert)
        foundLinks <- savedLinks.map(link => linkRepo.findById(link.id)).sequence
      } yield {
        foundLinks shouldBe savedLinks
      }

      res.transact(transactor)
    }

    "find by task id" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        taskStatusSaved <- createTaskStatus(savedProject.id)
        savedTask <- taskRepo.insert(task =
          task.copy(
            projectId = savedProject.id,
            assignedTo = Some(savedAccount.id),
            createdBy = savedAccount.id,
            taskStatusId = taskStatusSaved.id
          )
        )
        linksToInsert = createLinks(savedTask.id)
        savedLinks <- linkRepo.insertMany(linksToInsert)
        foundLinks <- linkRepo.findByTaskId(savedTask.id)
      } yield {
        foundLinks shouldBe savedLinks
      }

      res.transact(transactor)
    }

    def createLinks(taskId: UUID) = (0 to 10).map(index =>
      Link(
        id = UUID.randomUUID(),
        taskId = taskId,
        createdAt = nowAsInstant
      )
    )

    "delete by task id" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        taskStatusSaved <- createTaskStatus(savedProject.id)
        savedTask <- taskRepo.insert(task =
          task.copy(
            projectId = savedProject.id,
            assignedTo = Some(savedAccount.id),
            createdBy = savedAccount.id,
            taskStatusId = taskStatusSaved.id
          )
        )
        linksToInsert = createLinks(savedTask.id)
        savedLinks <- linkRepo.insertMany(linksToInsert)
        numberOfDeletedLinks <- linkRepo.deleteByTaskId(savedTask.id)
        foundLinks <- linkRepo.findByTaskId(savedTask.id)
      } yield {
        val cp = new Checkpoint
        cp(numberOfDeletedLinks shouldBe savedLinks.size)
        cp.reportAll()
        foundLinks shouldBe empty
      }

      res.transact(transactor)
    }

  }

}
