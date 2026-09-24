package com.togger.organization

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.project.{Project, ProjectRepoImpl}
import com.togger.task.TaskStatus
import com.togger.taskStatus.TaskStatusRepoImpl
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

trait TgTaskStatus { self: TimeHelpers =>

  val taskStatusRepo = new TaskStatusRepoImpl

  val taskStatus = TaskStatus(
    id = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    name = "In progress",
    description = "Task in progress",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createTaskStatus(projectId: UUID): ConnectionIO[TaskStatus] =
    for {
      savedTaskStatus <- taskStatusRepo.insert(taskStatus.copy(projectId = projectId))
    } yield savedTaskStatus
}

trait TgProject extends TgOrganization { self: TimeHelpers =>

  lazy val projectRepo = new ProjectRepoImpl

  lazy val project = Project(
    id = UUID.randomUUID(),
    organizationId = UUID.randomUUID(),
    name = "Project name",
    description = "Project description",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createOrgAndProject: ConnectionIO[(Organization, Project)] = {
    for {
      savedOrganization <- organizationRepo.insert(organization)
      savedProject <- projectRepo.insert(project.copy(organizationId = savedOrganization.id))
    } yield savedOrganization -> savedProject
  }

}

trait TgOrganization { self: TimeHelpers =>

  lazy val organizationRepo = new OrganizationRepoImpl

  val organization = Organization(
    id = UUID.randomUUID(),
    name = "Organization name",
    description = "Organization description",
    icon = "🚀",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createOrganization: ConnectionIO[Organization] = {
    for {
      savedOrganization <- organizationRepo.insert(organization)
    } yield savedOrganization
  }
}

class OrganizationRepoTest extends BusinessTgDbSpec with TgAccount with TimeHelpers {

  val organizationRepo = new OrganizationRepoImpl
  val organizationAccountRepo = new OrganizationAccountRepoImpl

  val organization = Organization(
    id = UUID.randomUUID(),
    name = "Organization name",
    description = "Organization description",
    icon = "🚀",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedOrganization <- organizationRepo.insert(organization)
        foundOrganization1 <- organizationRepo.findById(savedOrganization.id)
        newUpdatedAt = nowAsInstant
        updatedOrganization <- organizationRepo.update(
          savedOrganization.copy(name = "new organization name"),
          newUpdatedAt
        )
        foundOrganization2 <- organizationRepo.findById(savedOrganization.id)
        _ <- organizationRepo.deleteById(foundOrganization2.id)
        foundOrganization3 <- organizationRepo.findById(savedOrganization.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedOrganization shouldBe foundOrganization1)
        cp(updatedOrganization shouldBe foundOrganization2)
        cp.reportAll()
        foundOrganization3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find all" in { transactor =>
      val organizations = (0 to 10).map { index =>
        organization.copy(name = organization.name + s" $index")
      }

      val res = for {
        savedOrganizations <- organizations.map(organization => organizationRepo.insert(organization)).toList.sequence
        foundOrganizations <- savedOrganizations.map(org => organizationRepo.findById(org.id)).toList.sequence
      } yield {
        savedOrganizations shouldBe foundOrganizations
      }

      res.transact(transactor)

    }

    "find all by accountId" in { transactor =>
      val organizations = (0 to 10).map { index =>
        organization.copy(name = organization.name + s" $index")
      }

      val res = for {
        savedAccount <- createAccount
        savedOrganizations <- organizations.map(organization => organizationRepo.insert(organization)).toList.sequence
        chosenOrg = savedOrganizations.take(3)
        orgAccountsForSaving = chosenOrg.map(organization =>
          OrganizationAccount(
            organizationId = organization.id,
            accountId = savedAccount.id,
            createdAt = nowAsInstant
          )
        )
        savedOrganizationAccounts <- orgAccountsForSaving.map(organizationAccountRepo.insert).sequence
        foundOrganizationAccounts <- organizationRepo.findAllOrgByAccountId(savedAccount.id)
      } yield {
        chosenOrg.zip(savedOrganizationAccounts) shouldBe foundOrganizationAccounts
      }

      res.transact(transactor)
    }
  }

}
