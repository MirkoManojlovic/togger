package com.togger.project

import com.togger.account.AccountRepoImpl.passwordCodec
import com.togger.account.{Account, AccountRepoImpl}
import com.togger.db.Util._
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

class ProjectAccountRepoImpl extends ProjectAccountRepo {

  override def findById(projectId: Long, accountId: Long): ConnectionIO[ProjectAccount] =
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

  override def deleteById(projectId: Long, accountId: Long): ConnectionIO[Int] =
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

  override def findAllMembers(projectId: Long): ConnectionIO[Seq[Account]] = {
    logAroundInfoQuery(
      (selectAllStatement(ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG) ++
        innerJoin(AccountRepoImpl.ACCOUNT_TABLE_FRAG)(fr"project_account.account_id = account.id") ++
        fr"where project_account.project_id = $projectId")
        .query[(ProjectAccount, Account)]
        .map(_._2)
        .to[Seq]
    )(
      s"Finding all members of project ${sp(projectId)}",
      membersFound => s"${sp(membersFound)} members found"
    )
  }
}

object ProjectAccountRepoImpl {
  val PROJECT_ACCOUNT_TABLE: String = "project_account"
  val PROJECT_ACCOUNT_TABLE_FRAG: Fragment = Fragment.const(PROJECT_ACCOUNT_TABLE)
}
