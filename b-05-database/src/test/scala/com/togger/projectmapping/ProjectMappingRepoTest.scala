package com.togger.projectmapping

import cats.implicits._
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import com.togger.project.Project
import doobie.implicits._

import java.util.UUID

class ProjectMappingRepoTest extends BusinessTgDbSpec with TgProject with TimeHelpers {

  val projectMappingRepo = new ProjectMappingRepoImpl

  def newProjectMapping(projectIdDump: UUID, projectIdDb: UUID) = ProjectMapping(
    id = UUID.randomUUID(),
    projectIdDump = projectIdDump,
    projectIdDb = projectIdDb,
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
        dumpProject = orgAndProject._2
        dbProject <- createSecondProject(savedOrganization.id)
        savedMapping <- projectMappingRepo.insert(newProjectMapping(dumpProject.id, dbProject.id))
        foundMapping1 <- projectMappingRepo.findById(savedMapping.id)
        anotherDumpProject <- createSecondProject(savedOrganization.id)
        newUpdatedAt = nowAsInstant
        updatedMapping <- projectMappingRepo.update(
          savedMapping.copy(projectIdDump = anotherDumpProject.id),
          newUpdatedAt
        )
        foundMapping2 <- projectMappingRepo.findById(savedMapping.id)
        _ <- projectMappingRepo.deleteById(foundMapping2.id)
        foundMapping3 <- projectMappingRepo.findById(savedMapping.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedMapping shouldBe foundMapping1)
        cp(updatedMapping shouldBe foundMapping2)
        cp(updatedMapping.projectIdDump shouldBe anotherDumpProject.id)
        cp.reportAll()
        foundMapping3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find project mappings by db project id" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedOrganization = orgAndProject._1
        dumpProject1 = orgAndProject._2
        dumpProject2 <- createSecondProject(savedOrganization.id)
        dbProject1 <- createSecondProject(savedOrganization.id)
        dbProject2 <- createSecondProject(savedOrganization.id)
        mapping1 <- projectMappingRepo.insert(newProjectMapping(dumpProject1.id, dbProject1.id))
        _ <- projectMappingRepo.insert(newProjectMapping(dumpProject2.id, dbProject2.id))
        found <- projectMappingRepo.findByProjectIdDb(dbProject1.id)
      } yield {
        found shouldBe List(mapping1)
      }

      res.transact(transactor)
    }

    "fail to insert a second mapping for a project id that is already in the db" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedOrganization = orgAndProject._1
        dumpProject1 = orgAndProject._2
        dumpProject2 <- createSecondProject(savedOrganization.id)
        dbProject <- createSecondProject(savedOrganization.id)
        _ <- projectMappingRepo.insert(newProjectMapping(dumpProject1.id, dbProject.id))
        secondInsert <- projectMappingRepo.insert(newProjectMapping(dumpProject2.id, dbProject.id)).attempt
      } yield {
        secondInsert.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

}
