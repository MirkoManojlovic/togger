package com.togger.project

import cats.implicits._
import com.togger.account.{Account, AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PageRequest, TgIds, TimeHelpers}
import com.togger.organization.{TgOrganization, TgProject}
import doobie.implicits._
import doobie.postgres.implicits._

class ProjectAccountRepoTest
    extends BusinessTgDbSpec
    with TgAccount
    with TgOrganization
    with TgProject
    with TimeHelpers {

  val projectAccountRepo = new ProjectAccountRepoImpl
  val accountRepo = new AccountRepoImpl

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        savedProjectAccount <- projectAccountRepo.insert(
          ProjectAccount(
            projectId = savedProject.id,
            accountId = savedAccount.id,
            createdAt = nowAsInstant
          )
        )
        foundProjectAccount <- projectAccountRepo.findById(
          projectId = savedProjectAccount.projectId,
          accountId = savedProjectAccount.accountId
        )
        _ <- projectAccountRepo.deleteById(
          projectId = savedProjectAccount.projectId,
          accountId = savedProjectAccount.accountId
        )
        foundProjectAccount2 <- projectAccountRepo
          .findById(
            projectId = savedProjectAccount.projectId,
            accountId = savedProjectAccount.accountId
          )
          .attempt
      } yield {
        val cp = new Checkpoint
        cp(savedProjectAccount shouldBe foundProjectAccount)
        cp.reportAll()
        foundProjectAccount2.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

  "tell members from accounts that are not in the project" in { transactor =>
    (for {
      member <- createAccount
      other <- accountRepo.insert(member.copy(id = java.util.UUID.randomUUID(), email = "other@email.com"))
      orgAndProject <- createOrgAndProject
      project = orgAndProject._2
      _ <- projectAccountRepo.insert(ProjectAccount(project.id, member.id, nowAsInstant))
      memberResult <- projectAccountRepo.isMember(project.id, member.id)
      otherResult <- projectAccountRepo.isMember(project.id, other.id)
      unknownProject <- projectAccountRepo.isMember(java.util.UUID.randomUUID(), member.id)
    } yield {
      memberResult shouldBe true
      otherResult shouldBe false
      unknownProject shouldBe false
    }).transact(transactor)
  }

  "find all members of the project" in { transactor =>
    val accounts = Seq(
      createAccount("John", "Smith", "shark@email.com"),
      createAccount("Jack", "Curry", "bird@email.com"),
      createAccount("Steve", "Murry", "bird1@email.com"),
      createAccount("George", "Washington", "dog@email.com")
    )

    val res = for {
      accountTypes <- createAccountTypes
      savedAccounts <- accounts
        .map(account => accountRepo.insert(account.copy(accountTypeId = accountTypes.head.id)))
        .sequence
      savedOrgAndProject <- createOrgAndProject
      (_, savedProject) = savedOrgAndProject
      projectAccountsForSaving = savedAccounts.map(account =>
        ProjectAccount(
          projectId = savedProject.id,
          accountId = account.id,
          createdAt = nowAsInstant
        )
      )
      _ <- projectAccountsForSaving.map(projectAccountRepo.insert).sequence
      foundAccounts <- projectAccountRepo.findPaginatedMembers(savedProject.id, DbPageRequest.default)
    } yield {
      // by last name: Curry, Murry, Smith, Washington
      foundAccounts.items shouldBe savedAccounts.sortBy(_.lastName)
      foundAccounts.totalNumberOfItems shouldBe 4L
    }

    res.transact(transactor)
  }

  "split 11 members over 3 pages of 5, by last name, first name, without a duplicate" in { transactor =>
    val res = for {
      accountTypes <- createAccountTypes
      // two members share a last name, so first name decides; inserted in reverse so the order is not insertion order
      savedAccounts <- (0 to 10).toList.reverse
        .map(index =>
          createAccount(f"First $index%02d", if (index < 2) "Same" else f"Last $index%02d", s"member$index@email.com")
        )
        .map(account => accountRepo.insert(account.copy(accountTypeId = accountTypes.head.id)))
        .sequence
      savedOrgAndProject <- createOrgAndProject
      (_, savedProject) = savedOrgAndProject
      otherProject <- projectRepo.insert(project.copy(organizationId = savedProject.organizationId, name = "Other"))
      _ <- savedAccounts.traverse(account =>
        projectAccountRepo.insert(ProjectAccount(savedProject.id, account.id, nowAsInstant))
      )
      _ <- projectAccountRepo.insert(ProjectAccount(otherProject.id, savedAccounts.head.id, nowAsInstant))
      pages <- (1 to 3).toList.traverse(number =>
        projectAccountRepo.findPaginatedMembers(
          savedProject.id,
          DbPageRequest.fromPageRequest(PageRequest(number, 5)).toOption.get
        )
      )
      past <- projectAccountRepo.findPaginatedMembers(
        savedProject.id,
        DbPageRequest.fromPageRequest(PageRequest(4, 5)).toOption.get
      )
    } yield {
      val expected = savedAccounts.sortBy(account => (account.lastName, account.firstName))
      val cp = new Checkpoint
      cp(pages.map(_.items.size) shouldBe List(5, 5, 1))
      cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(11L))
      cp(pages.map(_.totalNumberOfPages).distinct shouldBe List(3L))
      cp(pages.flatMap(_.items) shouldBe expected)
      cp(past.items shouldBe empty)
      cp(past.totalNumberOfItems shouldBe 11L)
      cp.reportAll()
      succeed
    }

    res.transact(transactor)
  }

  "find all members for multiple project ids in one batch" in { transactor =>
    val res = for {
      accountTypes <- createAccountTypes
      memberOfProject1 <- accountRepo.insert(
        createAccount("John", "Smith", "shark@email.com").copy(accountTypeId = accountTypes.head.id)
      )
      memberOfProject2 <- accountRepo.insert(
        createAccount("Jack", "Curry", "bird@email.com").copy(accountTypeId = accountTypes.head.id)
      )
      savedOrganization <- createOrganization
      project1 <- projectRepo.insert(project.copy(organizationId = savedOrganization.id, name = "Project 1"))
      project2 <- projectRepo.insert(project.copy(organizationId = savedOrganization.id, name = "Project 2"))
      projectWithNoMembers <- projectRepo.insert(
        project.copy(organizationId = savedOrganization.id, name = "Project 3")
      )
      _ <- projectAccountRepo.insert(ProjectAccount(project1.id, memberOfProject1.id, nowAsInstant))
      _ <- projectAccountRepo.insert(ProjectAccount(project2.id, memberOfProject2.id, nowAsInstant))
      foundMembersByProjectId <- projectAccountRepo.findAllMembersByProjectIds(
        Seq(project1.id, project2.id, projectWithNoMembers.id)
      )
    } yield {
      val cp = new Checkpoint
      cp(foundMembersByProjectId.getOrElse(project1.id, Seq.empty) shouldBe Seq(memberOfProject1))
      cp(foundMembersByProjectId.getOrElse(project2.id, Seq.empty) shouldBe Seq(memberOfProject2))
      cp(foundMembersByProjectId.get(projectWithNoMembers.id) shouldBe None)
      cp.reportAll()
    }

    res.transact(transactor)
  }

  "return an empty map when finding members for no project ids" in { transactor =>
    val res = for {
      foundMembersByProjectId <- projectAccountRepo.findAllMembersByProjectIds(Seq.empty)
    } yield foundMembersByProjectId shouldBe empty

    res.transact(transactor)
  }

  def createAccount(firstName: String, lastName: String, email: String) =
    Account(
      id = TgIds.UNASSIGNED_UUID,
      firstName = firstName,
      lastName = lastName,
      email = email,
      password = passwordHashed,
      accountTypeId = TgIds.UNASSIGNED_UUID,
      verified = true,
      enabled = true,
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )
}
