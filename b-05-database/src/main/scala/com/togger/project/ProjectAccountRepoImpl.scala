package com.togger.project

import cats.data.NonEmptyList
import cats.implicits._
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
import doobie.util.fragments

import java.util.UUID

class ProjectAccountRepoImpl extends ProjectAccountRepo {

  override def findById(projectId: UUID, accountId: UUID): ConnectionIO[ProjectAccount] =
    logAroundInfoQuery(
      (selectAllStatement(ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG) ++
        fr"where project_id = $projectId and account_id = $accountId")
        .query[ProjectAccount]
        .unique
    )(
      s"Finding project_account with ${sp(projectId)} and ${sp(accountId)}",
      project => s"Project_Account found ${sp(project)}"
    )

  override def insert(projectAccount: ProjectAccount): ConnectionIO[ProjectAccount] = {
    import projectAccount._
    logAroundInfoQuery(
      insertStatement(ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG)(fr"""
        (project_id, account_id, created_at)
        values ($projectId, $accountId, $createdAt)
      """).update
        .withUniqueGeneratedKeys[ProjectAccount](
          "project_id",
          "account_id",
          "created_at"
        )
    )(
      s"Inserting ${sp(projectAccount)}",
      projectAccount => s"Project_account inserted ${sp(projectAccount)}"
    )
  }

  override def deleteById(projectId: UUID, accountId: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG) ++
        fr"where project_id = $projectId and account_id = $accountId").update.run
    )(
      s"Deleting project_account with ${sp(projectId)} and ${sp(accountId)}",
      _ => s"Project_account with ${sp(projectId)} and ${sp(accountId)} deleted"
    )

//  override def update(projectAccount: ProjectAccount, updatedAt: Instant): ConnectionIO[ProjectAccount] =
//    logAroundInfoQuery(
//      updateStatement(ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG)(projectAccount.projectId)(fr"""
//        name = ${project.name},
//        description = ${project.description},
//        created_at = ${project.createdAt},
//        updated_at = $updatedAt
//      """).update
//        .withUniqueGeneratedKeys[Project](
//          "id",
//          "name",
//          "description",
//          "created_at",
//          "updated_at"
//        )
//    )(
//      s"Updating ${sp(project)}",
//      project => s"${sp(project)} updated"
//    )

  override def findPaginatedMembers(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[Account]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[Account](
        pageQuery = request =>
          (fr"select account.*" ++ projectMembers(projectId) ++
            PaginationSql.orderBy(fr"account.last_name", fr"account.first_name", fr"account.id") ++
            PaginationSql.limitOffset(request)).query[Account].to[List],
        countQuery = (fr"select count(*)" ++ projectMembers(projectId)).query[Long].unique
      ).run(page)
    )(
      s"Finding members of project ${sp(projectId)} with ${sp(page)}",
      membersFound => s"${sp(membersFound)} members found"
    )

  // Shared by the page and the count query.
  private def projectMembers(projectId: UUID): Fragment =
    fr"from" ++ ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG ++
      innerJoin(AccountRepoImpl.ACCOUNT_TABLE_FRAG)(fr"project_account.account_id = account.id") ++
      fr"where project_account.project_id = $projectId"

  override def isMember(projectId: UUID, accountId: UUID): ConnectionIO[Boolean] =
    logAroundInfoQuery(
      (fr"select exists (select 1 from" ++ ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG ++
        fr"where project_id = $projectId and account_id = $accountId)").query[Boolean].unique
    )(
      s"Checking if ${sp(accountId)} is a member of ${sp(projectId)}",
      isMember => s"Membership of ${sp(accountId)} in ${sp(projectId)} is ${sp(isMember)}"
    )

  override def findAllMembersByProjectIds(projectIds: Seq[UUID]): ConnectionIO[Map[UUID, Seq[Account]]] =
    NonEmptyList.fromList(projectIds.toList) match {
      case None => Map.empty[UUID, Seq[Account]].pure[ConnectionIO]
      case Some(nonEmptyProjectIds) =>
        logAroundInfoQuery(
          (selectAllStatement(ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG) ++
            innerJoin(AccountRepoImpl.ACCOUNT_TABLE_FRAG)(fr"project_account.account_id = account.id") ++
            fr"where" ++ fragments.in(fr"project_account.project_id", nonEmptyProjectIds))
            .query[(ProjectAccount, Account)]
            .to[List]
            .map(_.groupMap(_._1.projectId)(_._2): Map[UUID, Seq[Account]])
        )(
          s"Finding all members for ${sp(projectIds)}",
          membersByProjectId => s"${sp(membersByProjectId)} found"
        )
    }
}

object ProjectAccountRepoImpl {
  val PROJECT_ACCOUNT_TABLE: String = "project_account"
  val PROJECT_ACCOUNT_TABLE_FRAG: Fragment = Fragment.const(PROJECT_ACCOUNT_TABLE)
}
