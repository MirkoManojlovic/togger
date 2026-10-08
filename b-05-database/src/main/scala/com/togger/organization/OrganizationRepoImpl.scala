package com.togger.organization

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.organization.OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time.Instant
import java.util.UUID

class OrganizationRepoImpl extends OrganizationRepo {

  override def findById(id: UUID): ConnectionIO[Organization] =
    logAroundInfoQuery(
      findByIdStatementUUID(OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG)(id).query[Organization].unique
    )(
      s"Finding organization with ${sp(id)}",
      organization => s"Organization found ${sp(organization)}"
    )

  override def insert(organization: Organization): ConnectionIO[Organization] = {
    import organization._
    logAroundInfoQuery(
      insertStatement(OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG)(fr"""
        (name, description, icon, created_at, updated_at)
        values ($name, $description, $icon, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[Organization](
          "id",
          "name",
          "description",
          "icon",
          "created_at",
          "updated_at"
        )
    )(
      s"Inserting ${sp(organization)}",
      organization => s"Organization inserted ${sp(organization)}"
    )
  }

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      deleteByIdStatementUUID(OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG)(id).update.run
    )(
      s"Deleting organization with ${sp(id)}",
      _ => s"Organization with ${sp(id)} deleted"
    )

  override def update(organization: Organization, updatedAt: Instant): ConnectionIO[Organization] = {
    logAroundInfoQuery(
      updateStatementUUID(OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG)(organization.id)(fr"""
        name = ${organization.name},
        description = ${organization.description},
        icon = ${organization.icon},
        created_at = ${organization.createdAt},
        updated_at = $updatedAt
      """).update
        .withUniqueGeneratedKeys[Organization](
          "id",
          "name",
          "description",
          "icon",
          "created_at",
          "updated_at"
        )
    )(
      s"Updating ${sp(organization)}",
      organization => s"${sp(organization)} updated"
    )
  }

  override def findPaginated(page: DbPageRequest): ConnectionIO[PaginatedResponse[Organization]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[Organization](
        pageQuery = request =>
          (fr"select organization.* from" ++ ORGANIZATION_TABLE_FRAG ++
            PaginationSql.orderBy(fr"organization.name", fr"organization.id") ++
            PaginationSql.limitOffset(request)).query[Organization].to[List],
        countQuery = (fr"select count(*) from" ++ ORGANIZATION_TABLE_FRAG).query[Long].unique
      ).run(page)
    )(
      s"Finding organizations with ${sp(page)}",
      organizations => s"${sp(organizations)} found"
    )

  override def findPaginatedByAccountId(
    accountId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[Organization]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[Organization](
        pageQuery = request =>
          (fr"select organization.*" ++ memberOrganizations(accountId) ++
            PaginationSql.orderBy(fr"organization.name", fr"organization.id") ++
            PaginationSql.limitOffset(request)).query[Organization].to[List],
        countQuery = (fr"select count(*)" ++ memberOrganizations(accountId)).query[Long].unique
      ).run(page)
    )(
      s"Finding organizations for ${sp(accountId)} with ${sp(page)}",
      organizations => s"${sp(organizations)} found for ${sp(accountId)}"
    )

  // Shared by the page and the count query.
  private def memberOrganizations(accountId: UUID): Fragment =
    fr"from" ++ ORGANIZATION_TABLE_FRAG ++
      innerJoin(OrganizationAccountRepoImpl.ORGANIZATION_ACCOUNT_TABLE_FRAG)(
        fr"organization.id = organization_account.organization_id"
      ) ++
      fr"where organization_account.account_id = $accountId"

}

object OrganizationRepoImpl {
  val ORGANIZATION_TABLE: String = "organization"
  val ORGANIZATION_TABLE_FRAG: Fragment = Fragment.const(ORGANIZATION_TABLE)
}
