package com.togger.common

import cats.implicits.toTraverseOps
import com.togger.account.{AccountRepoImpl, AccountSessionRepo, AccountTypeRepo, TgDbSpec}
import com.togger.logentry.LogEntryRepoImpl
import com.togger.organization.{OrganizationAccountRepoImpl, OrganizationRepoImpl}
import com.togger.project.{ProjectAccountRepoImpl, ProjectRepoImpl}
import com.togger.task.{LinkRepoImpl, TaskRepoImpl}
import com.togger.taskStatus.TaskStatusRepoImpl
import doobie.ConnectionIO

trait BusinessTgDbSpec extends TgDbSpec {
  override val dropTablesQuery: ConnectionIO[Seq[Int]] =
    dropAll(
      Seq(
        LogEntryRepoImpl.LOG_ENTRY_TABLE,
        LinkRepoImpl.LINK_TABLE,
        TaskRepoImpl.TASK_TABLE,
        TaskStatusRepoImpl.TASK_STATUS_TABLE,
        AccountSessionRepo.ACCOUNT_SESSION_TABLE,
        OrganizationAccountRepoImpl.ORGANIZATION_ACCOUNT_TABLE,
        ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE,
        AccountRepoImpl.ACCOUNT_TABLE,
        AccountTypeRepo.ACCOUNT_TYPE_TABLE,
        ProjectRepoImpl.PROJECT_TABLE,
        OrganizationRepoImpl.ORGANIZATION_TABLE
      )
    ).sequence
}
