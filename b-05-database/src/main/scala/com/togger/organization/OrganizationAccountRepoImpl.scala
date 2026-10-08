package com.togger.organization

import com.togger.account.AccountRepoImpl.passwordCodec
import com.togger.account.{Account, AccountRepoImpl}
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class OrganizationAccountRepoImpl extends OrganizationAccountRepo {

  override def findById(organizationId: UUID, accountId: UUID): ConnectionIO[OrganizationAccount] =
    logAroundInfoQuery(
      (selectAllStatement(OrganizationAccountRepoImpl.ORGANIZATION_ACCOUNT_TABLE_FRAG) ++
        fr"where organization_id = $organizationId and account_id = $accountId")
        .query[OrganizationAccount]
        .unique
    )(
      s"Finding organization_account with ${sp(organizationId)} and ${sp(accountId)}",
      organization => s"Organization_Account found ${sp(organization)}"
    )

  override def insert(organizationAccount: OrganizationAccount): ConnectionIO[OrganizationAccount] = {
    import organizationAccount._
    logAroundInfoQuery(
      insertStatement(OrganizationAccountRepoImpl.ORGANIZATION_ACCOUNT_TABLE_FRAG)(fr"""
        (organization_id, account_id, created_at)
        values ($organizationId, $accountId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[OrganizationAccount](
          "organization_id",
          "account_id",
          "created_at"
        )
    )(
      s"Inserting ${sp(organizationAccount)}",
      organizationAccount => s"Organization_account inserted ${sp(organizationAccount)}"
    )
  }

  override def deleteById(organizationId: UUID, accountId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(OrganizationAccountRepoImpl.ORGANIZATION_ACCOUNT_TABLE_FRAG) ++
        fr"where organization_id = $organizationId and account_id = $accountId").update.run
    )(
      s"Deleting organization_account with ${sp(organizationId)} and ${sp(accountId)}",
      _ => s"Organization_account with ${sp(organizationId)} and ${sp(accountId)} deleted"
    )

//  override def update(organizationAccount: OrganizationAccount, updatedAt: Instant): ConnectionIO[OrganizationAccount] =
//    logAroundInfoQuery(
//      updateStatement(OrganizationAccountRepoImpl.ORGANIZATION_ACCOUNT_TABLE_FRAG)(organizationAccount.organizationId)(fr"""
//        name = ${organization.name},
//        description = ${organization.description},
//        created_at = ${organization.createdAt},
//        updated_at = $updatedAt
//      """).update
//        .withUniqueGeneratedKeys[Organization](
//          "id",
//          "name",
//          "description",
//          "created_at",
//          "updated_at"
//        )
//    )(
//      s"Updating ${sp(organization)}",
//      organization => s"${sp(organization)} updated"
//    )

  override def findPaginatedMembers(
    organizationId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[Account]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[Account](
        pageQuery = request =>
          (fr"select account.*" ++ organizationMembers(organizationId) ++
            PaginationSql.orderBy(fr"account.last_name", fr"account.first_name", fr"account.id") ++
            PaginationSql.limitOffset(request)).query[Account].to[List],
        countQuery = (fr"select count(*)" ++ organizationMembers(organizationId)).query[Long].unique
      ).run(page)
    )(
      s"Finding members of organization ${sp(organizationId)} with ${sp(page)}",
      membersFound => s"${sp(membersFound)} members found"
    )

  // Shared by the page and the count query.
  private def organizationMembers(organizationId: UUID): Fragment =
    fr"from" ++ OrganizationAccountRepoImpl.ORGANIZATION_ACCOUNT_TABLE_FRAG ++
      innerJoin(AccountRepoImpl.ACCOUNT_TABLE_FRAG)(fr"organization_account.account_id = account.id") ++
      fr"where organization_account.organization_id = $organizationId"
}

object OrganizationAccountRepoImpl {
  val ORGANIZATION_ACCOUNT_TABLE: String = "organization_account"
  val ORGANIZATION_ACCOUNT_TABLE_FRAG: Fragment = Fragment.const(ORGANIZATION_ACCOUNT_TABLE)
}
