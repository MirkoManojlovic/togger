package com.togger.milestonetemplate

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.BusinessTgDbSpec
import com.togger.common.DbPageRequest
import com.togger.common.TimeHelpers
import com.togger.milestonesteptemplate.MilestoneStepTemplate
import com.togger.milestonesteptemplate.MilestoneStepTemplateRepoImpl
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersion
import com.togger.milestonesteptemplate.MilestoneStepTemplateVersionRepoImpl
import com.togger.organization.TgProject
import doobie.ConnectionIO
import doobie.implicits._

import java.util.UUID

class MilestoneTemplateRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  private val stepTemplateRepo = new MilestoneStepTemplateRepoImpl
  private val stepTemplateVersionRepo = new MilestoneStepTemplateVersionRepoImpl
  private val versionRepo = new MilestoneTemplateVersionRepoImpl
  private val templateRepo = new MilestoneTemplateRepoImpl(versionRepo)

  private def newStepTemplate(projectId: UUID, createdBy: UUID, name: String): ConnectionIO[UUID] =
    for {
      saved <- stepTemplateRepo.insert(MilestoneStepTemplate(UUID.randomUUID(), projectId, createdBy, nowAsInstant))
      _ <- stepTemplateVersionRepo.insert(
        MilestoneStepTemplateVersion(UUID.randomUUID(), saved.id, name, s"$name description", nowAsInstant)
      )
    } yield saved.id

  private def newTemplate(projectId: UUID, createdBy: UUID, name: String, steps: List[UUID]) =
    for {
      saved <- templateRepo.insert(MilestoneTemplate(UUID.randomUUID(), projectId, createdBy, nowAsInstant))
      version <- versionRepo.insert(MilestoneTemplateVersion(UUID.randomUUID(), saved.id, name, "d", nowAsInstant))
      _ <- versionRepo.insertSteps(version.version, steps.map(List(_)))
    } yield saved -> version

  testNameFromClassName should {

    "store the steps of a version in order and read them with the step template content" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        design <- newStepTemplate(projectId, account.id, "Design")
        development <- newStepTemplate(projectId, account.id, "Development")
        saved <- newTemplate(projectId, account.id, "New feature", List(development, design, development))
        full <- templateRepo.findFullById(saved._1.id)
      } yield {
        full.latest.version shouldBe saved._2
        full.latest.steps.map(_.name) shouldBe List("Development", "Design", "Development")
        full.latest.steps.map(_.description) shouldBe
          List("Development description", "Design description", "Development description")
      }).transact(transactor)
    }

    "store the steps of a stage with the same position and read them back by stage" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        design <- newStepTemplate(projectId, account.id, "Design")
        backend <- newStepTemplate(projectId, account.id, "Backend")
        frontend <- newStepTemplate(projectId, account.id, "Frontend")
        saved <- templateRepo.insert(MilestoneTemplate(UUID.randomUUID(), projectId, account.id, nowAsInstant))
        version <- versionRepo.insert(MilestoneTemplateVersion(UUID.randomUUID(), saved.id, "T", "d", nowAsInstant))
        _ <- versionRepo.insertSteps(version.version, List(List(design), List(frontend, backend, backend), Nil))
        full <- templateRepo.findFullById(saved.id)
      } yield {
        full.latest.steps.map(step => step.name -> step.stage) shouldBe
          List("Design" -> 0, "Backend" -> 1, "Frontend" -> 1)
        full.latest.stages shouldBe List(List(design), List(backend, frontend))
      }).transact(transactor)
    }

    "give the template without steps an empty list" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        saved <- newTemplate(projectId, account.id, "Empty", Nil)
        full <- templateRepo.findFullById(saved._1.id)
      } yield full.latest.steps shouldBe Nil).transact(transactor)
    }

    "keep the steps of each version and page the history newest first" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        design <- newStepTemplate(projectId, account.id, "Design")
        staging <- newStepTemplate(projectId, account.id, "Staging")
        saved <- newTemplate(projectId, account.id, "T", List(design))
        v2 <- versionRepo.insert(MilestoneTemplateVersion(UUID.randomUUID(), saved._1.id, "T2", "d", nowAsInstant))
        _ <- versionRepo.insertSteps(v2.version, List(staging, design).map(List(_)))
        history <- versionRepo.findPaginatedByTemplateId(saved._1.id, DbPageRequest.default)
        steps <- versionRepo.findStepsByVersions(history.items.map(_.version))
        full <- templateRepo.findFullById(saved._1.id)
      } yield {
        history.items.map(_.name) shouldBe List("T2", "T")
        steps(v2.version).map(_.name) shouldBe List("Staging", "Design")
        steps(saved._2.version).map(_.name) shouldBe List("Design")
        full.latest.version shouldBe v2
      }).transact(transactor)
    }

    "page the templates of a project by name with their steps" in { transactor =>
      (for {
        account <- createAccount
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        otherProject <- projectRepo.insert(
          project.copy(id = UUID.randomUUID(), organizationId = orgAndProject._1.id, name = "Other project")
        )
        otherProjectId = otherProject.id
        design <- newStepTemplate(projectId, account.id, "Design")
        _ <- newTemplate(projectId, account.id, "Release", List(design))
        _ <- newTemplate(projectId, account.id, "Bug fix", Nil)
        _ <- newTemplate(otherProjectId, account.id, "Other", Nil)
        page <- templateRepo.findPaginatedFullByProjectId(projectId, DbPageRequest.default)
      } yield {
        page.items.map(_.latest.version.name) shouldBe List("Bug fix", "Release")
        page.items.map(_.latest.steps.map(_.name)) shouldBe List(Nil, List("Design"))
        page.totalNumberOfItems shouldBe 2L
      }).transact(transactor)
    }

    "name the templates that use a step template in their latest version only" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        design <- newStepTemplate(projectId, account.id, "Design")
        staging <- newStepTemplate(projectId, account.id, "Staging")
        usesDesign <- newTemplate(projectId, account.id, "Uses design", List(design))
        // Used design once, then dropped it in a newer version.
        droppedDesign <- newTemplate(projectId, account.id, "Dropped design", List(design))
        v2 <- versionRepo.insert(
          MilestoneTemplateVersion(UUID.randomUUID(), droppedDesign._1.id, "Dropped design", "d", nowAsInstant)
        )
        _ <- versionRepo.insertSteps(v2.version, List(List(staging)))
        usingDesign <- templateRepo.findNamesUsingStepTemplate(design)
        usingStaging <- templateRepo.findNamesUsingStepTemplate(staging)
      } yield {
        usingDesign shouldBe List("Uses design")
        usingStaging shouldBe List("Dropped design")
        usesDesign._1.projectId shouldBe projectId
      }).transact(transactor)
    }

    "drop a deleted step template from the steps of the versions" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        design <- newStepTemplate(projectId, account.id, "Design")
        staging <- newStepTemplate(projectId, account.id, "Staging")
        saved <- newTemplate(projectId, account.id, "T", List(design, staging))
        _ <- stepTemplateRepo.deleteById(design)
        full <- templateRepo.findFullById(saved._1.id)
      } yield full.latest.steps.map(_.name) shouldBe List("Staging")).transact(transactor)
    }

    "delete the template together with its versions and steps" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        design <- newStepTemplate(projectId, account.id, "Design")
        saved <- newTemplate(projectId, account.id, "T", List(design))
        deleted <- templateRepo.deleteById(saved._1.id)
        steps <- versionRepo.findStepsByVersions(List(saved._2.version))
        afterDelete <- templateRepo.findById(saved._1.id).attempt
      } yield {
        deleted shouldBe 1
        steps shouldBe Map.empty
        afterDelete.isLeft shouldBe true
      }).transact(transactor)
    }
  }
}
