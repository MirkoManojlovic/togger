package com.togger.project

import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import com.togger.techstack.{TechStack, TechStackCategory, TechStackCategoryRepoImpl, TechStackRepoImpl}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class ProjectTechStackRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val techStackCategoryRepo = new TechStackCategoryRepoImpl
  val techStackRepo = new TechStackRepoImpl
  val projectTechStackRepo = new ProjectTechStackRepoImpl

  def techStack(name: String, categoryId: UUID): TechStack = TechStack(
    id = UUID.randomUUID(),
    name = name,
    description = s"$name description",
    categoryId = categoryId,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "insert, find and delete tech stacks assigned to a project" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        category <- techStackCategoryRepo.insert(
          TechStackCategory(id = UUID.randomUUID(), name = "Language", createdAt = nowAsInstant, updatedAt = nowAsInstant)
        )
        techStack1 <- techStackRepo.insert(techStack("Scala", category.id))
        techStack2 <- techStackRepo.insert(techStack("Kotlin", category.id))
        _ <- projectTechStackRepo.insert(ProjectTechStack(savedProject.id, techStack1.id, nowAsInstant))
        _ <- projectTechStackRepo.insert(ProjectTechStack(savedProject.id, techStack2.id, nowAsInstant))
        foundTechStacks <- projectTechStackRepo.findTechStacksByProjectId(savedProject.id)
        techStacksByProjectId <- projectTechStackRepo.findTechStacksByProjectIds(Seq(savedProject.id))
        _ <- projectTechStackRepo.deleteAllByProjectId(savedProject.id)
        foundTechStacksAfterDelete <- projectTechStackRepo.findTechStacksByProjectId(savedProject.id)
      } yield {
        val cp = new Checkpoint
        cp(foundTechStacks should contain theSameElementsAs List(techStack1, techStack2))
        cp(
          techStacksByProjectId.getOrElse(savedProject.id, List.empty) should contain theSameElementsAs List(
            techStack1,
            techStack2
          )
        )
        cp.reportAll()
        foundTechStacksAfterDelete shouldBe empty
      }

      res.transact(transactor)
    }

    "return an empty map when finding tech stacks for no project ids" in { transactor =>
      val res = for {
        techStacksByProjectId <- projectTechStackRepo.findTechStacksByProjectIds(Seq.empty)
      } yield techStacksByProjectId shouldBe empty

      res.transact(transactor)
    }
  }

}
