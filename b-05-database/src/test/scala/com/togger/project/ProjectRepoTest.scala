package com.togger.project

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, TimeHelpers}
import com.togger.organization.{TgOrganization, TgTaskStatus}
import com.togger.task.{Task, TaskRepoImpl}
import com.togger.task.TaskStatus
import com.togger.projectlabel.{ProjectLabel, ProjectLabelCategory, ProjectLabelCategoryRepoImpl, ProjectLabelRepoImpl}
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.DayOfWeek
import java.util.UUID

class ProjectRepoTest extends BusinessTgDbSpec with TgAccount with TgOrganization with TgTaskStatus with TimeHelpers {

  val taskRepo = new TaskRepoImpl

  val projectRepo = new ProjectRepoImpl
  val projectAccountRepo = new ProjectAccountRepoImpl
  val projectLabelCategoryRepo = new ProjectLabelCategoryRepoImpl
  val projectLabelRepo = new ProjectLabelRepoImpl
  val projectLabelAssignmentRepo = new ProjectLabelAssignmentRepoImpl

  def projectLabel(name: String, categoryId: UUID): ProjectLabel = ProjectLabel(
    id = UUID.randomUUID(),
    name = name,
    description = s"$name description",
    categoryId = categoryId,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  val project = Project(
    id = UUID.randomUUID(),
    organizationId = UUID.randomUUID(),
    name = "Project name",
    description = "Project description",
    icon = "🚀",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedOrganization <- createOrganization
        savedProject <- projectRepo.insert(project.copy(organizationId = savedOrganization.id))
        foundProject1 <- projectRepo.findById(savedProject.id)
        newUpdatedAt = nowAsInstant
        updatedProject <- projectRepo.update(savedProject.copy(name = "new project name"), newUpdatedAt)
        foundProject2 <- projectRepo.findById(savedProject.id)
        _ <- projectRepo.deleteById(foundProject2.id)
        foundProject3 <- projectRepo.findById(savedProject.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedProject shouldBe foundProject1)
        cp(updatedProject shouldBe foundProject2)
        cp.reportAll()
        foundProject3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "delete a project with all of its data and keep the other projects" in { transactor =>
      def count(table: String, projectId: UUID) =
        (fr"select count(*) from" ++ doobie.Fragment.const(table) ++ fr"where project_id = $projectId")
          .query[Long]
          .unique

      val res = for {
        savedAccount <- createAccount
        savedOrganization <- createOrganization
        doomed <- projectRepo.insert(project.copy(id = UUID.randomUUID(), organizationId = savedOrganization.id))
        kept <- projectRepo.insert(
          project.copy(id = UUID.randomUUID(), organizationId = savedOrganization.id, name = "Kept")
        )
        _ <- List(doomed, kept).traverse(saved =>
          projectAccountRepo.insert(ProjectAccount(saved.id, savedAccount.id, nowAsInstant))
        )
        _ <- List(doomed, kept).traverse(saved =>
          taskStatusRepo.insert(
            TaskStatus(UUID.randomUUID(), saved.id, "Todo", "", "", nowAsInstant, nowAsInstant)
          )
        )
        _ <- List(doomed, kept).traverse(saved =>
          taskRepo.insert(Task(UUID.randomUUID(), saved.id, savedAccount.id, nowAsInstant))
        )
        deleted <- projectRepo.deleteWithAllData(doomed.id)
        doomedFound <- projectRepo.findById(doomed.id).attempt
        keptFound <- projectRepo.findById(kept.id)
        doomedCounts <- List("task", "task_status", "project_account").traverse(count(_, doomed.id))
        keptCounts <- List("task", "task_status", "project_account").traverse(count(_, kept.id))
      } yield {
        val cp = new Checkpoint
        cp(deleted shouldBe 1)
        cp(doomedFound.isLeft shouldBe true)
        cp(keptFound shouldBe kept)
        cp(doomedCounts shouldBe List(0L, 0L, 0L))
        cp(keptCounts shouldBe List(1L, 1L, 1L))
        cp.reportAll()
      }

      res.transact(transactor)
    }

    "delete every project of an organization with its data" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        savedOrganization <- createOrganization
        projects <- List("First", "Second").traverse(name =>
          projectRepo.insert(project.copy(id = UUID.randomUUID(), organizationId = savedOrganization.id, name = name))
        )
        _ <- projects.traverse(saved =>
          taskRepo.insert(Task(UUID.randomUUID(), saved.id, savedAccount.id, nowAsInstant))
        )
        deleted <- projectRepo.deleteAllByOrganizationId(savedOrganization.id)
        found <- projects.traverse(saved => projectRepo.findById(saved.id).attempt)
        tasksLeft <- fr"select count(*) from task".query[Long].unique
      } yield {
        val cp = new Checkpoint
        cp(deleted shouldBe 2)
        cp(found.forall(_.isLeft) shouldBe true)
        cp(tasksLeft shouldBe 0L)
        cp.reportAll()
      }

      res.transact(transactor)
    }

