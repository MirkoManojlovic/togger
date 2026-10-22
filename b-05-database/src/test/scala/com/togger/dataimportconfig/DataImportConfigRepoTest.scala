package com.togger.dataimportconfig

import cats.implicits._
import com.togger.account.{Account, AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, TgIds, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class DataImportConfigRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val dataImportConfigRepo = new DataImportConfigRepoImpl
  val accountRepo = new AccountRepoImpl

  def newDataImportConfig(
    projectId: UUID,
    accountId: UUID,
    name: String = "My load config",
    importPath: String = "/tmp/dumps"
  ) = DataImportConfig(
    id = UUID.randomUUID(),
    name = name,
    importPath = importPath,
    projectId = projectId,
    accountId = accountId,
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
        savedConfig <- dataImportConfigRepo.insert(newDataImportConfig(savedProject.id, savedAccount.id))
        foundConfig1 <- dataImportConfigRepo.findById(savedConfig.id)
        newUpdatedAt = nowAsInstant
        updatedConfig <- dataImportConfigRepo.update(
          savedConfig.copy(name = "Renamed config", importPath = "/tmp/dumps-new"),
          newUpdatedAt
        )
        foundConfig2 <- dataImportConfigRepo.findById(savedConfig.id)
        _ <- dataImportConfigRepo.deleteById(foundConfig2.id)
        foundConfig3 <- dataImportConfigRepo.findById(savedConfig.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedConfig shouldBe foundConfig1)
        cp(updatedConfig shouldBe foundConfig2)
        cp(updatedConfig.name shouldBe "Renamed config")
        cp(updatedConfig.importPath shouldBe "/tmp/dumps-new")
        cp.reportAll()
        foundConfig3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find Import data configs by project id, scoped to that project" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedOrganization = orgAndProject._1
        savedProject1 = orgAndProject._2
        savedProject2 <- projectRepo.insert(
          project.copy(id = UUID.randomUUID(), organizationId = savedOrganization.id, name = "Second project")
        )
        savedAccount <- createAccount
        config1 <- dataImportConfigRepo.insert(
          newDataImportConfig(savedProject1.id, savedAccount.id, "p1-a", "/dumps/p1-a")
        )
        config2 <- dataImportConfigRepo.insert(
          newDataImportConfig(savedProject1.id, savedAccount.id, "p1-b", "/dumps/p1-b")
        )
        _ <- dataImportConfigRepo.insert(newDataImportConfig(savedProject2.id, savedAccount.id, "p2-a", "/dumps/p2-a"))
        found <- dataImportConfigRepo.findPaginatedByProjectId(savedProject1.id, DbPageRequest.default).map(_.items)
      } yield {
        found.toSet shouldBe Set(config1, config2)
      }

      res.transact(transactor)
    }

  }

}
