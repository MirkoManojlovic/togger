package com.togger.milestonesteptemplate

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class MilestoneStepTemplateRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  private val templateRepo = new MilestoneStepTemplateRepoImpl
  private val versionRepo = new MilestoneStepTemplateVersionRepoImpl

  private def newTemplate(projectId: UUID, createdBy: UUID) =
    MilestoneStepTemplate(UUID.randomUUID(), projectId, createdBy, nowAsInstant)

  private def newVersion(templateId: UUID, name: String, description: String) =
    MilestoneStepTemplateVersion(UUID.randomUUID(), templateId, name, description, nowAsInstant)

  private def insertWithVersion(template: MilestoneStepTemplate, name: String, description: String) =
    for {
      saved <- templateRepo.insert(template)
      version <- versionRepo.insert(newVersion(saved.id, name, description))
    } yield saved -> version

  testNameFromClassName should {

    "insert, find and delete a template together with its versions" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        saved <- templateRepo.insert(newTemplate(projectId, account.id))
        found <- templateRepo.findById(saved.id)
        _ <- versionRepo.insert(newVersion(saved.id, "Design", "Plan it"))
        deleted <- templateRepo.deleteById(saved.id)
        afterDelete <- templateRepo.findById(saved.id).attempt
      } yield {
        found shouldBe saved
        deleted shouldBe 1
        afterDelete.isLeft shouldBe true
      }).transact(transactor)
    }

    "find the full template with its latest version" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        saved <- templateRepo.insert(newTemplate(projectId, account.id))
        _ <- versionRepo.insert(newVersion(saved.id, "Design", "v1"))
        v2 <- versionRepo.insert(newVersion(saved.id, "Design renamed", "v2"))
        full <- templateRepo.findFullById(saved.id)
        history <- versionRepo.findPaginatedByTemplateId(saved.id, DbPageRequest.default)
      } yield {
        full shouldBe MilestoneStepTemplateFull(saved, v2)
        history.items.map(_.description) shouldBe List("v2", "v1")
      }).transact(transactor)
    }

    "page the templates of a project by name and ignore other projects" in { transactor =>
      (for {
        account <- createAccount
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        otherProject <- projectRepo.insert(
          project.copy(id = UUID.randomUUID(), organizationId = orgAndProject._1.id, name = "Other project")
        )
        otherProjectId = otherProject.id
        _ <- insertWithVersion(newTemplate(projectId, account.id), "Staging", "s")
        _ <- insertWithVersion(newTemplate(projectId, account.id), "Design", "d")
        _ <- insertWithVersion(newTemplate(otherProjectId, account.id), "Other", "o")
        page <- templateRepo.findPaginatedFullByProjectId(projectId, DbPageRequest.default)
      } yield {
        page.items.map(_.latestVersion.name) shouldBe List("Design", "Staging")
        page.totalNumberOfItems shouldBe 2L
      }).transact(transactor)
    }
  }
}
