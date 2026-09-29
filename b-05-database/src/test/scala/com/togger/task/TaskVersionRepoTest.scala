package com.togger.task

import cats.implicits.catsSyntaxApplicativeError
import com.togger.account.{Account, AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, TgIds, TimeHelpers}
import com.togger.organization.{TgProject, TgTaskStatus}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TaskVersionRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val taskRepo = new TaskRepoImpl
  val taskVersionRepo = new TaskVersionRepoImpl

  val task = Task(
    id = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    createdBy = TgIds.UNASSIGNED_UUID,
    createdAt = nowAsInstant
  )

  val v1 = "v1"
  val v2 = "v2"

  def taskVersion(
    taskId: UUID,
    taskStatusId: UUID,
    assignedTo: Option[UUID],
    suffix: String,
    priority: TaskPriority = TaskPriority.Medium
  ) = TaskVersion(
    version = UUID.randomUUID(),
    taskId = taskId,
    name = s"Task name $suffix",
    description = s"Task description $suffix",
    assignedTo = assignedTo,
    closedBy = None,
    taskStatusId = taskStatusId,
    priority = priority,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createTaskAndFixtures() =
    for {
      savedAccount <- createAccount
      orgAndProject <- createOrgAndProject
      savedProject = orgAndProject._2
      taskStatusSaved <- createTaskStatus(savedProject.id)
      savedTask <- taskRepo.insert(task.copy(projectId = savedProject.id, createdBy = savedAccount.id))
    } yield (savedAccount, savedProject, taskStatusSaved, savedTask)

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        fixtures <- createTaskAndFixtures()
        (savedAccount, _, taskStatusSaved, savedTask) = fixtures
        savedVersion <- taskVersionRepo.insert(
          taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v1, TaskPriority.Critical)
        )
        foundVersion1 <- taskVersionRepo.findByVersion(savedVersion.version)
        updatedVersion <- taskVersionRepo.updateContent(
          savedVersion.copy(description = "new description", priority = TaskPriority.Planned)
        )
        foundVersion2 <- taskVersionRepo.findByVersion(savedVersion.version)
        _ <- taskVersionRepo.deleteByVersion(foundVersion2.version)
        foundVersion3 <- taskVersionRepo.findByVersion(savedVersion.version).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedVersion shouldBe foundVersion1)
        cp(foundVersion1.priority shouldBe TaskPriority.Critical)
        cp(updatedVersion shouldBe foundVersion2)
        cp(updatedVersion.description shouldBe "new description")
        cp(updatedVersion.priority shouldBe TaskPriority.Planned)
        cp.reportAll()
        foundVersion3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find by task id, return newest first, and find the latest" in { transactor =>
      val res = for {
        fixtures <- createTaskAndFixtures()
        (savedAccount, _, taskStatusSaved, savedTask) = fixtures
        taskVersion1 <- taskVersionRepo.insert(
          taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v1)
        )
        taskVersion2 <- taskVersionRepo.insert(
          taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v2)
        )
        taskVersion3 <- taskVersionRepo.insert(
          taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), "v3")
        )
        foundVersions <- taskVersionRepo.findByTaskId(savedTask.id)
        latest <- taskVersionRepo.findLatestByTaskId(savedTask.id)
      } yield {
        val cp = new Checkpoint
        cp(
          foundVersions should contain theSameElementsInOrderAs List(
            taskVersion3,
            taskVersion2,
            taskVersion1
          )
        )
        cp.reportAll()
        latest shouldBe taskVersion3
      }

      res.transact(transactor)
    }

    "count versions for a task" in { transactor =>
      val res = for {
        fixtures <- createTaskAndFixtures()
        (savedAccount, _, taskStatusSaved, savedTask) = fixtures
        _ <- taskVersionRepo.insert(taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v1))
        _ <- taskVersionRepo.insert(taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v2))
        count <- taskVersionRepo.countByTaskId(savedTask.id)
      } yield count shouldBe 2

      res.transact(transactor)
    }

    "delete versions when the task is deleted" in { transactor =>
      val res = for {
        fixtures <- createTaskAndFixtures()
        (savedAccount, _, taskStatusSaved, savedTask) = fixtures
        _ <- taskVersionRepo.insert(taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v1))
        _ <- taskVersionRepo.insert(taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v2))
        _ <- taskRepo.deleteById(savedTask.id)
        remainingVersions <- taskVersionRepo.findByTaskId(savedTask.id)
      } yield remainingVersions shouldBe empty

      res.transact(transactor)
    }

    "find a task with its latest version, status and accounts resolved" in { transactor =>
      val res = for {
        fixtures <- createTaskAndFixtures()
        (savedAccount, _, taskStatusSaved, savedTask) = fixtures
        _ <- taskVersionRepo.insert(taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v1))
        latestVersion <- taskVersionRepo.insert(
          taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v2)
        )
        taskFull <- taskVersionRepo.findByIdWithLatest(savedTask.id)
      } yield {
        val cp = new Checkpoint
        cp(taskFull.id shouldBe savedTask.id)
        cp(taskFull.name shouldBe latestVersion.name)
        cp(taskFull.description shouldBe latestVersion.description)
        cp(taskFull.taskStatus shouldBe taskStatusSaved)
        cp(taskFull.createdBy shouldBe savedAccount)
        cp(taskFull.assignedTo shouldBe Some(savedAccount))
        cp(taskFull.closedBy shouldBe None)
        cp.reportAll()
        taskFull.latestVersion shouldBe latestVersion.version
      }

      res.transact(transactor)
    }

    "find all tasks for a project with latest content resolved" in { transactor =>
      val res = for {
        fixtures <- createTaskAndFixtures()
        (savedAccount, savedProject, taskStatusSaved, savedTask) = fixtures
        _ <- taskVersionRepo.insert(taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v1))
        latestVersion <- taskVersionRepo.insert(
          taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v2)
        )
        foundTasks <- taskVersionRepo.findByProjectIdWithLatest(savedProject.id)
      } yield {
        val cp = new Checkpoint
        cp(foundTasks.map(_.id) shouldBe Seq(savedTask.id))
        cp.reportAll()
        foundTasks.head.latestVersion shouldBe latestVersion.version
      }

      res.transact(transactor)
    }

    "find all task ids for the latest version's assignee" in { transactor =>
      val accountRepo = new AccountRepoImpl
      val res = for {
        fixtures <- createTaskAndFixtures()
        (savedAccount, _, taskStatusSaved, savedTask) = fixtures
        otherAccount <- accountRepo.insert(
          Account(
            id = TgIds.UNASSIGNED_UUID,
            firstName = "Jane",
            lastName = "Doe",
            email = "jane@email.com",
            password = passwordHashed,
            verified = false,
            enabled = true,
            accountTypeId = savedAccount.accountTypeId,
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
        _ <- taskVersionRepo.insert(taskVersion(savedTask.id, taskStatusSaved.id, Some(savedAccount.id), v1))
        _ <- taskVersionRepo.insert(taskVersion(savedTask.id, taskStatusSaved.id, Some(otherAccount.id), v2))
        foundForOriginalAssignee <- taskVersionRepo.findAllTaskIdsByAssigneeId(savedAccount.id)
        foundForLatestAssignee <- taskVersionRepo.findAllTaskIdsByAssigneeId(otherAccount.id)
      } yield {
        val cp = new Checkpoint
        cp(foundForOriginalAssignee shouldBe empty)
        cp.reportAll()
        foundForLatestAssignee shouldBe List(savedTask.id)
      }

      res.transact(transactor)
    }

  }

}
