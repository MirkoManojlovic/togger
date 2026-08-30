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
        mapping = newProjectMapping(dumpProject.id, dbProject.id)
        savedMapping <- projectMappingRepo.insert(mapping)
        foundByDb <- projectMappingRepo.findByProjectIdDb(dbProject.id)
        foundByDump <- projectMappingRepo.findByProjectIdDump(mapping.projectIdDump)
        _ <- projectMappingRepo.deleteByIds(dbProject.id, mapping.projectIdDump)
        foundAfterDelete <- projectMappingRepo.findByProjectIdDb(dbProject.id)
      } yield {
        val cp = new Checkpoint
        cp(savedMapping.projectIdDb shouldBe dbProject.id)
        cp(savedMapping.projectIdDump shouldBe mapping.projectIdDump)
        cp(foundByDb shouldBe List(savedMapping))
        cp(foundByDump shouldBe List(savedMapping))
        cp.reportAll()
        foundAfterDelete shouldBe empty
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

    "allow mapping multiple dump projects to the same db project" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedOrganization = orgAndProject._1
        dumpProject1 = orgAndProject._2
        dumpProject2 <- createSecondProject(savedOrganization.id)
        dbProject <- createSecondProject(savedOrganization.id)
        mapping1 <- projectMappingRepo.insert(newProjectMapping(dumpProject1.id, dbProject.id))
        mapping2 <- projectMappingRepo.insert(newProjectMapping(dumpProject2.id, dbProject.id))
        found <- projectMappingRepo.findByProjectIdDb(dbProject.id)
      } yield {
        found.toSet shouldBe Set(mapping1, mapping2)
      }

      res.transact(transactor)
    }

    "fail to insert a duplicate composite-key mapping" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedOrganization = orgAndProject._1
        dumpProject = orgAndProject._2
        dbProject <- createSecondProject(savedOrganization.id)
        _ <- projectMappingRepo.insert(newProjectMapping(dumpProject.id, dbProject.id))
        secondInsert <- projectMappingRepo.insert(newProjectMapping(dumpProject.id, dbProject.id)).attempt
      } yield {
        secondInsert.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

}
