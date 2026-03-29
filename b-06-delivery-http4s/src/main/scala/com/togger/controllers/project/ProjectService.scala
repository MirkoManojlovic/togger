package com.togger.controllers.project
import cats.effect.IO
import cats.implicits.toTraverseOps
import com.togger.account.Account
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.project.{Project, ProjectAccount, ProjectAccountRepo, ProjectRepo}
import com.togger.task.TaskStatusRepo
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

class ProjectService(
  projectRepo: ProjectRepo,
  projectAccountRepo: ProjectAccountRepo,
  taskStatusRepo: TaskStatusRepo,
  tgClock: TgClock,
  defaultTaskStatusService: DefaultTaskStatusService,
  transactor: Transactor[IO]
) extends TgLogging {

  def saveProject(
    project: Project,
    accountId: Long,
    membersToAdd: Seq[Long]
  ): IO[(Project, Seq[Account])] = {
    (for {
      savedProject <- projectRepo.insert(project)
      _ <- projectAccountRepo.insert(
        ProjectAccount(
          projectId = savedProject.id,
          accountId = accountId,
          createdAt = tgClock.currentInstant
        )
      )
      _ <- addMembers(savedProject, membersToAdd)
      members <- findMembersLocal(savedProject.id)
      _ <- defaultTaskStatusService.createDefaultTaskStatuses(savedProject.id).map(taskStatusRepo.insert).sequence
    } yield savedProject -> members).transact(transactor)
  }

  private def addMembers(
    project: Project,
    membersToAdd: Seq[Long]
  ): ConnectionIO[List[ProjectAccount]] = {
    membersToAdd
      .map(accountId =>
        ProjectAccount(
          projectId = project.id,
          accountId = accountId,
          createdAt = tgClock.currentInstant
        )
      )
      .map(projectAccountRepo.insert)
      .toList
      .sequence
  }

  private def removeMembers(project: Project, membersToRemove: Seq[Long]): ConnectionIO[List[Int]] = {
    membersToRemove
      .map(accountId =>
        ProjectAccount(
          projectId = project.id,
          accountId = accountId,
          createdAt = tgClock.currentInstant
        )
      )
      .map(account => projectAccountRepo.deleteById(projectId = project.id, accountId = account.accountId))
      .toList
      .sequence
  }

  private def findMembersLocal(projectId: Long): ConnectionIO[Seq[Account]] = {
    projectAccountRepo.findAllMembers(projectId)
  }

  def updateProject(
    project: Project,
    membersToAdd: Seq[Long],
    membersToRemove: Seq[Long]
  ): IO[(Project, Seq[Account])] = {
    logger.info(s"Updating a ${sp(project)}")
    val now = tgClock.currentInstant
    (for {
      updatedProject <- projectRepo.update(project, now)
      _ <- addMembers(updatedProject, membersToAdd)
      _ <- removeMembers(updatedProject, membersToRemove)
      members <- projectAccountRepo.findAllMembers(updatedProject.id)
    } yield {
      logger.info(s"Organization updated successfully ${sp(updatedProject)}")
      updatedProject -> members
    }).transact(transactor)
  }

  def findAllByOrganizationIdAndAccountId(organizationId: Long, accountId: Long): IO[Seq[Project]] = {
    projectRepo
      .findByOrganizationIdAndAccountId(organizationId, accountId)
      .transact(transactor)
      .map(_.map(_._1))
  }

  def findById(id: Long): IO[(Project, Seq[Account])] = {
    (for {
      project <- projectRepo.findById(id)
      members <- projectAccountRepo.findAllMembers(id)
    } yield project -> members).transact(transactor)

  }

  def findAllMembers(projectId: Long): IO[Seq[Account]] =
    projectAccountRepo.findAllMembers(projectId).transact(transactor)
}
