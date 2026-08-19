package com.togger.task

import cats.implicits.catsSyntaxApplicativeError
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TgIds, TimeHelpers}
import com.togger.organization.{TgProject, TgTaskStatus}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class LinkVersionRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val taskRepo = new TaskRepoImpl
  val linkRepo = new LinkRepoImpl
  val linkVersionRepo = new LinkVersionRepoImpl

  val task = Task(
    id = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    createdBy = TgIds.UNASSIGNED_UUID,
    createdAt = nowAsInstant
  )

  val link = Link(
    id = UUID.randomUUID(),
    taskId = UUID.randomUUID(),
    createdAt = nowAsInstant
  )

  val v1 = "v1"
  val v2 = "v2"

  def linkVersion(linkId: UUID, suffix: String) = LinkVersion(
    version = UUID.randomUUID(),
    linkId = linkId,
    name = s"Link name $suffix",
    url = s"url-$suffix",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createTaskAndLink() =
    for {
      savedAccount <- createAccount
      orgAndProject <- createOrgAndProject
      savedProject = orgAndProject._2
      savedTask <- taskRepo.insert(task.copy(projectId = savedProject.id, createdBy = savedAccount.id))
      savedLink <- linkRepo.insert(link.copy(taskId = savedTask.id))
    } yield savedLink

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedLink <- createTaskAndLink()
        savedVersion <- linkVersionRepo.insert(linkVersion(savedLink.id, v1))
        foundVersion1 <- linkVersionRepo.findByVersion(savedVersion.version)
        updatedVersion <- linkVersionRepo.updateContent(savedVersion.copy(url = "new url"))
        foundVersion2 <- linkVersionRepo.findByVersion(savedVersion.version)
        _ <- linkVersionRepo.deleteByVersion(foundVersion2.version)
        foundVersion3 <- linkVersionRepo.findByVersion(savedVersion.version).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedVersion shouldBe foundVersion1)
        cp(updatedVersion shouldBe foundVersion2)
        cp(updatedVersion.url shouldBe "new url")
        cp.reportAll()
        foundVersion3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find by link id, return newest first, and find the latest" in { transactor =>
      val res = for {
        savedLink <- createTaskAndLink()
        linkVersion1 <- linkVersionRepo.insert(linkVersion(savedLink.id, v1))
        linkVersion2 <- linkVersionRepo.insert(linkVersion(savedLink.id, v2))
        linkVersion3 <- linkVersionRepo.insert(linkVersion(savedLink.id, "v3"))
        foundVersions <- linkVersionRepo.findByLinkId(savedLink.id)
        latest <- linkVersionRepo.findLatestByLinkId(savedLink.id)
      } yield {
        val cp = new Checkpoint
        cp(
          foundVersions should contain theSameElementsInOrderAs List(
            linkVersion3,
            linkVersion2,
            linkVersion1
          )
        )
        cp.reportAll()
        latest shouldBe linkVersion3
      }

      res.transact(transactor)
    }

    "count versions for a link" in { transactor =>
      val res = for {
        savedLink <- createTaskAndLink()
        _ <- linkVersionRepo.insert(linkVersion(savedLink.id, v1))
        _ <- linkVersionRepo.insert(linkVersion(savedLink.id, v2))
        count <- linkVersionRepo.countByLinkId(savedLink.id)
      } yield count shouldBe 2

      res.transact(transactor)
    }

    "delete versions when the link is deleted" in { transactor =>
      val res = for {
        savedLink <- createTaskAndLink()
        _ <- linkVersionRepo.insert(linkVersion(savedLink.id, v1))
        _ <- linkVersionRepo.insert(linkVersion(savedLink.id, v2))
        _ <- linkRepo.deleteById(savedLink.id)
        remainingVersions <- linkVersionRepo.findByLinkId(savedLink.id)
      } yield remainingVersions shouldBe empty

      res.transact(transactor)
    }

  }

}
