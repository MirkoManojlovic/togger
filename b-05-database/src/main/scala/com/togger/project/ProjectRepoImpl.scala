package com.togger.project
import cats.data.NonEmptyList
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.project.ProjectRepoImpl.PROJECT_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.{ConnectionIO, Fragments, Meta}
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time.{DayOfWeek, Instant}
import java.util.UUID

class ProjectRepoImpl extends ProjectRepo {

  implicit val workDaysCodec: Meta[Set[DayOfWeek]] =
    Meta[Int].imap(ProjectRepoImpl.decodeWorkDays)(ProjectRepoImpl.encodeWorkDays)

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

  override def findPaginatedByOrganizationIdAndAccountId(
    organizationId: UUID,
    accountId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[Project]] =
    logAroundInfoQuery(
      memberProjectsPage(accountId, List(fr"project.organization_id = $organizationId"), page)
    )(
      s"Finding projects of ${sp(organizationId)} for ${sp(accountId)} with ${sp(page)}",
      projects => s"Projects found ${sp(projects)} for ${sp(accountId)} and ${sp(organizationId)}"
    )

  override def findPaginatedByAccountId(
    accountId: UUID,
    labelIds: Seq[UUID],
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[Project]] =
    logAroundInfoQuery(
      memberProjectsPage(accountId, NonEmptyList.fromList(labelIds.distinct.toList).map(hasAllLabels).toList, page)
    )(
      s"Finding projects for ${sp(accountId)} with all of ${sp(labelIds)} and ${sp(page)}",
      projects => s"Projects found ${sp(projects)} for ${sp(accountId)}"
    )

  private def memberProjectsPage(
    accountId: UUID,
    conditions: List[Fragment],
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[Project]] =
    TgFindPaginatedQuery[Project](
      pageQuery = request =>
        (fr"select project.*" ++ memberProjects(accountId, conditions) ++
          PaginationSql.orderBy(fr"project.name", fr"project.id") ++
          PaginationSql.limitOffset(request)).query[Project].to[List],
      countQuery = (fr"select count(*)" ++ memberProjects(accountId, conditions)).query[Long].unique
    ).run(page)

  // Shared by the page and the count query. A project has one row per member, so one account's membership is one row.
  private def memberProjects(accountId: UUID, conditions: List[Fragment]): Fragment =
    fr"from" ++ PROJECT_TABLE_FRAG ++
      innerJoin(ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG)(fr"project.id = project_account.project_id") ++
      Fragments.whereAnd(fr"project_account.account_id = $accountId", conditions: _*)

  // A project with several of the labels stays one row: the labels are counted in a subquery, not joined.
  private def hasAllLabels(labelIds: NonEmptyList[UUID]): Fragment =
    fr"project.id in (select project_id from" ++ ProjectLabelAssignmentRepoImpl.PROJECT_LABEL_ASSIGNMENT_TABLE_FRAG ++
      fr"where" ++ Fragments.in(fr"project_label_id", labelIds) ++
      fr"group by project_id having count(distinct project_label_id) = ${labelIds.size})"
}

object ProjectRepoImpl {
  val PROJECT_TABLE: String = "project"
  val PROJECT_TABLE_FRAG: Fragment = Fragment.const(PROJECT_TABLE)

  def encodeWorkDays(days: Set[DayOfWeek]): Int =
    days.foldLeft(0)((acc, day) => acc | (1 << (day.getValue - 1)))

  def decodeWorkDays(bitmask: Int): Set[DayOfWeek] =
    DayOfWeek.values().toSet.filter(day => (bitmask & (1 << (day.getValue - 1))) != 0)
}
