package com.togger.project
import com.togger.account.AccountRepoImpl
import com.togger.db.Util._
import com.togger.project.ProjectRepoImpl.PROJECT_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.Meta
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time.{DayOfWeek, Instant}
import java.util.UUID

class ProjectRepoImpl extends ProjectRepo {

  implicit val workDaysCodec: Meta[Set[DayOfWeek]] = Meta[Int].imap(ProjectRepoImpl.decodeWorkDays)(ProjectRepoImpl.encodeWorkDays)

  override def findById(id: UUID): ConnectionIO[Project] =
    logAroundInfoQuery(
      findByIdStatementUUID(ProjectRepoImpl.PROJECT_TABLE_FRAG)(id).query[Project].unique
    )(
      s"Finding project with ${sp(id)}",
      project => s"Project found ${sp(project)}"
    )

  override def insert(project: Project): ConnectionIO[Project] = {
    import project._
    logAroundInfoQuery(
      insertStatement(ProjectRepoImpl.PROJECT_TABLE_FRAG)(fr"""
        (organization_id, name, description, icon, work_days, created_at, updated_at)
        values ($organizationId, $name, $description, $icon, $workDays, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[Project](
          "id",
          "organization_id",
          "name",
          "description",
          "icon",
          "work_days",
          "created_at",
          "updated_at"
        )
    )(
      s"Inserting ${sp(project)}",
      project => s"Project inserted ${sp(project)}"
    )
  }

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      deleteByIdStatementUUID(ProjectRepoImpl.PROJECT_TABLE_FRAG)(id).update.run
    )(
      s"Deleting project with ${sp(id)}",
      _ => s"Project with ${sp(id)} deleted"
    )

  override def update(project: Project, updatedAt: Instant): ConnectionIO[Project] = {
    logAroundInfoQuery(
      updateStatementUUID(ProjectRepoImpl.PROJECT_TABLE_FRAG)(project.id)(fr"""
        name = ${project.name},
        organization_id = ${project.organizationId},
        description = ${project.description},
        icon = ${project.icon},
        work_days = ${project.workDays},
        created_at = ${project.createdAt},
        updated_at = $updatedAt
      """).update
        .withUniqueGeneratedKeys[Project](
          "id",
          "organization_id",
          "name",
          "description",
          "icon",
          "work_days",
          "created_at",
          "updated_at"
        )
    )(
      s"Updating ${sp(project)}",
      project => s"${sp(project)} updated"
    )
  }

  override def findAllProjectsByAccountId(accountId: UUID): ConnectionIO[Seq[(Project, ProjectAccount)]] = {
    logAroundInfoQuery(
      (selectAllStatement(PROJECT_TABLE_FRAG) ++
        innerJoin(ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG)(
          fr"project.id = project_account.project_id"
        ))
//        fr"where project_account.project_id = $accountId")
        .query[(Project, ProjectAccount)]
        .to[Seq]
    )(
      s"Finding all projects for ${sp(accountId)}",
      projectsAndAccounts => s"${sp(projectsAndAccounts)} found"
    )
  }

  override def findByOrganizationIdAndAccountId(
    organizationId: UUID,
    accountId: UUID
  ): ConnectionIO[Seq[(Project, ProjectAccount)]] = {
    logAroundInfoQuery(
      (selectAllStatement(PROJECT_TABLE_FRAG) ++
        innerJoin(ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG)(fr"project.id = project_account.project_id") ++
        fr"where project_account.account_id = $accountId and project.organization_id = $organizationId")
        .query[(Project, ProjectAccount)]
        .to[Seq]
    )(
      s"Finding all projects for ${sp(accountId)} and ${sp(organizationId)}",
      projectsAndAccounts => s"${sp(projectsAndAccounts)} found"
    )
  }
}

object ProjectRepoImpl {
  val PROJECT_TABLE: String = "project"
  val PROJECT_TABLE_FRAG: Fragment = Fragment.const(PROJECT_TABLE)

  def encodeWorkDays(days: Set[DayOfWeek]): Int =
    days.foldLeft(0)((acc, day) => acc | (1 << (day.getValue - 1)))

  def decodeWorkDays(bitmask: Int): Set[DayOfWeek] =
    DayOfWeek.values().toSet.filter(day => (bitmask & (1 << (day.getValue - 1))) != 0)
}
