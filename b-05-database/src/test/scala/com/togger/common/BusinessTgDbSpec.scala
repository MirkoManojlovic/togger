package com.togger.common

import cats.implicits.toTraverseOps
import com.togger.account.{AccountRepoImpl, AccountSessionRepo, AccountTypeRepo, TgDbSpec}
import com.togger.accountmapping.AccountMappingRepoImpl
import com.togger.logentry.{LogEntryRepoImpl, LogEntryVersionRepoImpl}
import com.togger.milestone.{MilestoneDateRepoImpl, MilestoneDateVersionRepoImpl, MilestoneRepoImpl, MilestoneVersionRepoImpl}
import com.togger.organization.{OrganizationAccountRepoImpl, OrganizationRepoImpl}
import com.togger.project.{ProjectAccountRepoImpl, ProjectRepoImpl}
import com.togger.projectmapping.ProjectMappingRepoImpl
import com.togger.task.{
  LinkRepoImpl,
  LinkVersionRepoImpl,
  TaskLabelAssignmentRepoImpl,
  TaskLabelRepoImpl,
  TaskRepoImpl,
  TaskVersionRepoImpl
}
import com.togger.taskStatus.TaskStatusRepoImpl
import com.togger.taskdumpconfig.TaskDumpConfigRepoImpl
import com.togger.taskloadconfig.TaskLoadConfigRepoImpl
import doobie.ConnectionIO

trait BusinessTgDbSpec extends TgDbSpec {
  override val dropTablesQuery: ConnectionIO[Seq[Int]] =
    dropAll(
      Seq(
        AccountMappingRepoImpl.ACCOUNT_MAPPING_TABLE,
        ProjectMappingRepoImpl.PROJECT_MAPPING_TABLE,
        TaskDumpConfigRepoImpl.TASK_DUMP_CONFIG_TABLE,
        TaskLoadConfigRepoImpl.TASK_LOAD_CONFIG_TABLE,
        LogEntryVersionRepoImpl.LOG_ENTRY_VERSION_TABLE,
        LogEntryRepoImpl.LOG_ENTRY_TABLE,
        MilestoneDateVersionRepoImpl.MILESTONE_DATE_VERSION_TABLE,
        MilestoneDateRepoImpl.MILESTONE_DATE_TABLE,
        MilestoneVersionRepoImpl.MILESTONE_VERSION_TABLE,
        MilestoneRepoImpl.MILESTONE_TABLE,
        LinkVersionRepoImpl.LINK_VERSION_TABLE,
        LinkRepoImpl.LINK_TABLE,
        TaskLabelAssignmentRepoImpl.TASK_LABEL_ASSIGNMENT_TABLE,
        TaskVersionRepoImpl.TASK_VERSION_TABLE,
        TaskRepoImpl.TASK_TABLE,
        TaskStatusRepoImpl.TASK_STATUS_TABLE,
        TaskLabelRepoImpl.TASK_LABEL_TABLE,
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
