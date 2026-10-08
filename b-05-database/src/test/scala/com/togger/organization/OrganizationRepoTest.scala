package com.togger.organization

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PageRequest, TimeHelpers}
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
    icon = "🚀",
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
        _ <- orgAccountsForSaving.map(organizationAccountRepo.insert).sequence
        foundOrganizations <- organizationRepo.findPaginatedByAccountId(savedAccount.id, DbPageRequest.default)
      } yield {
        foundOrganizations.items shouldBe chosenOrg.sortBy(_.name)
        foundOrganizations.totalNumberOfItems shouldBe 3L
      }

      res.transact(transactor)
    }

    // eleven organizations "Organization 00".."Organization 10", inserted in reverse so the order is by name
    def seedOrganizations: ConnectionIO[List[Organization]] =
      (0 to 10).toList.reverse
        .traverse(index => organizationRepo.insert(organization.copy(name = f"Organization $index%02d")))
        .map(_.sortBy(_.name))

    def pageOf(pageNumber: Int, size: Int) =
      DbPageRequest.fromPageRequest(PageRequest(pageNumber, size)).toOption.get

    "split all 11 organizations over 3 pages of 5, by name, without a duplicate" in { transactor =>
      val res = for {
        seeded <- seedOrganizations
        pages <- (1 to 3).toList.traverse(number => organizationRepo.findPaginated(pageOf(number, 5)))
        past <- organizationRepo.findPaginated(pageOf(4, 5))
      } yield {
        val cp = new Checkpoint
        cp(pages.map(_.items.size) shouldBe List(5, 5, 1))
        cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(11L))
        cp(pages.map(_.totalNumberOfPages).distinct shouldBe List(3L))
        cp(pages.flatMap(_.items) shouldBe seeded)
        cp(past.items shouldBe empty)
        cp(past.totalNumberOfItems shouldBe 11L)
        cp.reportAll()
        succeed
      }

      res.transact(transactor)
    }

    "split the organizations of an account over pages, leaving out the ones it is not a member of" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        seeded <- seedOrganizations
        memberOf = seeded.filter(org => org.name.takeRight(2).toInt % 2 == 0)
        _ <- memberOf.traverse(org =>
          organizationAccountRepo.insert(OrganizationAccount(org.id, savedAccount.id, nowAsInstant))
        )
        pages <- (1 to 2).toList.traverse(number =>
          organizationRepo.findPaginatedByAccountId(savedAccount.id, pageOf(number, 4))
        )
        otherAccount <- organizationRepo.findPaginatedByAccountId(UUID.randomUUID(), pageOf(1, 4))
      } yield {
        val cp = new Checkpoint
        cp(pages.map(_.items.size) shouldBe List(4, 2))
        cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(6L))
        cp(pages.flatMap(_.items) shouldBe memberOf)
        cp(otherAccount.items shouldBe empty)
        cp(otherAccount.totalNumberOfItems shouldBe 0L)
        cp.reportAll()
        succeed
      }

      res.transact(transactor)
    }
  }

}
