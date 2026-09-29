package com.togger.task

import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TgIds, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TaskLabelAssignmentRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val taskRepo = new TaskRepoImpl
  val taskLabelRepo = new TaskLabelRepoImpl
  val taskLabelAssignmentRepo = new TaskLabelAssignmentRepoImpl

  val task = Task(
    id = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    createdBy = TgIds.UNASSIGNED_UUID,
    createdAt = nowAsInstant
  )

  def label(name: String, projectId: UUID): TaskLabel = TaskLabel(
    id = UUID.randomUUID(),
    projectId = projectId,
    name = name,
    description = s"$name description",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "insert, find and delete labels assigned to a task" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        savedTask <- taskRepo.insert(task.copy(projectId = savedProject.id, createdBy = savedAccount.id))
        label1 <- taskLabelRepo.insert(label("feature", savedProject.id))
        label2 <- taskLabelRepo.insert(label("bug", savedProject.id))
        _ <- taskLabelAssignmentRepo.insert(TaskLabelAssignment(savedTask.id, label1.id, nowAsInstant))
        _ <- taskLabelAssignmentRepo.insert(TaskLabelAssignment(savedTask.id, label2.id, nowAsInstant))
        foundLabels <- taskLabelAssignmentRepo.findLabelsByTaskId(savedTask.id)
        labelsByTaskId <- taskLabelAssignmentRepo.findLabelsByTaskIds(Seq(savedTask.id))
        _ <- taskLabelAssignmentRepo.deleteAllByTaskId(savedTask.id)
        foundLabelsAfterDelete <- taskLabelAssignmentRepo.findLabelsByTaskId(savedTask.id)
      } yield {
        val cp = new Checkpoint
        cp(foundLabels should contain theSameElementsAs List(label1, label2))
        cp(labelsByTaskId.getOrElse(savedTask.id, List.empty) should contain theSameElementsAs List(label1, label2))
        cp.reportAll()
        foundLabelsAfterDelete shouldBe empty
      }

      res.transact(transactor)
    }

    "return an empty map when finding labels for no task ids" in { transactor =>
      val res = for {
        labelsByTaskId <- taskLabelAssignmentRepo.findLabelsByTaskIds(Seq.empty)
      } yield labelsByTaskId shouldBe empty

      res.transact(transactor)
    }
  }

}
