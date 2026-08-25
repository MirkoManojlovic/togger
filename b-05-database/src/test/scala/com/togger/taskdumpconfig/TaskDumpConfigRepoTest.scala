package com.togger.taskdumpconfig

import cats.implicits._
import com.togger.account.{Account, AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, TgIds, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TaskDumpConfigRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val taskDumpConfigRepo = new TaskDumpConfigRepoImpl
  val accountRepo = new AccountRepoImpl

  def newTaskDumpConfig(
    projectId: UUID,
    accountId: UUID,
    name: String = "My dump config",
    dumpPath: String = "/tmp/dumps"
  ) = TaskDumpConfig(
    id = UUID.randomUUID(),
    projectId = projectId,
    accountId = accountId,
    name = name,
    dumpPath = dumpPath,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createAccountWith(email: String, accountTypeId: UUID) = Account(
    id = TgIds.UNASSIGNED_UUID,
    firstName = "Jane",
    lastName = "Doe",
    email = email,
    password = passwordHashed,
    accountTypeId = accountTypeId,
    verified = true,
    enabled = true,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        savedAccount <- createAccount
        savedConfig <- taskDumpConfigRepo.insert(newTaskDumpConfig(savedProject.id, savedAccount.id))
        foundConfig1 <- taskDumpConfigRepo.findById(savedConfig.id)
        newUpdatedAt = nowAsInstant
        updatedConfig <- taskDumpConfigRepo.update(
          savedConfig.copy(name = "Renamed config", dumpPath = "/tmp/dumps-new"),
          newUpdatedAt
        )
        foundConfig2 <- taskDumpConfigRepo.findById(savedConfig.id)
        _ <- taskDumpConfigRepo.deleteById(foundConfig2.id)
        foundConfig3 <- taskDumpConfigRepo.findById(savedConfig.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedConfig shouldBe foundConfig1)
        cp(updatedConfig shouldBe foundConfig2)
        cp(updatedConfig.name shouldBe "Renamed config")
        cp(updatedConfig.dumpPath shouldBe "/tmp/dumps-new")
        cp.reportAll()
        foundConfig3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find task dump configs by project id, scoped to that project" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedOrganization = orgAndProject._1
        savedProject1 = orgAndProject._2
        savedProject2 <- projectRepo.insert(
          project.copy(id = UUID.randomUUID(), organizationId = savedOrganization.id, name = "Second project")
        )
        savedAccount <- createAccount
        config1 <- taskDumpConfigRepo.insert(newTaskDumpConfig(savedProject1.id, savedAccount.id, "p1-a", "/dumps/p1-a"))
        config2 <- taskDumpConfigRepo.insert(newTaskDumpConfig(savedProject1.id, savedAccount.id, "p1-b", "/dumps/p1-b"))
        _ <- taskDumpConfigRepo.insert(newTaskDumpConfig(savedProject2.id, savedAccount.id, "p2-a", "/dumps/p2-a"))
        found <- taskDumpConfigRepo.findByProjectId(savedProject1.id)
      } yield {
        found.toSet shouldBe Set(config1, config2)
      }

      res.transact(transactor)
    }

    "find task dump configs by account id, scoped to that account" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        savedAccount1 <- createAccount
        accountTypes <- createAccountTypes
        savedAccount2 <- accountRepo.insert(createAccountWith("second-account@email.com", accountTypes.head.id))
        config1 <- taskDumpConfigRepo.insert(newTaskDumpConfig(savedProject.id, savedAccount1.id, "a1", "/dumps/a1"))
        _ <- taskDumpConfigRepo.insert(newTaskDumpConfig(savedProject.id, savedAccount2.id, "a2", "/dumps/a2"))
        found <- taskDumpConfigRepo.findByAccountId(savedAccount1.id)
      } yield {
        found shouldBe List(config1)
      }

      res.transact(transactor)
    }
  }

}
