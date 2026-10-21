package com.togger.tasktemplate

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, PaginationTestSupport, TimeHelpers}
import com.togger.organization.TgProject
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TaskTemplateVersionRepoTest
    extends BusinessTgDbSpec
    with PaginationTestSupport
    with TgAccount
    with TgProject
    with TimeHelpers {

  private val templateRepo = new TaskTemplateRepoImpl
  private val versionRepo = new TaskTemplateVersionRepoImpl

  private def newVersion(templateId: UUID, suffix: String) =
    TaskTemplateVersion(UUID.randomUUID(), templateId, s"name $suffix", s"body $suffix", nowAsInstant)

  private def createTemplate(): ConnectionIO[TaskTemplate] =
    for {
      account <- createAccount
      projectId <- createOrgAndProject.map(_._2.id)
      template <- templateRepo.insert(TaskTemplate(UUID.randomUUID(), projectId, None, account.id, nowAsInstant))
    } yield template

  testNameFromClassName should {

    "insert versions with generated ids and find the latest one" in { transactor =>
      (for {
        template <- createTemplate()
        v1 <- versionRepo.insert(newVersion(template.id, "v1"))
        v2 <- versionRepo.insert(newVersion(template.id, "v2"))
        found <- versionRepo.findByVersion(v1.version)
        latest <- versionRepo.findLatestByTemplateId(template.id)
        count <- versionRepo.countByTemplateId(template.id)
      } yield {
        val cp = new Checkpoint
        cp(found shouldBe v1)
        cp(latest shouldBe v2)
        cp(count shouldBe 2)
        cp(v1.name shouldBe "name v1")
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "split 11 versions over 3 pages of 5, newest first" in { transactor =>
      (for {
        template <- createTemplate()
        versions <- (0 to 10).toList.traverse(index => versionRepo.insert(newVersion(template.id, s"v$index")))
        other <- createOtherTemplate(template)
        _ <- versionRepo.insert(newVersion(other.id, "other"))
        result <- assertElevenRowsOverThreePages(
          versionRepo.findPaginatedByTemplateId(template.id, _),
          versions.reverse.map(_.version)
        )(_.version)
      } yield result).transact(transactor)
    }
  }

  private def createOtherTemplate(template: TaskTemplate): ConnectionIO[TaskTemplate] =
    templateRepo.insert(template.copy(id = UUID.randomUUID()))
}