    "round-trip a non-default work days set through insert, update, and findById" in { transactor =>
      val weekendWorkDays: Set[DayOfWeek] = Set(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

      val res = for {
        savedOrganization <- createOrganization
        savedProject <- projectRepo.insert(
          project.copy(organizationId = savedOrganization.id, workDays = weekendWorkDays)
        )
        foundAfterInsert <- projectRepo.findById(savedProject.id)
        newWorkDays: Set[DayOfWeek] = Set(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        updatedProject <- projectRepo.update(savedProject.copy(workDays = newWorkDays), nowAsInstant)
        foundAfterUpdate <- projectRepo.findById(savedProject.id)
      } yield {
        val cp = new Checkpoint
        cp(foundAfterInsert.workDays shouldBe weekendWorkDays)
        cp(updatedProject.workDays shouldBe newWorkDays)
        cp(foundAfterUpdate.workDays shouldBe newWorkDays)
        cp.reportAll()
      }

      res.transact(transactor)
    }

    "find by org id" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        savedOrganization <- createOrganization
        savedProject1 <- projectRepo.insert(project.copy(organizationId = savedOrganization.id))
        _ <- projectRepo.insert(
          project.copy(organizationId = savedOrganization.id, name = "Project name 2")
        )
        _ <- projectAccountRepo.insert(
          ProjectAccount(
            projectId = savedProject1.id,
            accountId = savedAccount.id,
            createdAt = nowAsInstant
          )
        )
        foundProjects <- projectRepo.findPaginatedByOrganizationIdAndAccountId(
          organizationId = savedOrganization.id,
          accountId = savedAccount.id,
          page = DbPageRequest.default
        )
      } yield {
        foundProjects.items shouldBe List(savedProject1)
      }

      res.transact(transactor)
    }

    "find projects by account id that have ALL of the given labels assigned" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        savedOrganization <- createOrganization
        category <- projectLabelCategoryRepo.insert(
          ProjectLabelCategory(
            id = UUID.randomUUID(),
            name = "Language",
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
        scalaLabel <- projectLabelRepo.insert(projectLabel("Scala", category.id))
        kotlinLabel <- projectLabelRepo.insert(projectLabel("Kotlin", category.id))
        javaLabel <- projectLabelRepo.insert(projectLabel("Java", category.id))

        // matches: member, has both selected labels
        matchingProject <- projectRepo.insert(
          project.copy(organizationId = savedOrganization.id, name = "Matching project")
        )
        _ <- projectAccountRepo.insert(ProjectAccount(matchingProject.id, savedAccount.id, nowAsInstant))
        _ <- projectLabelAssignmentRepo.insert(ProjectLabelAssignment(matchingProject.id, scalaLabel.id, nowAsInstant))
        _ <- projectLabelAssignmentRepo.insert(ProjectLabelAssignment(matchingProject.id, kotlinLabel.id, nowAsInstant))
        _ <- projectLabelAssignmentRepo.insert(ProjectLabelAssignment(matchingProject.id, javaLabel.id, nowAsInstant))

        // non-match: member, but only has a subset of the selected labels
        partiallyLabeledProject <- projectRepo.insert(
          project.copy(organizationId = savedOrganization.id, name = "Partially labeled project")
        )
        _ <- projectAccountRepo.insert(ProjectAccount(partiallyLabeledProject.id, savedAccount.id, nowAsInstant))
        _ <- projectLabelAssignmentRepo.insert(
          ProjectLabelAssignment(partiallyLabeledProject.id, scalaLabel.id, nowAsInstant)
        )

        // non-match: has both selected labels, but the account is not a member
        notMemberProject <- projectRepo.insert(
          project.copy(organizationId = savedOrganization.id, name = "Not a member project")
        )
        _ <- projectLabelAssignmentRepo.insert(ProjectLabelAssignment(notMemberProject.id, scalaLabel.id, nowAsInstant))
        _ <- projectLabelAssignmentRepo.insert(
          ProjectLabelAssignment(notMemberProject.id, kotlinLabel.id, nowAsInstant)
        )

        foundProjects <- projectRepo.findPaginatedByAccountId(
          savedAccount.id,
          Seq(scalaLabel.id, kotlinLabel.id),
          DbPageRequest.default
        )
        foundProjectsForEmptyLabels <- projectRepo.findPaginatedByAccountId(
          savedAccount.id,
          Seq.empty,
          DbPageRequest.default
        )
      } yield {
        val cp = new Checkpoint
        cp(foundProjects.items shouldBe List(matchingProject))
        // no label filter: every project of the account, labelled or not
        cp(
          foundProjectsForEmptyLabels.items.map(_.id).toSet shouldBe Set(matchingProject.id, partiallyLabeledProject.id)
        )
        cp.reportAll()
      }

      res.transact(transactor)
    }
  }

}
