package com.togger.preview

import io.circe.generic.auto._

import java.util.UUID

case class PreviewOrganization(id: UUID, name: String, description: String, icon: String)

case class PreviewMembership(id: UUID, parentId: UUID, accountId: UUID)

case class PreviewProject(
  id: UUID,
  organizationId: UUID,
  name: String,
  description: String,
  icon: String,
  workDays: Set[Int],
  projectLabelIds: List[UUID] = List.empty
)

/** Organizations and their members. A membership's `parentId` is the organization id. */
class OrganizationsPreviewData(db: PreviewDb) {

  val organizations: PreviewCollection[PreviewOrganization] =
    db.collection[PreviewOrganization](OrganizationsPreviewData.Name, _.id)

  val members: PreviewCollection[PreviewMembership] =
    db.collection[PreviewMembership](OrganizationsPreviewData.MembersName, _.id)

  def memberIds(organizationId: UUID): List[UUID] =
    members.filter(_.parentId == organizationId).map(_.accountId)

  def organizationIdsOf(accountId: UUID): Set[UUID] =
    members.filter(_.accountId == accountId).map(_.parentId).toSet

  def addMembers(organizationId: UUID, accountIds: Seq[UUID]): Unit = {
    val existing = memberIds(organizationId).toSet
    accountIds.distinct.filterNot(existing.contains).foreach { accountId =>
      members.upsert(PreviewMembership(UUID.randomUUID(), organizationId, accountId))
    }
  }

  def removeMembers(organizationId: UUID, accountIds: Seq[UUID]): Unit =
    members.deleteWhere(member => member.parentId == organizationId && accountIds.contains(member.accountId))
}

object OrganizationsPreviewData {
  val Name = "organizations"
  val MembersName = "organization-members"
}

/** Projects and their members. A membership's `parentId` is the project id. */
class ProjectsPreviewData(db: PreviewDb) {

  val projects: PreviewCollection[PreviewProject] = db.collection[PreviewProject](ProjectsPreviewData.Name, _.id)

  val members: PreviewCollection[PreviewMembership] =
    db.collection[PreviewMembership](ProjectsPreviewData.MembersName, _.id)

  private var deletionHooks: List[UUID => Unit] = List.empty

  private var creationHooks: List[UUID => Unit] = List.empty

  /** Lets other domains give a project created at runtime what the backend creates with it (default statuses, ...). */
  def onProjectCreated(hook: UUID => Unit): Unit = creationHooks = creationHooks :+ hook

  def notifyProjectCreated(projectId: UUID): Unit = creationHooks.foreach(_(projectId))

  /** Lets other domains (tasks, milestones, ...) drop their rows of a project that is being deleted. */
  def onProjectDeleted(hook: UUID => Unit): Unit = deletionHooks = deletionHooks :+ hook

  def memberIds(projectId: UUID): List[UUID] =
    members.filter(_.parentId == projectId).map(_.accountId)

  def projectIdsOf(accountId: UUID): Set[UUID] =
    members.filter(_.accountId == accountId).map(_.parentId).toSet

  def isMember(projectId: UUID, accountId: UUID): Boolean =
    members.all.exists(member => member.parentId == projectId && member.accountId == accountId)

  def addMembers(projectId: UUID, accountIds: Seq[UUID]): Unit = {
    val existing = memberIds(projectId).toSet
    accountIds.distinct.filterNot(existing.contains).foreach { accountId =>
      members.upsert(PreviewMembership(UUID.randomUUID(), projectId, accountId))
    }
  }

  def removeMembers(projectId: UUID, accountIds: Seq[UUID]): Unit =
    members.deleteWhere(member => member.parentId == projectId && accountIds.contains(member.accountId))

  def deleteProject(projectId: UUID): Boolean = {
    deletionHooks.foreach(_(projectId))
    members.deleteWhere(_.parentId == projectId)
    projects.delete(projectId)
  }
}

object ProjectsPreviewData {
  val Name = "projects"
  val MembersName = "project-members"
}
