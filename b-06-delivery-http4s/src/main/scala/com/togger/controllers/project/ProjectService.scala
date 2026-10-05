package com.togger.controllers.project
import cats.effect.IO
import cats.implicits.toTraverseOps
import com.togger.account.Account
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.project.{Project, ProjectAccount, ProjectAccountRepo, ProjectRepo, ProjectTechStack, ProjectTechStackRepo}
import com.togger.task.{TaskLabelRepo, TaskStatusRepo}
import com.togger.techstack.TechStack
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

class ProjectService(
  projectRepo: ProjectRepo,
  projectAccountRepo: ProjectAccountRepo,
  taskStatusRepo: TaskStatusRepo,
  taskLabelRepo: TaskLabelRepo,
  projectTechStackRepo: ProjectTechStackRepo,
  tgClock: TgClock,
  defaultTaskStatusService: DefaultTaskStatusService,
  defaultTaskLabelService: DefaultTaskLabelService,
  transactor: Transactor[IO]
) extends TgLogging {

  def saveProject(
    project: Project,
    accountId: UUID,
    membersToAdd: Seq[UUID],
    techStackIds: Seq[UUID] = Seq.empty
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
      _ <- addTechStacks(savedProject, techStackIds)
      _ <- defaultTaskStatusService.createDefaultTaskStatuses(savedProject.id).map(taskStatusRepo.insert).sequence
      _ <- defaultTaskLabelService.createDefaultTaskLabels(savedProject.id).map(taskLabelRepo.insert).sequence
    } yield savedProject -> members).transact(transactor)
  }

  private def addMembers(
    project: Project,
    membersToAdd: Seq[UUID]
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

  private def removeMembers(project: Project, membersToRemove: Seq[UUID]): ConnectionIO[List[Int]] = {
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

  private def addTechStacks(project: Project, techStackIds: Seq[UUID]): ConnectionIO[List[ProjectTechStack]] = {
    techStackIds
      .map(techStackId => ProjectTechStack(projectId = project.id, techStackId = techStackId, createdAt = tgClock.currentInstant))
      .map(projectTechStackRepo.insert)
      .toList
      .sequence
  }

  private def findMembersLocal(projectId: UUID): ConnectionIO[Seq[Account]] = {
    projectAccountRepo.findAllMembers(projectId)
  }

  def updateProject(
    project: Project,
    membersToAdd: Seq[UUID],
    membersToRemove: Seq[UUID],
    techStackIds: Seq[UUID] = Seq.empty
  ): IO[(Project, Seq[Account], Seq[TechStack])] = {
    logger.info(s"Updating a ${sp(project)}")
    val now = tgClock.currentInstant
    (for {
      updatedProject <- projectRepo.update(project, now)
      _ <- addMembers(updatedProject, membersToAdd)
      _ <- removeMembers(updatedProject, membersToRemove)
      members <- projectAccountRepo.findAllMembers(updatedProject.id)
      _ <- projectTechStackRepo.deleteAllByProjectId(updatedProject.id)
      _ <- addTechStacks(updatedProject, techStackIds)
      techStacks <- projectTechStackRepo.findTechStacksByProjectId(updatedProject.id)
    } yield {
      logger.info(s"Organization updated successfully ${sp(updatedProject)}")
      (updatedProject, members, techStacks)
    }).transact(transactor)
  }

  def findAllByOrganizationIdAndAccountId(organizationId: UUID, accountId: UUID): IO[Seq[Project]] = {
    projectRepo
      .findByOrganizationIdAndAccountId(organizationId, accountId)
      .transact(transactor)
      .map(_.map(_._1))
  }

  def findById(id: UUID): IO[(Project, Seq[Account], Seq[TechStack])] = {
    (for {
      project <- projectRepo.findById(id)
      members <- projectAccountRepo.findAllMembers(id)
      techStacks <- projectTechStackRepo.findTechStacksByProjectId(id)
    } yield (project, members, techStacks)).transact(transactor)

  }

  def findAllMembers(projectId: UUID): IO[Seq[Account]] =
    projectAccountRepo.findAllMembers(projectId).transact(transactor)

  def findTechStacksByProjectIds(projectIds: Seq[UUID]): IO[Map[UUID, List[TechStack]]] =
    projectTechStackRepo.findTechStacksByProjectIds(projectIds).transact(transactor)
}
