package com.togger.routes

import com.raquo.waypoint._
import com.togger.routes.Pages._

import java.util.UUID

class TgRoutes {

  private type ListQuery = (Option[String], Option[String])

  private val listQueryParams = param[String]("page").? & param[String]("size").?

  private def listToQuery(list: ListParams): ListQuery =
    (ListParams.pageToQuery(list), ListParams.sizeToQuery(list))

  private def listFromQuery(params: ListQuery): ListParams =
    ListParams.fromQuery(params._1, params._2)

  private type TaskConfigListQuery = (Option[String], Option[String], Option[String], Option[String])

  private type MilestoneListQuery = (Option[String], Option[String], List[String], Option[String])

  private type ContributionsQuery = (Option[String], Option[String])

  private type MilestoneHistoryQuery =
    (
      Option[String],
      List[String],
      Option[String],
      Option[String],
      List[String],
      List[String],
      List[String],
      Option[String]
    )

  private type TaskListQuery =
    (Option[String], Option[String], List[String], List[String], List[String], List[String], List[String], Option[String])

  def routes(basePath: String): Seq[Route[_ <: FrontendPage, _ <: Any]] =
    Seq(
      Route.static(
        HomePage,
        root / endOfSegments
//        basePath = basePath
      ),
      Route.static(
        LoginPage,
        root / "login"
      ),
      Route.static(
        ChangePasswordPage,
        root / "change-password"
      ),
      Route.static(
        ApexChartDemoPage,
        root / "chart-demo"
      ),
      Route(
        encode = (page: CreateTaskPage) => page.projectId.toString,
        decode = (projectId: String) => CreateTaskPage(UUID.fromString(projectId)),
        pattern = root / "create-task" / segment[String] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: ProjectHomePage) => page.projectId.toString,
        decode = (projectId: String) => ProjectHomePage(UUID.fromString(projectId)),
        pattern = root / "project-home" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: CreateTaskStatusPage) => page.projectId.toString,
        decode = (projectId: String) => CreateTaskStatusPage(UUID.fromString(projectId)),
        pattern = root / "create-task-status" / segment[String] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: EditTaskStatusPage) => page.projectId.toString -> page.taskStatusId.toString,
        decode = { (projectIdAndTaskStatusId: (String, String)) =>
          EditTaskStatusPage(
            UUID.fromString(projectIdAndTaskStatusId._1),
            UUID.fromString(projectIdAndTaskStatusId._2)
          )
        },
        pattern = root / "edit-task-status" / "project-id" / segment[String] / "task-status-id" / segment[
          String
        ] / endOfSegments
        //        basePath = basePath
      ),
      Route(
        encode = (page: EditTaskPage) => page.projectId.toString -> page.taskId.toString,
        decode = { (projectIdAndTaskId: (String, String)) =>
          EditTaskPage(UUID.fromString(projectIdAndTaskId._1), UUID.fromString(projectIdAndTaskId._2))
        },
        pattern = root / "edit-task" / "project-id" / segment[String] / "task-id" / segment[String] / endOfSegments
//        basePath = basePath
      ),
      Route.withQuery[ShowAllTasksPage, String, TaskListQuery](
        encode = (page: ShowAllTasksPage) =>
          PatternArgs(
            path = page.projectId.toString,
            params = (
              TaskListParams.pageToQuery(page.list),
              TaskListParams.sizeToQuery(page.list),
              TaskListParams.selectionToQuery(page.list.statuses),
              TaskListParams.selectionToQuery(page.list.priorities),
              TaskListParams.selectionToQuery(page.list.labels),
              TaskListParams.selectionToQuery(page.list.assignees),
              TaskListParams.selectionToQuery(page.list.milestones),
              TaskListParams.searchToQuery(page.list)
            )
          ),
        decode = { (args: PatternArgs[String, TaskListQuery]) =>
          val (page, size, statuses, priorities, labels, assignees, milestones, search) = args.params
          ShowAllTasksPage(
            UUID.fromString(args.path),
            TaskListParams.fromQuery(page, size, statuses, priorities, labels, assignees, milestones, search)
          )
        },
        pattern = (root / "show-tasks" / segment[String] / endOfSegments) ? (
          param[String]("page").? &
            param[String]("size").? &
            listParam[String]("status") &
            listParam[String]("priority") &
            listParam[String]("label") &
            listParam[String]("assignee") &
            listParam[String]("milestone") &
            param[String]("search").?
        )
      ),
      Route.withQuery[ShowAllTaskStatusesPage, String, ListQuery](
        encode = (page: ShowAllTaskStatusesPage) =>
          PatternArgs(path = page.projectId.toString, params = listToQuery(page.list)),
        decode = (args: PatternArgs[String, ListQuery]) =>
          ShowAllTaskStatusesPage(UUID.fromString(args.path), listFromQuery(args.params)),
        pattern = (root / "task-status" / segment[String] / endOfSegments) ? listQueryParams
      ),
      Route(
        encode = (page: CreateTaskLabelPage) => page.projectId.toString,
        decode = (projectId: String) => CreateTaskLabelPage(UUID.fromString(projectId)),
        pattern = root / "create-task-label" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: EditTaskLabelPage) => page.projectId.toString -> page.taskLabelId.toString,
        decode = { (projectIdAndTaskLabelId: (String, String)) =>
          EditTaskLabelPage(
            UUID.fromString(projectIdAndTaskLabelId._1),
            UUID.fromString(projectIdAndTaskLabelId._2)
          )
        },
        pattern = root / "edit-task-label" / "project-id" / segment[String] / "task-label-id" / segment[
          String
        ] / endOfSegments
      ),
      Route.withQuery[ShowAllTaskLabelsPage, String, ListQuery](
        encode = (page: ShowAllTaskLabelsPage) =>
          PatternArgs(path = page.projectId.toString, params = listToQuery(page.list)),
        decode = (args: PatternArgs[String, ListQuery]) =>
          ShowAllTaskLabelsPage(UUID.fromString(args.path), listFromQuery(args.params)),
        pattern = (root / "task-label" / segment[String] / endOfSegments) ? listQueryParams
      ),
      Route(
        encode = (page: CreateTaskTemplatePage) => page.projectId.toString,
        decode = (projectId: String) => CreateTaskTemplatePage(UUID.fromString(projectId)),
        pattern = root / "create-task-template" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: EditTaskTemplatePage) => page.projectId.toString -> page.taskTemplateId.toString,
        decode = { (projectIdAndTemplateId: (String, String)) =>
          EditTaskTemplatePage(
            UUID.fromString(projectIdAndTemplateId._1),
            UUID.fromString(projectIdAndTemplateId._2)
          )
        },
        pattern = root / "edit-task-template" / "project-id" / segment[String] / "task-template-id" / segment[
          String
        ] / endOfSegments
      ),
      Route.withQuery[ShowAllTaskTemplatesPage, String, ListQuery](
        encode = (page: ShowAllTaskTemplatesPage) =>
          PatternArgs(path = page.projectId.toString, params = listToQuery(page.list)),
        decode = (args: PatternArgs[String, ListQuery]) =>
          ShowAllTaskTemplatesPage(UUID.fromString(args.path), listFromQuery(args.params)),
        pattern = (root / "task-template" / segment[String] / endOfSegments) ? listQueryParams
      ),
      Route(
        encode = (page: CreateProjectPage) => page.organizationId.toString,
        decode = (organizationId: String) => CreateProjectPage(UUID.fromString(organizationId)),
        pattern = root / "create-project" / segment[String] / endOfSegments
        //        basePath = basePath
      ),
      Route.withQuery[ShowProjectsForOrganizationIdTable, String, ListQuery](
        encode = (page: ShowProjectsForOrganizationIdTable) =>
          PatternArgs(path = page.id.toString, params = listToQuery(page.list)),
        decode = (args: PatternArgs[String, ListQuery]) =>
          ShowProjectsForOrganizationIdTable(UUID.fromString(args.path), listFromQuery(args.params)),
        pattern = (root / "project" / "table" / "organization" / segment[String] / endOfSegments) ? listQueryParams
      ),
      Route.static(
        SearchProjectsByLabelPage,
        root / "project" / "search" / endOfSegments
      ),
      Route(
        encode = (page: EditProjectPage) => page.id.toString,
        decode = (id: String) => EditProjectPage(UUID.fromString(id)),
        pattern = root / "project" / segment[String] / endOfSegments
        //        basePath = basePath
      ),
      Route.static(
        CreateOrganizationPage,
        root / "organization" / "create"
        //        basePath = basePath
      ),
      Route.withQuery[ShowAllOrganizationsPage, Unit, ListQuery](
        encode = (page: ShowAllOrganizationsPage) => PatternArgs(path = (), params = listToQuery(page.list)),
        decode = (args: PatternArgs[Unit, ListQuery]) => ShowAllOrganizationsPage(listFromQuery(args.params)),
        pattern = (root / "organization" / endOfSegments) ? listQueryParams
      ),
      Route(
        encode = (page: EditOrganizationPage) => page.id.toString,
        decode = (id: String) => EditOrganizationPage(UUID.fromString(id)),
        pattern = root / "organization" / segment[String] / endOfSegments
        //        basePath = basePath
      ),
      Route.static(
        SyncCheckSettingsPage,
        root / "settings" / "sync-check"
      ),
      Route.static(
        CreateProjectLabelPage,
        root / "settings" / "project-label" / "create"
      ),
      Route.withQuery[ShowAllProjectLabelsPage, Unit, ListQuery](
        encode = (page: ShowAllProjectLabelsPage) => PatternArgs(path = (), params = listToQuery(page.list)),
        decode = (args: PatternArgs[Unit, ListQuery]) => ShowAllProjectLabelsPage(listFromQuery(args.params)),
        pattern = (root / "settings" / "project-label" / endOfSegments) ? listQueryParams
      ),
      Route(
        encode = (page: EditProjectLabelPage) => page.id.toString,
        decode = (id: String) => EditProjectLabelPage(UUID.fromString(id)),
        pattern = root / "settings" / "project-label" / "edit" / segment[String] / endOfSegments
      ),
      Route.static(
        CreateTgPromptPage,
        root / "settings" / "prompt" / "create"
      ),
      Route.withQuery[ShowAllTgPromptsPage, Unit, ListQuery](
        encode = (page: ShowAllTgPromptsPage) => PatternArgs(path = (), params = listToQuery(page.list)),
        decode = (args: PatternArgs[Unit, ListQuery]) => ShowAllTgPromptsPage(listFromQuery(args.params)),
        pattern = (root / "settings" / "prompt" / endOfSegments) ? listQueryParams
      ),
      Route(
        encode = (page: EditTgPromptPage) => page.id.toString,
        decode = (id: String) => EditTgPromptPage(UUID.fromString(id)),
        pattern = root / "settings" / "prompt" / "edit" / segment[String] / endOfSegments
      ),
      Route.static(
        CreateProjectLabelCategoryPage,
        root / "settings" / "project-label-category" / "create"
      ),
      Route.withQuery[ShowAllProjectLabelCategoriesPage, Unit, ListQuery](
        encode = (page: ShowAllProjectLabelCategoriesPage) => PatternArgs(path = (), params = listToQuery(page.list)),
        decode = (args: PatternArgs[Unit, ListQuery]) => ShowAllProjectLabelCategoriesPage(listFromQuery(args.params)),
        pattern = (root / "settings" / "project-label-category" / endOfSegments) ? listQueryParams
      ),
      Route(
        encode = (page: EditProjectLabelCategoryPage) => page.id.toString,
        decode = (id: String) => EditProjectLabelCategoryPage(UUID.fromString(id)),
        pattern = root / "settings" / "project-label-category" / "edit" / segment[String] / endOfSegments
      ),
      Route.withQuery[SyncConfigPage, String, TaskConfigListQuery](
        encode = (page: SyncConfigPage) =>
          PatternArgs(
            path = page.projectId.toString,
            params = (
              ListParams.pageToQuery(page.list.export),
              ListParams.sizeToQuery(page.list.export),
              ListParams.pageToQuery(page.list.importData),
              ListParams.sizeToQuery(page.list.importData)
            )
          ),
        decode = { (args: PatternArgs[String, TaskConfigListQuery]) =>
          val (exportPage, exportSize, importPage, importSize) = args.params
          SyncConfigPage(
            UUID.fromString(args.path),
            TaskConfigListParams.fromQuery(exportPage, exportSize, importPage, importSize)
          )
        },
        pattern = (root / "sync-config" / "project" / segment[String] / endOfSegments) ? (
          param[String]("exportPage").? &
            param[String]("exportSize").? &
            param[String]("importPage").? &
            param[String]("importSize").?
        )
      ),
      Route(
        encode = (page: CreateDataExportConfigPage) => page.projectId.toString,
        decode = (projectId: String) => CreateDataExportConfigPage(UUID.fromString(projectId)),
        pattern = root / "data-export-config" / "create" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: EditDataExportConfigPage) => page.projectId.toString -> page.id.toString,
        decode = { (projectIdAndId: (String, String)) =>
          EditDataExportConfigPage(UUID.fromString(projectIdAndId._1), UUID.fromString(projectIdAndId._2))
        },
        pattern =
          root / "data-export-config" / "edit" / "project-id" / segment[String] / "config-id" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: DataExportStatusPage) => page.projectId.toString,
        decode = (projectId: String) => DataExportStatusPage(UUID.fromString(projectId)),
        pattern = root / "data-export-status" / "project" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: CreateDataImportConfigPage) => page.projectId.toString,
        decode = (projectId: String) => CreateDataImportConfigPage(UUID.fromString(projectId)),
        pattern = root / "data-import-config" / "create" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: EditDataImportConfigPage) => page.projectId.toString -> page.id.toString,
        decode = { (projectIdAndId: (String, String)) =>
          EditDataImportConfigPage(UUID.fromString(projectIdAndId._1), UUID.fromString(projectIdAndId._2))
        },
        pattern =
          root / "data-import-config" / "edit" / "project-id" / segment[String] / "config-id" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: CreateProjectMappingPage) => page.projectId.toString,
        decode = (projectId: String) => CreateProjectMappingPage(UUID.fromString(projectId)),
        pattern = root / "project-mapping" / "create" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: CreateMilestonePage) => page.projectId.toString,
        decode = (projectId: String) => CreateMilestonePage(UUID.fromString(projectId)),
        pattern = root / "create-milestone" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: EditMilestonePage) => page.projectId.toString -> page.id.toString,
        decode = { (projectIdAndMilestoneId: (String, String)) =>
          EditMilestonePage(UUID.fromString(projectIdAndMilestoneId._1), UUID.fromString(projectIdAndMilestoneId._2))
        },
        pattern =
          root / "edit-milestone" / "project-id" / segment[String] / "milestone-id" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: MilestoneSummaryPage) => page.projectId.toString -> page.milestoneId.toString,
        decode = { (ids: (String, String)) => MilestoneSummaryPage(UUID.fromString(ids._1), UUID.fromString(ids._2)) },
        pattern = root / "milestone-summary" / "project-id" / segment[String] / "milestone-id" / segment[String] /
          endOfSegments
      ),
      Route(
        encode = (page: MilestoneReviewPage) => page.projectId.toString -> page.milestoneId.toString,
        decode = { (ids: (String, String)) => MilestoneReviewPage(UUID.fromString(ids._1), UUID.fromString(ids._2)) },
        pattern =
          root / "milestone-review" / "project-id" / segment[String] / "milestone-id" / segment[String] / endOfSegments
      ),
      Route.withQuery[ShowMilestonesPage, String, MilestoneListQuery](
        encode = (page: ShowMilestonesPage) =>
          PatternArgs(
            path = page.projectId.toString,
            params = (
              MilestoneListParams.pageToQuery(page.list),
              MilestoneListParams.sizeToQuery(page.list),
              TaskListParams.selectionToQuery(page.list.statuses),
              MilestoneListParams.searchToQuery(page.list)
            )
          ),
        decode = { (args: PatternArgs[String, MilestoneListQuery]) =>
          val (page, size, statuses, search) = args.params
          ShowMilestonesPage(UUID.fromString(args.path), MilestoneListParams.fromQuery(page, size, statuses, search))
        },
        pattern = (root / "show-milestones" / segment[String] / endOfSegments) ? (
          param[String]("page").? &
            param[String]("size").? &
            listParam[String]("status") &
            param[String]("search").?
        )
      ),
      Route.withQuery[ShowMilestoneHistoryPage, String, MilestoneHistoryQuery](
        encode = (page: ShowMilestoneHistoryPage) =>
          PatternArgs(
            path = page.projectId.toString,
            params = (
              MilestoneHistoryParams.modeToQuery(page.history),
              TaskListParams.selectionToQuery(page.history.months),
              MilestoneHistoryParams.dateToQuery(page.history.start),
              MilestoneHistoryParams.dateToQuery(page.history.stop),
              TaskListParams.selectionToQuery(page.history.taskStatuses),
              TaskListParams.selectionToQuery(page.history.milestones),
              TaskListParams.selectionToQuery(page.history.milestoneStatuses),
              MilestoneHistoryParams.searchToQuery(page.history)
            )
          ),
        decode = { (args: PatternArgs[String, MilestoneHistoryQuery]) =>
          val (mode, months, start, stop, taskStatuses, milestones, milestoneStatuses, search) = args.params
          ShowMilestoneHistoryPage(
            UUID.fromString(args.path),
            MilestoneHistoryParams.fromQuery(mode, months, start, stop, taskStatuses, milestones, milestoneStatuses, search)
          )
        },
        pattern = (root / "milestone-history" / segment[String] / endOfSegments) ? (
          param[String]("mode").? &
            listParam[String]("month") &
            param[String]("from").? &
            param[String]("to").? &
            listParam[String]("status") &
            listParam[String]("milestone") &
            listParam[String]("milestoneStatus") &
            param[String]("search").?
        )
      ),
      Route.withQuery[ShowTimeboxesPage, String, ListQuery](
        encode = (page: ShowTimeboxesPage) =>
          PatternArgs(path = page.projectId.toString, params = listToQuery(page.list)),
        decode = (args: PatternArgs[String, ListQuery]) =>
          ShowTimeboxesPage(UUID.fromString(args.path), listFromQuery(args.params)),
        pattern = (root / "show-timeboxes" / segment[String] / endOfSegments) ? listQueryParams
      ),
      Route(
        encode = (page: CreateTimeboxPage) => page.projectId.toString,
        decode = (projectId: String) => CreateTimeboxPage(UUID.fromString(projectId)),
        pattern = root / "create-timebox" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: EditTimeboxPage) => page.projectId.toString -> page.timeboxId.toString,
        decode = { (ids: (String, String)) => EditTimeboxPage(UUID.fromString(ids._1), UUID.fromString(ids._2)) },
        pattern = root / "edit-timebox" / "project-id" / segment[String] / "timebox-id" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: TimeboxHistoryPage) => page.projectId.toString -> page.timeboxId.toString,
        decode = { (ids: (String, String)) => TimeboxHistoryPage(UUID.fromString(ids._1), UUID.fromString(ids._2)) },
        pattern =
          root / "timebox-history" / "project-id" / segment[String] / "timebox-id" / segment[String] / endOfSegments
      ),
      Route(
        encode = (page: TimeboxReviewPage) => page.projectId.toString -> page.timeboxId.toString,
        decode = { (ids: (String, String)) => TimeboxReviewPage(UUID.fromString(ids._1), UUID.fromString(ids._2)) },
        pattern =
          root / "timebox-review" / "project-id" / segment[String] / "timebox-id" / segment[String] / endOfSegments
      ),
      Route.withQuery[ShowContributionsPage, String, ContributionsQuery](
        encode = (page: ShowContributionsPage) =>
          PatternArgs(
            path = page.projectId.toString,
            params =
              (ContributionsParams.dateToQuery(page.params.from), ContributionsParams.dateToQuery(page.params.to))
          ),
        decode = { (args: PatternArgs[String, ContributionsQuery]) =>
          val (from, to) = args.params
          ShowContributionsPage(UUID.fromString(args.path), ContributionsParams.fromQuery(from, to))
        },
        pattern = (root / "contributions" / segment[String] / endOfSegments) ? (
          param[String]("from").? & param[String]("to").?
        )
      )
    )

}
