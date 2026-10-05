package com.togger.project

import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import com.togger.projectlabel.{ProjectLabel, ProjectLabelCategory, ProjectLabelCategoryRepoImpl, ProjectLabelRepoImpl}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class ProjectLabelAssignmentRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val projectLabelCategoryRepo = new ProjectLabelCategoryRepoImpl
  val projectLabelRepo = new ProjectLabelRepoImpl
  val projectLabelAssignmentRepo = new ProjectLabelAssignmentRepoImpl

  def projectLabel(name: String, categoryId: UUID): ProjectLabel = ProjectLabel(
    id = UUID.randomUUID(),
    name = name,
    description = s"$name description",
    categoryId = categoryId,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "insert, find and delete project labels assigned to a project" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        category <- projectLabelCategoryRepo.insert(
          ProjectLabelCategory(id = UUID.randomUUID(), name = "Language", createdAt = nowAsInstant, updatedAt = nowAsInstant)
        )
        projectLabel1 <- projectLabelRepo.insert(projectLabel("Scala", category.id))
        projectLabel2 <- projectLabelRepo.insert(projectLabel("Kotlin", category.id))
        _ <- projectLabelAssignmentRepo.insert(ProjectLabelAssignment(savedProject.id, projectLabel1.id, nowAsInstant))
        _ <- projectLabelAssignmentRepo.insert(ProjectLabelAssignment(savedProject.id, projectLabel2.id, nowAsInstant))
        foundProjectLabels <- projectLabelAssignmentRepo.findProjectLabelsByProjectId(savedProject.id)
        projectLabelsByProjectId <- projectLabelAssignmentRepo.findProjectLabelsByProjectIds(Seq(savedProject.id))
        _ <- projectLabelAssignmentRepo.deleteAllByProjectId(savedProject.id)
        foundProjectLabelsAfterDelete <- projectLabelAssignmentRepo.findProjectLabelsByProjectId(savedProject.id)
      } yield {
        val cp = new Checkpoint
        cp(foundProjectLabels should contain theSameElementsAs List(projectLabel1, projectLabel2))
        cp(
          projectLabelsByProjectId.getOrElse(savedProject.id, List.empty) should contain theSameElementsAs List(
            projectLabel1,
            projectLabel2
          )
        )
        cp.reportAll()
        foundProjectLabelsAfterDelete shouldBe empty
      }

      res.transact(transactor)
    }

    "return an empty map when finding project labels for no project ids" in { transactor =>
      val res = for {
        projectLabelsByProjectId <- projectLabelAssignmentRepo.findProjectLabelsByProjectIds(Seq.empty)
      } yield projectLabelsByProjectId shouldBe empty

      res.transact(transactor)
    }
  }

}
