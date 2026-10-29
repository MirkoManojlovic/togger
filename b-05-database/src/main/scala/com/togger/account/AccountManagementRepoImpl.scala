package com.togger.account

import cats.data.NonEmptyList
import cats.implicits._
import com.togger.account.AccountRepoImpl.ACCOUNT_TABLE_FRAG
import com.togger.account.AccountSessionRepo.ACCOUNT_SESSION_TABLE_FRAG
import com.togger.account.AccountTypeRepo.ACCOUNT_TYPE_TABLE_FRAG
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.organization.OrganizationAccountRepoImpl
import com.togger.project.ProjectAccountRepoImpl
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment
import doobie.util.fragments

import java.util.UUID

class AccountManagementRepoImpl extends AccountManagementRepo {

  private case class AccountRow(
    id: UUID,
    firstName: String,
    lastName: String,
    email: String,
    accountTypeId: UUID,
    accountTypeName: String
  )

  private val selectRows: Fragment =
    fr"select account.id, account.first_name, account.last_name, account.email, account.account_type_id," ++
      fr"account_type.name from" ++ ACCOUNT_TABLE_FRAG ++
      innerJoin(ACCOUNT_TYPE_TABLE_FRAG)(fr"account.account_type_id = account_type.id")

  override def findPaginated(page: DbPageRequest): ConnectionIO[PaginatedResponse[AccountWithProjects]] =
    logAroundInfoQuery(
      for {
        rows <- TgFindPaginatedQuery[AccountRow](
          pageQuery = request =>
            (selectRows ++ PaginationSql.orderBy(fr"account.last_name", fr"account.first_name", fr"account.id") ++
              PaginationSql.limitOffset(request)).query[AccountRow].to[List],
          countQuery = (fr"select count(*) from" ++ ACCOUNT_TABLE_FRAG).query[Long].unique
        ).run(page)
        accounts <- withProjects(rows.items)
      } yield rows.copy(items = accounts)
    )(s"Finding accounts with ${sp(page)}", accounts => s"${sp(accounts)} accounts found")

  override def findOne(id: UUID): ConnectionIO[Option[AccountWithProjects]] =
    (selectRows ++ fr"where account.id = $id").query[AccountRow].option.flatMap {
      case None      => Option.empty[AccountWithProjects].pure[ConnectionIO]
      case Some(row) => withProjects(List(row)).map(_.headOption)
    }

  override def findAccountTypes: ConnectionIO[List[AccountTypeRef]] =
    (fr"select id, name from" ++ ACCOUNT_TYPE_TABLE_FRAG ++ fr"order by name").query[AccountTypeRef].to[List]

  override def deleteWithMemberships(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      for {
        _ <- (deleteStatement(ACCOUNT_SESSION_TABLE_FRAG) ++ fr"where account_id = $id").update.run
        _ <- (deleteStatement(ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG) ++ fr"where account_id = $id").update.run
        _ <- (deleteStatement(OrganizationAccountRepoImpl.ORGANIZATION_ACCOUNT_TABLE_FRAG) ++
          fr"where account_id = $id").update.run
        deleted <- (deleteStatement(ACCOUNT_TABLE_FRAG) ++ fr"where id = $id").update.run
      } yield deleted
    )(s"Deleting account ${sp(id)} with its memberships", deleted => s"Deleted $deleted accounts")

  // One query for the whole page, so the projects of the accounts are not fetched one account at a time.
  private def withProjects(rows: List[AccountRow]): ConnectionIO[List[AccountWithProjects]] =
    NonEmptyList.fromList(rows.map(_.id)).fold(List.empty[AccountWithProjects].pure[ConnectionIO]) { ids =>
      (fr"select project_account.account_id, project.id, project.organization_id, project.name from" ++
        ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG ++
        innerJoin(fr"project")(fr"project_account.project_id = project.id") ++
        fr"where" ++ fragments.in(fr"project_account.account_id", ids) ++ fr"order by project.name, project.id")
        .query[(UUID, UUID, UUID, String)]
        .to[List]
        .map { memberships =>
          val projectsByAccount =
            memberships.groupMap(_._1) { case (_, projectId, organizationId, name) =>
              AccountProjectRef(projectId, organizationId, name)
            }
          rows.map(row =>
            AccountWithProjects(
              row.id,
              row.firstName,
              row.lastName,
              row.email,
              row.accountTypeId,
              row.accountTypeName,
              projectsByAccount.getOrElse(row.id, Nil)
            )
          )
        }
    }
}
