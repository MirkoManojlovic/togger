package com.togger.tasktemplate

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, PaginationTestSupport, TimeHelpers}
import com.togger.organization.TgProject
import com.togger.task.{TaskLabel, TaskLabelRepoImpl}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TaskTemplateRepoTest extends BusinessTgDbSpec
    with PaginationTestSupport
    with TgAccount
    with TgProject
    with TimeHelpers {

  private val templateRepo = new TaskTemplateRepoImpl
  private val versionRepo = new TaskTemplateVersionRepoImpl
  private val labelRepo = new TaskLabelRepoImpl

  private def newLabel(projectId: UUID, name: String) =
    TaskLabel(UUID.randomUUID(), projectId, name, s"$name description", "🏷️", nowAsInstant, nowAsInstant)

  private def newTemplate(projectId: UUID, createdBy: UUID, labelId: Option[UUID]) =
    TaskTemplate(UUID.randomUUID(), projectId, labelId, createdBy, nowAsInstant)

  private def newVersion(templateId: UUID, name: String, body: String) =
    TaskTemplateVersion(UUID.randomUUID(), templateId, name, body, nowAsInstant)

  private def insertWithVersion(template: TaskTemplate, name: String, body: String) =
    for {
      saved <- templateRepo.insert(template)
      version <- versionRepo.insert(newVersion(saved.id, name, body))
    } yield saved -> version

  testNameFromClassName should {

    "insert, find and delete a template together with its versions" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        saved <- templateRepo.insert(newTemplate(projectId, account.id, None))
        found <- templateRepo.findById(saved.id)
        v1 <- versionRepo.insert(newVersion(saved.id, "name", "body"))
        deleted <- templateRepo.deleteById(saved.id)
        versionsLeft <- versionRepo.countByTemplateId(saved.id)
        afterDelete <- templateRepo.findById(saved.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(found shouldBe saved)
        cp(v1.taskTemplateId shouldBe saved.id)
        cp(deleted shouldBe 1)
        cp(versionsLeft shouldBe 0)
        cp(afterDelete.isLeft shouldBe true)
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "find the full template with its latest version and the name of the label" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        label <- labelRepo.insert(newLabel(projectId, "feature"))
        saved <- templateRepo.insert(newTemplate(projectId, account.id, Some(label.id)))
        _ <- versionRepo.insert(newVersion(saved.id, "first", "first body"))
        latest <- versionRepo.insert(newVersion(saved.id, "second", "second body"))
        full <- templateRepo.findFullById(saved.id)
        byLabel <- templateRepo.findFullByLabelId(label.id)
        unknownLabel <- templateRepo.findFullByLabelId(UUID.randomUUID())
      } yield {
        val cp = new Checkpoint
        cp(full shouldBe TaskTemplateFull(saved, latest, Some("feature")))
        cp(byLabel shouldBe Some(full))
        cp(unknownLabel shouldBe None)
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "have no label name for a template without a label" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        both <- insertWithVersion(newTemplate(projectId, account.id, None), "n", "b")
        full <- templateRepo.findFullById(both._1.id)
      } yield full shouldBe TaskTemplateFull(both._1, both._2, None)).transact(transactor)
    }

    "move, remove and look up the label link" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        first <- labelRepo.insert(newLabel(projectId, "first"))
        second <- labelRepo.insert(newLabel(projectId, "second"))
        saved <- templateRepo.insert(newTemplate(projectId, account.id, Some(first.id)))
        linkedToFirst <- templateRepo.findByLabelId(first.id)
        moved <- templateRepo.updateLabel(saved.id, Some(second.id))
        firstAfterMove <- templateRepo.findByLabelId(first.id)
        removed <- templateRepo.updateLabel(saved.id, None)
      } yield {
        val cp = new Checkpoint
        cp(linkedToFirst shouldBe Some(saved))
        cp(moved.taskLabelId shouldBe Some(second.id))
        cp(firstAfterMove shouldBe None)
        cp(removed.taskLabelId shouldBe None)
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "reject two templates linked to the same label, but allow many without a label" in { transactor =>
      for {
        ids <- (for {
          account <- createAccount
          projectId <- createOrgAndProject.map(_._2.id)
          label <- labelRepo.insert(newLabel(projectId, "feature"))
          _ <- templateRepo.insert(newTemplate(projectId, account.id, Some(label.id)))
        } yield (account.id, projectId, label.id)).transact(transactor)
        (accountId, projectId, labelId) = ids
        duplicate <- templateRepo.insert(newTemplate(projectId, accountId, Some(labelId))).transact(transactor).attempt
        unlinked <- (templateRepo.insert(newTemplate(projectId, accountId, None)) *>
          templateRepo.insert(newTemplate(projectId, accountId, None))).transact(transactor).attempt
      } yield {
        duplicate.isLeft shouldBe true
        unlinked.isRight shouldBe true
      }
    }

    "split 11 templates of a project over 3 pages of 5, by the name of the latest version" in { transactor =>
      (for {
        account <- createAccount
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        otherProject <- projectRepo.insert(project.copy(organizationId = orgAndProject._1.id, name = "Other project"))
        created <- (0 to 10).toList.reverse.traverse { index =>
          insertWithVersion(newTemplate(projectId, account.id, None), "old name", "b").flatMap { case (saved, _) =>
            versionRepo.insert(newVersion(saved.id, f"Template $index%02d", "b")).as(index -> saved)
          }
        }
        _ <- insertWithVersion(newTemplate(otherProject.id, account.id, None), "Template 00", "b")
        result <- assertElevenRowsOverThreePages(
          templateRepo.findPaginatedFullByProjectId(projectId, _),
          created.sortBy(_._1).map(_._2.id)
        )(_.template.id)
      } yield result).transact(transactor)
    }

    "keep the template and clear the link when the label is deleted" in { transactor =>
      (for {
        account <- createAccount
        projectId <- createOrgAndProject.map(_._2.id)
        label <- labelRepo.insert(newLabel(projectId, "feature"))
        saved <- insertWithVersion(newTemplate(projectId, account.id, Some(label.id)), "n", "b").map(_._1)
        _ <- labelRepo.deleteById(label.id)
        found <- templateRepo.findFullById(saved.id)
      } yield {
        found.template.taskLabelId shouldBe None
        found.taskLabelName shouldBe None
      }).transact(transactor)
    }
  }
}
