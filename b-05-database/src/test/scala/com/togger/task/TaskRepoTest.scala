package com.togger.task

import cats.implicits.{catsSyntaxApplicativeError, toTraverseOps}
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TgIds, TimeHelpers}
import com.togger.organization.{TgProject, TgTaskStatus}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TaskRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val taskRepo = new TaskRepoImpl

  val task = Task(
    id = -1L,
    projectId = -1L,
    name = "Task name",
    description = "Task description",
    assignedTo = None,
    taskStatusId = UUID.randomUUID(),
    createdBy = TgIds.UNASSIGNED_UUID,
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
        foundTask1 <- taskRepo.findById(savedTask.id)
        newUpdatedAt = nowAsInstant
        updatedTask <- taskRepo.update(savedTask.copy(name = "new task name"), newUpdatedAt)
        foundTask2 <- taskRepo.findById(savedTask.id)
        _ <- taskRepo.deleteById(foundTask2.id)
        foundTask3 <- taskRepo.findById(savedTask.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedTask shouldBe foundTask1)
        cp(updatedTask shouldBe foundTask2)
        cp.reportAll()
        foundTask3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find all task ids for account id" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        taskStatusSaved <- createTaskStatus(savedProject.id)
        tasksWithAccountId = (0 to 10).map { _ =>
          task.copy(
            projectId = savedProject.id,
            assignedTo = Some(savedAccount.id),
            createdBy = savedAccount.id,
            taskStatusId = taskStatusSaved.id
          )
        }
        savedTasks <- tasksWithAccountId.map(task => taskRepo.insert(task)).toList.sequence
        foundTasks <- taskRepo.findAllTaskIdsByAssigneeId(savedAccount.id)
      } yield {
        savedTasks.map(_.id).toSet shouldBe foundTasks.toSet
      }

      res.transact(transactor)
    }

    "find all task ids for project id" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        taskStatusSaved <- createTaskStatus(savedProject.id)
        tasksWithAccountId = (0 to 10).map { _ =>
          task.copy(
            projectId = savedProject.id,
            assignedTo = Some(savedAccount.id),
            createdBy = savedAccount.id,
            taskStatusId = taskStatusSaved.id
          )
        }
        savedTasks <- tasksWithAccountId.map(task => taskRepo.insert(task)).toList.sequence
        foundTasks <- taskRepo.findByProjectId(savedProject.id)
      } yield {
        savedTasks
          .map(task =>
            task.toFull(
              createdBy = savedAccount,
              assignedToOpt = Some(savedAccount),
              closedByOpt = None,
              taskStatus = taskStatusSaved
            )
          )
          .toSet shouldBe foundTasks.toSet
      }

      res.transact(transactor)
    }

    "find task with status" in { transactor =>
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
        taskAndStatus <- taskRepo.findByIdWithStatus(savedTask.id)
        (foundTask, foundStatus) = taskAndStatus
      } yield {
        val cp = new Checkpoint
        cp(savedTask shouldBe foundTask)
        cp.reportAll()
        foundStatus shouldBe taskStatusSaved
      }

      res.transact(transactor)
    }

    "find task with status, createdBy and assigned to" in { transactor =>
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
        taskFull <- taskRepo.findByIdWithStatusAndAccount(savedTask.id)
//        (foundTask, foundStatus, createdBy, assignedToOpt, closedByOpt) = taskAndStatus
      } yield {
        val cp = new Checkpoint
        cp(savedTask.id shouldBe taskFull.id)
        cp(taskFull.taskStatus shouldBe taskStatusSaved)
        cp(taskFull.createdBy shouldBe savedAccount)
        cp(taskFull.assignedTo shouldBe Some(savedAccount))
        cp(taskFull.closedBy shouldBe None)
        cp.reportAll()
        1 shouldBe 1
      }

      res.transact(transactor)
    }

  }

}
