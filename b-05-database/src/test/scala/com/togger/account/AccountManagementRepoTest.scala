package com.togger.account

import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PageRequest, TimeHelpers}
import com.togger.organization.{TgOrganization, TgProject}
import com.togger.project.{ProjectAccount, ProjectAccountRepoImpl}
import doobie.implicits._

import java.util.UUID

class AccountManagementRepoTest
    extends BusinessTgDbSpec
    with TgAccount
    with TgOrganization
    with TgProject
    with TimeHelpers {

  private val repo = new AccountManagementRepoImpl
  private val accountRepo = new AccountRepoImpl
  private val projectAccountRepo = new ProjectAccountRepoImpl

  testNameFromClassName should {

    "list the accounts with their account type and projects" in { transactor =>
      (for {
        member <- createAccount
        other <- accountRepo.insert(member.copy(id = UUID.randomUUID(), email = "other@email.com"))
        orgAndProject <- createOrgAndProject
        project = orgAndProject._2
        _ <- projectAccountRepo.insert(ProjectAccount(project.id, member.id, nowAsInstant))
        page <- repo.findPaginated(DbPageRequest.fromPageRequest(PageRequest(1, 10)).toOption.get)
      } yield {
        val byId = page.items.map(a => a.id -> a).toMap
        val cp = new Checkpoint
        cp(page.totalNumberOfItems shouldBe 2)
        cp(byId(member.id).projects.map(_.projectId) shouldBe List(project.id))
        cp(byId(member.id).accountTypeId shouldBe member.accountTypeId)
        cp(byId(other.id).projects shouldBe Nil)
        cp.reportAll()
      }).transact(transactor)
    }

    "delete an account together with its memberships" in { transactor =>
      (for {
        member <- createAccount
        orgAndProject <- createOrgAndProject
        _ <- projectAccountRepo.insert(ProjectAccount(orgAndProject._2.id, member.id, nowAsInstant))
        deleted <- repo.deleteWithMemberships(member.id)
        found <- repo.findOne(member.id)
      } yield {
        val cp = new Checkpoint
        cp(deleted shouldBe 1)
        cp(found shouldBe None)
        cp.reportAll()
      }).transact(transactor)
    }
  }
}
