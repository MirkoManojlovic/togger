package com.togger.projectmapping

import cats.implicits._
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import com.togger.project.Project
import doobie.implicits._

import java.util.UUID

class ProjectMappingRepoTest extends BusinessTgDbSpec with TgProject with TimeHelpers {

  val projectMappingRepo = new ProjectMappingRepoImpl

  def newProjectMapping(projectIdOrigin: UUID, projectIdLoaded: UUID) = ProjectMapping(
    id = UUID.randomUUID(),
    projectIdOrigin = projectIdOrigin,
    projectIdLoaded = projectIdLoaded,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createSecondProject(organizationId: UUID): doobie.ConnectionIO[Project] =
    projectRepo.insert(
      project.copy(id = UUID.randomUUID(), organizationId = organizationId, name = "Second project")
    )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedOrganization = orgAndProject._1
        originProject = orgAndProject._2
        loadedProject <- createSecondProject(savedOrganization.id)
        savedMapping <- projectMappingRepo.insert(newProjectMapping(originProject.id, loadedProject.id))
        foundMapping1 <- projectMappingRepo.findById(savedMapping.id)
        anotherOriginProject <- createSecondProject(savedOrganization.id)
        newUpdatedAt = nowAsInstant
        updatedMapping <- projectMappingRepo.update(
          savedMapping.copy(projectIdOrigin = anotherOriginProject.id),
          newUpdatedAt
        )
        foundMapping2 <- projectMappingRepo.findById(savedMapping.id)
        _ <- projectMappingRepo.deleteById(foundMapping2.id)
        foundMapping3 <- projectMappingRepo.findById(savedMapping.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedMapping shouldBe foundMapping1)
        cp(updatedMapping shouldBe foundMapping2)
        cp(updatedMapping.projectIdOrigin shouldBe anotherOriginProject.id)
        cp.reportAll()
        foundMapping3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find project mappings by loaded project id" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedOrganization = orgAndProject._1
        originProject1 = orgAndProject._2
        originProject2 <- createSecondProject(savedOrganization.id)
        loadedProject1 <- createSecondProject(savedOrganization.id)
        loadedProject2 <- createSecondProject(savedOrganization.id)
        mapping1 <- projectMappingRepo.insert(newProjectMapping(originProject1.id, loadedProject1.id))
        _ <- projectMappingRepo.insert(newProjectMapping(originProject2.id, loadedProject2.id))
        found <- projectMappingRepo.findByProjectIdLoaded(loadedProject1.id)
      } yield {
        found shouldBe List(mapping1)
      }

      res.transact(transactor)
    }

    "fail to insert a second mapping for a project id that is already loaded" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedOrganization = orgAndProject._1
        originProject1 = orgAndProject._2
        originProject2 <- createSecondProject(savedOrganization.id)
        loadedProject <- createSecondProject(savedOrganization.id)
        _ <- projectMappingRepo.insert(newProjectMapping(originProject1.id, loadedProject.id))
        secondInsert <- projectMappingRepo.insert(newProjectMapping(originProject2.id, loadedProject.id)).attempt
      } yield {
        secondInsert.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

}
