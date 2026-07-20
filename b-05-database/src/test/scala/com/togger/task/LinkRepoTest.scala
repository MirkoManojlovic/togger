package com.togger.task

import cats.implicits.{catsSyntaxApplicativeError, toTraverseOps}
import com.togger.account._
import com.togger.common.{BusinessTgDbSpec, TgIds, TimeHelpers}
import com.togger.organization.{TgProject, TgTaskStatus}
import doobie.implicits._
import doobie.postgres.implicits._

class LinkRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val taskRepo = new TaskRepoImpl
  val linkRepo = new LinkRepoImpl

  val task = Task(
    id = -1L,
    projectId = -1L,
    name = "Task name",
    description = "Task description",
    assignedTo = None,
    taskStatusId = -1L,
    createdBy = TgIds.UNASSIGNED_ACCOUNT_ID,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  val link = Link(
    id = -1L,
    name = "link name",
    url = "example url",
    taskId = -1L,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
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
        newUpdatedAt = nowAsInstant
        updatedLink <- linkRepo.update(savedLink.copy(url = "new url"), newUpdatedAt)
        foundLink2 <- linkRepo.findById(savedLink.id)
        _ <- linkRepo.deleteById(foundLink2.id)
        foundLink3 <- linkRepo.findById(savedLink.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedLink shouldBe foundLink1)
        cp(updatedLink shouldBe foundLink2)
        cp.reportAll()
        foundLink3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "insert many links" in { transactor =>
      def createLinks(taskId: Long) = (0 to 10).map(index =>
        Link(
          id = -1,
          name = s"Link $index",
          url = s"url-$index",
          taskId = taskId,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
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

    def createLinks(taskId: Long) = (0 to 10).map(index =>
      Link(
        id = -1,
        name = s"Link $index",
        url = s"url-$index",
        taskId = taskId,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
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
