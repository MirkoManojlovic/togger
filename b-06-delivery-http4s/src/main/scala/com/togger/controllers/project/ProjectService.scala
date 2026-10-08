package com.togger.controllers.project
import cats.effect.IO
import cats.implicits.toTraverseOps
import com.togger.account.Account
import com.togger.common.{DbPageRequest, PaginatedResponse, TgClock}
import com.togger.logging.TgLogging
import com.togger.project.{
  Project,
  ProjectAccount,
  ProjectAccountRepo,
  ProjectRepo,
  ProjectLabelAssignment,
  ProjectLabelAssignmentRepo
}
import com.togger.task.{TaskLabelRepo, TaskStatusRepo}
import com.togger.projectlabel.ProjectLabel
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

class ProjectService(
  projectRepo: ProjectRepo,
  projectAccountRepo: ProjectAccountRepo,
  taskStatusRepo: TaskStatusRepo,
  taskLabelRepo: TaskLabelRepo,
  projectLabelAssignmentRepo: ProjectLabelAssignmentRepo,
  tgClock: TgClock,
  defaultTaskStatusService: DefaultTaskStatusService,
  defaultTaskLabelService: DefaultTaskLabelService,
  transactor: Transactor[IO]
) extends TgLogging {

  private type ProjectDetails = (Project, Seq[Account], Seq[ProjectLabel])

  def saveProject(
    project: Project,
    accountId: UUID,
    membersToAdd: Seq[UUID],
    projectLabelIds: Seq[UUID] = Seq.empty
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
      _ <- addProjectLabels(savedProject, projectLabelIds)
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

  private def addProjectLabels(
    project: Project,
    projectLabelIds: Seq[UUID]
  ): ConnectionIO[List[ProjectLabelAssignment]] = {
    projectLabelIds
      .map(projectLabelId =>
        ProjectLabelAssignment(
          projectId = project.id,
          projectLabelId = projectLabelId,
          createdAt = tgClock.currentInstant
        )
      )
      .map(projectLabelAssignmentRepo.insert)
      .toList
      .sequence
  }

  // The members embedded in a project response are the first page of the maximum size; a project with more members
  // shows only those (the paginated members endpoint reads them all).
  private def findMembersLocal(projectId: UUID): ConnectionIO[Seq[Account]] =
    projectAccountRepo.findPaginatedMembers(projectId, DbPageRequest.firstMaxSized).map(_.items)

  def updateProject(
    project: Project,
    membersToAdd: Seq[UUID],
    membersToRemove: Seq[UUID],
    projectLabelIds: Seq[UUID] = Seq.empty
  ): IO[(Project, Seq[Account], Seq[ProjectLabel])] = {
    logger.info(s"Updating a ${sp(project)}")
    val now = tgClock.currentInstant
    (for {
      updatedProject <- projectRepo.update(project, now)
      _ <- addMembers(updatedProject, membersToAdd)
      _ <- removeMembers(updatedProject, membersToRemove)
      members <- findMembersLocal(updatedProject.id)
      _ <- projectLabelAssignmentRepo.deleteAllByProjectId(updatedProject.id)
      _ <- addProjectLabels(updatedProject, projectLabelIds)
      projectLabels <- projectLabelAssignmentRepo.findProjectLabelsByProjectId(updatedProject.id)
    } yield {
      logger.info(s"Organization updated successfully ${sp(updatedProject)}")
      (updatedProject, members, projectLabels)
    }).transact(transactor)
  }

  def findPaginatedByOrganizationIdAndAccountId(
    organizationId: UUID,
    accountId: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[Project]] =
    projectRepo.findPaginatedByOrganizationIdAndAccountId(organizationId, accountId, page).transact(transactor)

  def findById(id: UUID): IO[(Project, Seq[Account], Seq[ProjectLabel])] = {
    (for {
      project <- projectRepo.findById(id)
      members <- findMembersLocal(id)
      projectLabels <- projectLabelAssignmentRepo.findProjectLabelsByProjectId(id)
    } yield (project, members, projectLabels)).transact(transactor)

  }

  def findPaginatedMembers(projectId: UUID, page: DbPageRequest): IO[PaginatedResponse[Account]] =
    projectAccountRepo.findPaginatedMembers(projectId, page).transact(transactor)

  def findProjectLabelsByProjectIds(projectIds: Seq[UUID]): IO[Map[UUID, List[ProjectLabel]]] =
    projectLabelAssignmentRepo.findProjectLabelsByProjectIds(projectIds).transact(transactor)

  def searchByLabels(
    accountId: UUID,
    labelIds: Seq[UUID],
    page: DbPageRequest
  ): IO[PaginatedResponse[ProjectDetails]] =
    if (labelIds.isEmpty) {
      IO.pure(PaginatedResponse.empty[ProjectDetails](page.page.pageValue, page.limit))
    } else {
      (for {
        projectsPage <- projectRepo.findPaginatedByAccountId(accountId, labelIds, page)
        projectIds = projectsPage.items.map(_.id)
        membersByProjectId <- projectAccountRepo.findAllMembersByProjectIds(projectIds)
        labelsByProjectId <- projectLabelAssignmentRepo.findProjectLabelsByProjectIds(projectIds)
      } yield projectsPage.mapItem[ProjectDetails](project =>
        (
          project,
          membersByProjectId.getOrElse(project.id, Seq.empty),
          labelsByProjectId.getOrElse(project.id, List.empty)
        )
      )).transact(transactor)
    }
}
