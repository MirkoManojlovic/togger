package com.togger.project

import cats.implicits.catsSyntaxApplicativeError
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgOrganization
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class ProjectRepoTest extends BusinessTgDbSpec with TgAccount with TgOrganization with TimeHelpers {

  val projectRepo = new ProjectRepoImpl
  val projectAccountRepo = new ProjectAccountRepoImpl

  val project = Project(
    id = UUID.randomUUID(),
    organizationId = UUID.randomUUID(),
    name = "Project name",
    description = "Project description",
    icon = "🚀",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedOrganization <- createOrganization
        savedProject <- projectRepo.insert(project.copy(organizationId = savedOrganization.id))
        foundProject1 <- projectRepo.findById(savedProject.id)
        newUpdatedAt = nowAsInstant
        updatedProject <- projectRepo.update(savedProject.copy(name = "new project name"), newUpdatedAt)
        foundProject2 <- projectRepo.findById(savedProject.id)
        _ <- projectRepo.deleteById(foundProject2.id)
        foundProject3 <- projectRepo.findById(savedProject.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedProject shouldBe foundProject1)
        cp(updatedProject shouldBe foundProject2)
        cp.reportAll()
        foundProject3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find by org id" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        savedOrganization <- createOrganization
        savedProject1 <- projectRepo.insert(project.copy(organizationId = savedOrganization.id))
        _ <- projectRepo.insert(
          project.copy(organizationId = savedOrganization.id, name = "Project name 2")
        )
        savedProjectAccount <- projectAccountRepo.insert(
          ProjectAccount(
            projectId = savedProject1.id,
            accountId = savedAccount.id,
            createdAt = nowAsInstant
          )
        )
        foundProjects <- projectRepo.findByOrganizationIdAndAccountId(
          organizationId = savedOrganization.id,
          accountId = savedAccount.id
        )
      } yield {
        foundProjects shouldBe Seq(savedProject1 -> savedProjectAccount)
      }

      res.transact(transactor)
    }
  }

}
