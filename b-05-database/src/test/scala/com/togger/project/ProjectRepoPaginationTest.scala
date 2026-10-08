package com.togger.project

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PageRequest, PaginatedResponse, TimeHelpers}
import com.togger.organization.{Organization, TgProject}
import com.togger.projectlabel.{ProjectLabel, ProjectLabelCategory, ProjectLabelCategoryRepoImpl, ProjectLabelRepoImpl}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class ProjectRepoPaginationTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  private val projectAccountRepo = new ProjectAccountRepoImpl
  private val projectLabelCategoryRepo = new ProjectLabelCategoryRepoImpl
  private val projectLabelRepo = new ProjectLabelRepoImpl
  private val projectLabelAssignmentRepo = new ProjectLabelAssignmentRepoImpl

  /** Eleven projects "Project 00".."Project 10" of the account in `organization`, inserted in reverse so the order is
    * by name and not by insertion. Labelled with both labels for 0-5, with `scala` only for 6-8, unlabelled for 9-10.
    * Besides: one more project of the account in another organization, and one project of the organization the
    * account is not a member of.
    */
  private case class Seeded(
    accountId: UUID,
    organization: Organization,
    projects: Vector[Project],
    otherOrganizationProject: Project,
    foreignProject: Project,
    scala: ProjectLabel,
    kotlin: ProjectLabel
  ) {
    def namesOf(indexes: Seq[Int]): List[String] = indexes.map(index => f"Project $index%02d").toList
  }

  private def newProject(organizationId: UUID, name: String): Project =
    project.copy(id = UUID.randomUUID(), organizationId = organizationId, name = name)

  private def label(name: String, categoryId: UUID) =
    ProjectLabel(UUID.randomUUID(), name, s"$name description", categoryId, nowAsInstant, nowAsInstant)

  private def seed(): ConnectionIO[Seeded] =
    for {
      account <- createAccount
      savedOrganization <- organizationRepo.insert(organization)
      otherOrganization <- organizationRepo.insert(organization.copy(name = "Other organization"))
      category <- projectLabelCategoryRepo.insert(
        ProjectLabelCategory(UUID.randomUUID(), "Language", nowAsInstant, nowAsInstant)
      )
      scala <- projectLabelRepo.insert(label("Scala", category.id))
      kotlin <- projectLabelRepo.insert(label("Kotlin", category.id))
      savedProjects <- (0 to 10).toList.reverse.traverse { index =>
        for {
          created <- projectRepo.insert(newProject(savedOrganization.id, f"Project $index%02d"))
          _ <- projectAccountRepo.insert(ProjectAccount(created.id, account.id, nowAsInstant))
          labels = if (index <= 5) List(scala, kotlin) else if (index <= 8) List(scala) else List.empty
          _ <- labels.traverse(l =>
            projectLabelAssignmentRepo.insert(ProjectLabelAssignment(created.id, l.id, nowAsInstant))
          )
        } yield created
      }
      otherOrganizationProject <- projectRepo.insert(newProject(otherOrganization.id, "Project in other organization"))
      _ <- projectAccountRepo.insert(ProjectAccount(otherOrganizationProject.id, account.id, nowAsInstant))
      foreignProject <- projectRepo.insert(newProject(savedOrganization.id, "Project of somebody else"))
    } yield Seeded(
      account.id,
      savedOrganization,
      savedProjects.sortBy(_.name).toVector,
      otherOrganizationProject,
      foreignProject,
      scala,
      kotlin
    )

  private def pageRequest(pageNumber: Int, size: Int): DbPageRequest =
    DbPageRequest.fromPageRequest(PageRequest(pageNumber, size)).toOption.get

  private def inOrganization(seeded: Seeded, pageNumber: Int, size: Int): ConnectionIO[PaginatedResponse[Project]] =
    projectRepo.findPaginatedByOrganizationIdAndAccountId(
      seeded.organization.id,
      seeded.accountId,
      pageRequest(pageNumber, size)
    )

  private def ofAccount(
    seeded: Seeded,
    labelIds: Seq[UUID] = Seq.empty,
    pageNumber: Int = 1,
    size: Int = 100
  ): ConnectionIO[PaginatedResponse[Project]] =
    projectRepo.findPaginatedByAccountId(seeded.accountId, labelIds, pageRequest(pageNumber, size))

  testNameFromClassName should {

    "split the 11 projects of an organization over 3 pages of 5, by name, without a duplicate" in { transactor =>
      (for {
        seeded <- seed()
        pages <- (1 to 3).toList.traverse(inOrganization(seeded, _, 5))
      } yield {
        val cp = new Checkpoint
        cp(pages.map(_.items.size) shouldBe List(5, 5, 1))
        cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(11L))
        cp(pages.map(_.totalNumberOfPages).distinct shouldBe List(3L))
        cp(pages.map(_.page) shouldBe List(1, 2, 3))
        cp(pages.flatMap(_.items) shouldBe seeded.projects.toList)
        cp(pages.flatMap(_.items.map(_.name)) shouldBe seeded.namesOf(0 to 10))
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "return an empty page with the totals past the last page of an organization" in { transactor =>
      (for {
        seeded <- seed()
        past <- inOrganization(seeded, 4, 5)
      } yield {
        past.items shouldBe empty
        past.totalNumberOfItems shouldBe 11L
        past.totalNumberOfPages shouldBe 3L
      }).transact(transactor)
    }

    "leave out the projects of other organizations and the ones the account is not a member of" in { transactor =>
      (for {
        seeded <- seed()
        found <- inOrganization(seeded, 1, 100)
      } yield {
        val cp = new Checkpoint
        cp(found.items should not contain seeded.otherOrganizationProject)
        cp(found.items should not contain seeded.foreignProject)
        cp(found.totalNumberOfItems shouldBe 11L)
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "find nothing for an account without projects" in { transactor =>
      (for {
        seeded <- seed()
        found <- projectRepo.findPaginatedByOrganizationIdAndAccountId(
          seeded.organization.id,
          UUID.randomUUID(),
          pageRequest(1, 5)
        )
      } yield {
        found.items shouldBe empty
        found.totalNumberOfItems shouldBe 0L
      }).transact(transactor)
    }

    "find every project of the account across organizations when no labels are given" in { transactor =>
      (for {
        seeded <- seed()
        found <- ofAccount(seeded)
      } yield {
        found.totalNumberOfItems shouldBe 12L
        found.items.map(_.name) shouldBe (seeded.namesOf(0 to 10) :+ "Project in other organization")
      }).transact(transactor)
    }

    "paginate the projects of the account over its pages" in { transactor =>
      (for {
        seeded <- seed()
        pages <- (1 to 3).toList.traverse(ofAccount(seeded, Seq.empty, _, 5))
      } yield {
        pages.map(_.items.size) shouldBe List(5, 5, 2)
        pages.map(_.totalNumberOfItems).distinct shouldBe List(12L)
        pages.flatMap(_.items.map(_.id)).distinct should have size 12
      }).transact(transactor)
    }

    "find the projects that have ALL of the labels, a project with both counting once" in { transactor =>
      (for {
        seeded <- seed()
        both <- ofAccount(seeded, Seq(seeded.scala.id, seeded.kotlin.id))
        onlyKotlin <- ofAccount(seeded, Seq(seeded.kotlin.id))
        onlyScala <- ofAccount(seeded, Seq(seeded.scala.id))
        repeated <- ofAccount(seeded, Seq(seeded.scala.id, seeded.scala.id))
      } yield {
        val cp = new Checkpoint
        cp(both.items.map(_.name) shouldBe seeded.namesOf(0 to 5))
        cp(both.totalNumberOfItems shouldBe 6L)
        cp(onlyKotlin.items.map(_.name) shouldBe seeded.namesOf(0 to 5))
        cp(onlyScala.items.map(_.name) shouldBe seeded.namesOf(0 to 8))
        cp(repeated.items.map(_.name) shouldBe seeded.namesOf(0 to 8))
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "paginate a label search over its pages without a duplicate" in { transactor =>
      (for {
        seeded <- seed()
        pages <- (1 to 3).toList.traverse(ofAccount(seeded, Seq(seeded.scala.id), _, 4))
      } yield {
        val cp = new Checkpoint
        cp(pages.map(_.items.size) shouldBe List(4, 4, 1))
        cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(9L))
        cp(pages.flatMap(_.items.map(_.name)) shouldBe seeded.namesOf(0 to 8))
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "find nothing for labels no project has" in { transactor =>
      (for {
        seeded <- seed()
        found <- ofAccount(seeded, Seq(UUID.randomUUID()))
      } yield {
        found.items shouldBe empty
        found.totalNumberOfItems shouldBe 0L
      }).transact(transactor)
    }
  }
}
