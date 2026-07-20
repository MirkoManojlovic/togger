package com.togger.organization

import com.togger.account.Account
import com.togger.db.Util._
import com.togger.organization.OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.time.Instant
import java.util.UUID

class OrganizationRepoImpl extends OrganizationRepo {

  override def findById(id: Long): ConnectionIO[Organization] =
    logAroundInfoQuery(
      findByIdStatement(OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG)(id).query[Organization].unique
    )(
      s"Finding organization with ${sp(id)}",
      organization => s"Organization found ${sp(organization)}"
    )

  override def insert(organization: Organization): ConnectionIO[Organization] = {
    import organization._
    logAroundInfoQuery(
      insertStatement(OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG)(fr"""
        (name, description, created_at, updated_at)
        values ($name, $description, $createdAt, $updatedAt)
      """).update
        .withUniqueGeneratedKeys[Organization](
          "id",
          "name",
          "description",
          "created_at",
          "updated_at"
        )
    )(
      s"Inserting ${sp(organization)}",
      organization => s"Organization inserted ${sp(organization)}"
    )
  }

  override def deleteById(id: Long): ConnectionIO[Int] =
    logAroundInfoQuery(
      deleteByIdStatement(OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG)(id).update.run
    )(
      s"Deleting organization with ${sp(id)}",
      _ => s"Organization with ${sp(id)} deleted"
    )

  override def update(organization: Organization, updatedAt: Instant): ConnectionIO[Organization] = {
    logAroundInfoQuery(
      updateStatement(OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG)(organization.id)(fr"""
        name = ${organization.name},
        description = ${organization.description},
        created_at = ${organization.createdAt},
        updated_at = $updatedAt
      """).update
        .withUniqueGeneratedKeys[Organization](
          "id",
          "name",
          "description",
          "created_at",
          "updated_at"
        )
    )(
      s"Updating ${sp(organization)}",
      organization => s"${sp(organization)} updated"
    )
  }

  override def findAll(): ConnectionIO[Seq[Organization]] = {
    logAroundInfoQuery(
      selectAllStatement(OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG).query[Organization].to[Seq]
    )(
      "Finding all organizations",
      organizations => s"${sp(organizations)} found"
    )
  }

  override def findAllOrgByAccountId(accountId: UUID): ConnectionIO[Seq[(Organization, OrganizationAccount)]] = {
    logAroundInfoQuery(
      (selectAllStatement(ORGANIZATION_TABLE_FRAG) ++
        innerJoin(OrganizationAccountRepoImpl.ORGANIZATION_ACCOUNT_TABLE_FRAG)(
          fr"organization.id = organization_account.organization_id"
        ) ++
        fr"where organization_account.account_id = $accountId")
        .query[(Organization, OrganizationAccount)]
        .to[Seq]
    )(
      s"Finding all organizations for ${sp(accountId)}",
      organizationsAndAccounts => s"${sp(organizationsAndAccounts)} found"
    )
  }


}

object OrganizationRepoImpl {
  val ORGANIZATION_TABLE: String = "organization"
  val ORGANIZATION_TABLE_FRAG: Fragment = Fragment.const(ORGANIZATION_TABLE)
}
