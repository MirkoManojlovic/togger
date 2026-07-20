package com.togger.organization

import com.togger.account.AccountRepoImpl.passwordCodec
import com.togger.account.{Account, AccountRepoImpl}
import com.togger.db.Util._
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

  override def findAllMembers(organizationId: UUID): ConnectionIO[Seq[Account]] = {
    logAroundInfoQuery(
      (selectAllStatement(OrganizationAccountRepoImpl.ORGANIZATION_ACCOUNT_TABLE_FRAG) ++
        innerJoin(AccountRepoImpl.ACCOUNT_TABLE_FRAG)(fr"organization_account.account_id = account.id") ++
        fr"where organization_account.organization_id = ${organizationId}")
        .query[(OrganizationAccount, Account)]
        .map(_._2)
        .to[Seq]
    )(
      s"Finding all members of organization ${sp(organizationId)}",
      membersFound => s"${sp(membersFound)} members found"
    )
  }
}

object OrganizationAccountRepoImpl {
  val ORGANIZATION_ACCOUNT_TABLE: String = "organization_account"
  val ORGANIZATION_ACCOUNT_TABLE_FRAG: Fragment = Fragment.const(ORGANIZATION_ACCOUNT_TABLE)
}
