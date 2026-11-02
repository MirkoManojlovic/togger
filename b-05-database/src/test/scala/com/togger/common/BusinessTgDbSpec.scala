package com.togger.common

import cats.implicits.toTraverseOps
import com.togger.account.AccountRepoImpl
import com.togger.account.AccountSessionRepo
import com.togger.account.AccountTypeRepo
import com.togger.account.TgDbSpec
import com.togger.accountmapping.AccountMappingRepoImpl
import com.togger.ai.AiEndpointRepoImpl
import com.togger.dataexportconfig.DataExportConfigRepoImpl
import com.togger.dataimportconfig.DataImportConfigRepoImpl
import com.togger.logentry.LogEntryRepoImpl
import com.togger.logentry.LogEntryVersionRepoImpl
import com.togger.milestone.MilestoneDateRepoImpl
import com.togger.milestone.MilestoneDateVersionRepoImpl
import com.togger.milestone.MilestoneRepoImpl
import com.togger.milestone.MilestoneSummaryRepoImpl
import com.togger.milestone.MilestoneSummaryVersionRepoImpl
import com.togger.milestone.MilestoneVersionRepoImpl
import com.togger.organization.OrganizationAccountRepoImpl
import com.togger.organization.OrganizationRepoImpl
import com.togger.project.ProjectAccountRepoImpl
import com.togger.project.ProjectLabelAssignmentRepoImpl
import com.togger.project.ProjectRepoImpl
import com.togger.projectlabel.ProjectLabelCategoryRepoImpl
import com.togger.projectlabel.ProjectLabelRepoImpl
import com.togger.projectmapping.ProjectMappingRepoImpl
import com.togger.task.LinkRepoImpl
import com.togger.task.LinkVersionRepoImpl
import com.togger.task.TaskLabelAssignmentRepoImpl
import com.togger.task.TaskLabelRepoImpl
import com.togger.task.TaskRepoImpl
import com.togger.task.TaskVersionRepoImpl
import com.togger.taskStatus.TaskStatusRepoImpl
import com.togger.tasktemplate.TaskTemplateRepoImpl
import com.togger.tasktemplate.TaskTemplateVersionRepoImpl
import com.togger.tgaiaction.TgAiActionDefaultRepoImpl
import com.togger.tgaiaction.TgAiActionEndpointRepoImpl
import com.togger.tgaiaction.TgAiActionRepoImpl
import com.togger.tgprompttemplate.TgPromptTemplateRepoImpl
import com.togger.tgprompttemplate.TgPromptTemplateVersionRepoImpl
import com.togger.timebox.TimeboxRepoImpl
import com.togger.timebox.TimeboxVersionRepoImpl
import doobie.ConnectionIO

trait BusinessTgDbSpec extends TgDbSpec {
  override val dropTablesQuery: ConnectionIO[Seq[Int]] =
    dropAll(
      Seq(
        AccountMappingRepoImpl.ACCOUNT_MAPPING_TABLE,
        ProjectMappingRepoImpl.PROJECT_MAPPING_TABLE,
        DataExportConfigRepoImpl.DATA_EXPORT_CONFIG_TABLE,
        DataImportConfigRepoImpl.DATA_IMPORT_CONFIG_TABLE,
        TimeboxVersionRepoImpl.TIMEBOX_VERSION_TASK_TABLE,
        TimeboxVersionRepoImpl.TIMEBOX_VERSION_TABLE,
        TimeboxRepoImpl.TIMEBOX_TABLE,
        LogEntryVersionRepoImpl.LOG_ENTRY_VERSION_TABLE,
        LogEntryRepoImpl.LOG_ENTRY_TABLE,
        LinkVersionRepoImpl.LINK_VERSION_TABLE,
        LinkRepoImpl.LINK_TABLE,
        TaskLabelAssignmentRepoImpl.TASK_LABEL_ASSIGNMENT_TABLE,
        TaskVersionRepoImpl.TASK_VERSION_TABLE,
        TaskRepoImpl.TASK_TABLE,
        MilestoneSummaryVersionRepoImpl.MILESTONE_SUMMARY_VERSION_TABLE,
        MilestoneSummaryRepoImpl.MILESTONE_SUMMARY_TABLE,
        MilestoneDateVersionRepoImpl.MILESTONE_DATE_VERSION_TABLE,
        MilestoneDateRepoImpl.MILESTONE_DATE_TABLE,
        MilestoneVersionRepoImpl.MILESTONE_VERSION_TABLE,
        MilestoneRepoImpl.MILESTONE_TABLE,
        TaskStatusRepoImpl.TASK_STATUS_TABLE,
        TgAiActionDefaultRepoImpl.TG_AI_ACTION_DEFAULT_TABLE,
        TgAiActionEndpointRepoImpl.TG_AI_ACTION_ENDPOINT_TABLE,
        TgAiActionRepoImpl.TG_AI_ACTION_TABLE,
        AiEndpointRepoImpl.AI_ENDPOINTS_TABLE,
        TgPromptTemplateVersionRepoImpl.TG_PROMPT_TEMPLATE_VERSION_TABLE,
        TgPromptTemplateRepoImpl.TG_PROMPT_TEMPLATE_TABLE,
        TaskTemplateVersionRepoImpl.TASK_TEMPLATE_VERSION_TABLE,
        TaskTemplateRepoImpl.TASK_TEMPLATE_TABLE,
        TaskLabelRepoImpl.TASK_LABEL_TABLE,
        ProjectLabelAssignmentRepoImpl.PROJECT_LABEL_ASSIGNMENT_TABLE,
        ProjectLabelRepoImpl.PROJECT_LABEL_TABLE,
        ProjectLabelCategoryRepoImpl.PROJECT_LABEL_CATEGORY_TABLE,
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
